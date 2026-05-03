package betterlaundry.polling;

import betterlaundry.client.SmartThingsClient;
import betterlaundry.config.DeviceConstants;
import betterlaundry.db.DatabaseManager;
import betterlaundry.exception.SmartThingsAPIException;
import betterlaundry.model.*;
import betterlaundry.parser.DeviceStatusParser;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

// Polls SmartThings on an interval and tracks cycle transitions
// having an interval is helpful because it allows us to get data from the API at a cadence.
// By being able to get data this way, there's still an advantage to using BetterLaundry 
// over using the SmartThings app on your phone directly because you still get real-time updates
public class DevicePollingService {

    private static final Logger log = LoggerFactory.getLogger(DevicePollingService.class);

    // max pending SSE messages; oldest is dropped when queue is full
    // 35 is just a guess. without a limit, the queue would grow indefinitely
    private static final int SSE_QUEUE_CAPACITY = 35;

    private final SmartThingsClient client;
    private final DeviceStatusParser parser;
    private final List<LaundryDevice> devices;
    private final DatabaseManager db;
    private final int intervalSeconds;

    // latest status snapshot per device id
    private final Map<String, WasherStatus> washerStatuses = new ConcurrentHashMap<>();
    private final Map<String, DryerStatus>  dryerStatuses  = new ConcurrentHashMap<>();
    // latest active snapshots per device id, used for persistence at cycle end
    private final Map<String, WasherStatus> lastActiveWasherStatuses = new ConcurrentHashMap<>();
    private final Map<String, DryerStatus>  lastActiveDryerStatuses  = new ConcurrentHashMap<>();

    // per-device error flag (true means last poll failed)
    private final Map<String, Boolean> deviceErrors = new ConcurrentHashMap<>();

    // cycle tracking state while machine is active
    private final Map<String, Instant> cycleStartTimes = new ConcurrentHashMap<>();

    // previous state per device so we can detect transitions
    private final Map<String, MachineState> previousStates = new ConcurrentHashMap<>();

    // queue read by dashboard SSE endpoint
    private final Queue<DeviceEvent> sseEvents = new ArrayBlockingQueue<>(SSE_QUEUE_CAPACITY);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "device-poller");
                t.setDaemon(true); // Daemon threads are threads that do not prevent the JVM from exiting
                return t;
            });

    public DevicePollingService(SmartThingsClient client, DeviceStatusParser parser, List<LaundryDevice> devices, DatabaseManager db, int intervalSeconds) {
        this.client = client;
        this.parser = parser;
        this.devices = List.copyOf(devices);
        this.db = db;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
                this::pollAll, 0, intervalSeconds, TimeUnit.SECONDS);
        log.info("Polling every {}s for {} device(s).", intervalSeconds, devices.size());
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void pollAll() {
        for (LaundryDevice device : devices) {
            try {
                pollDevice(device);
                deviceErrors.put(device.getDeviceId(), false);
            } catch (SmartThingsAPIException e) {
                log.warn("Poll failed for {}: {}",
                        device.getDisplayName(), e.getMessage());
                deviceErrors.put(device.getDeviceId(), true);
            } catch (Exception e) {
                log.error("Unexpected error polling {}", device.getDisplayName(), e);
                deviceErrors.put(device.getDeviceId(), true);
            }
        }
    }

    private void pollDevice(LaundryDevice device) throws SmartThingsAPIException {
        String id = device.getDeviceId();
        JsonNode raw = client.getDeviceStatus(id);

        if (device instanceof WasherDevice) {
            WasherStatus status = parser.parseWasher(raw);
            washerStatuses.put(id, status);
            if (isMachineActive(status.getMachineState())) {
                lastActiveWasherStatuses.put(id, status);
            }
            handleTransition(device, status.getMachineState(), "washer");

        } else if (device instanceof DryerDevice) {
            DryerStatus status = parser.parseDryer(raw);
            dryerStatuses.put(id, status);
            if (isMachineActive(status.getMachineState())) {
                lastActiveDryerStatuses.put(id, status);
            }
            handleTransition(device, status.getMachineState(), "dryer");
        }
    }

    private boolean isMachineActive(MachineState state) {
        return state == MachineState.RUNNING || state == MachineState.PAUSED;
        }

    private void handleTransition(
            LaundryDevice device,
            MachineState newState,
            String deviceType
    ) {
        String id  = device.getDeviceId();
        MachineState prev = previousStates.getOrDefault(id, MachineState.UNKNOWN);
        if (prev == MachineState.UNKNOWN) {
            // App likely started mid-cycle; avoid creating a partial cycle window.
            previousStates.put(id, newState);
            return;
        }
        // Compare previous and current activity states to detect the "edges" 
        // of a laundry cycle. e.g., Start: Inactive -> Active | End: Active -> Inactive
        boolean wasActive = isMachineActive(prev);
        boolean isActive  = isMachineActive(newState);

        // cycle start transition
        if (!wasActive && isActive) {
            cycleStartTimes.put(id, Instant.now());
            log.info("{} cycle started.", device.getDisplayName());
        }

        // cycle end transition (active -> inactive)
        // this can happen if someone in the house starts up a cycle of laundry but then doesn't let it finish
        // this typically doesn't happen, but it's a fallback in case the power goes out in the house in the middle of a laundry load.
        // the laundry room's location is sandwiched between the kitchen and the garage of my house. We somtimes use a lot of power causing the breaker(s) to trip
        // so this could impact the washer and dryer. We haven't had this happen in the past few months though

        if (wasActive && !isActive) {
            Instant start = cycleStartTimes.remove(id);
            Instant end = Instant.now();
            WasherStatus washerSnapshot = null;
            DryerStatus dryerSnapshot = null;
            if ("washer".equals(deviceType)) {
                washerSnapshot = lastActiveWasherStatuses.remove(id);
                if (washerSnapshot == null) {
                    washerSnapshot = washerStatuses.get(id);
                }
            } else if ("dryer".equals(deviceType)) {
                dryerSnapshot = lastActiveDryerStatuses.remove(id);
                if (dryerSnapshot == null) {
                    dryerSnapshot = dryerStatuses.get(id);
                }
            }

            if (start != null) {
                CycleRecord record = buildCycleRecord(deviceType, id, start, end, washerSnapshot, dryerSnapshot);
                db.save(record);
                log.info("{} cycle completed: {} min",
                        device.getDisplayName(), record.getDurationMinutes());

                DeviceEvent event = new DeviceEvent(
                        deviceType,
                        id,
                        device.getDisplayName() + " cycle finished!",
                        Instant.now()
                );
                if (!sseEvents.offer(event)) {
                    sseEvents.poll();
                    sseEvents.offer(event);
                }
            } else {
                log.warn("Cycle ended for {}, but start data was missing (app likely started mid-cycle).",
                        device.getDisplayName());
            }
        }

        previousStates.put(id, newState);
    }

    private CycleRecord buildCycleRecord(
            String deviceType,
            String deviceId,
            Instant start,
            Instant end,
            WasherStatus washerSnapshot,
            DryerStatus dryerSnapshot
    ) {
    if ("washer".equals(deviceType)) {
        WasherStatus status = (washerSnapshot != null) ? washerSnapshot : washerStatuses.get(deviceId);
        if (status == null) {
            return new CycleRecord(deviceType, deviceId, start, end,
                    "-", "-", "-", "-", "-");
        }
        return new CycleRecord(
                deviceType,
                deviceId,
                start,
                end,
                status.getCycleLabel(),
                status.getWaterTemperature(),
                status.getRinseCycles(),
                status.getSoilLevel(),
                "-" // wrinklePrevent (N/A for washer)
        );
    }

    if ("dryer".equals(deviceType)) {
        DryerStatus status = (dryerSnapshot != null) ? dryerSnapshot : dryerStatuses.get(deviceId);
        if (status == null) {
            return new CycleRecord(deviceType, deviceId, start, end,
                    "-", "-", "-", "-", "-");
        }
        return new CycleRecord(
                deviceType,
                deviceId,
                start,
                end,
                status.getCycleLabel(),
                status.getDryingTemperature(),
                "-", // rinses (N/A for dryer)
                "-", // soilLevel (N/A for dryer)
                status.getWrinklePrevent()
        );
    }

    return new CycleRecord(deviceType, deviceId, start, end,
            "-", "-", "-", "-", "-");
}

    // if the value is null, blank, "unknown", or "none", return "-". This only happens for certain fields that are only unique to the washer or dryer
    private String normalize(String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value)) {
            return "-";
        }
        return value;
    }

    // if the status is null, return a default status with the unknown state and cycle label
    // from the perspectrive of the convinence of the user, throwing an error will make the dashboard unavailable
    // just writing unknown and - for cycle details is more user friendly, and if the machine does send a status again, 
    // the dashboard will merely be updated in the next poll
    public WasherStatus getWasherStatus(String deviceId) {
        WasherStatus status = washerStatuses.get(deviceId);
        return (status != null) ? status : new WasherStatus(
            MachineState.UNKNOWN, "unknown", null, "unknown",
            "-", "-", "-", null, null, Instant.now()
            );
        }

    public DryerStatus getDryerStatus(String deviceId) {
        DryerStatus status = dryerStatuses.get(deviceId);
        return (status != null) ? status : new DryerStatus(
            MachineState.UNKNOWN, "unknown", null, "unknown",
            "-", "-", null, null, Instant.now()
            );
        }

    public boolean hasDeviceError(String deviceId) {
        return deviceErrors.getOrDefault(deviceId, false);
    }

    // non-blocking queue drain for SSE
    public List<DeviceEvent> drainSseEvents() {
        List<DeviceEvent> batch = new ArrayList<>();
        DeviceEvent event;
        while ((event = sseEvents.poll()) != null) {
            batch.add(event);
        }
        return batch;
    }

    public List<LaundryDevice> getDevices() {
        return devices;
    }
}
