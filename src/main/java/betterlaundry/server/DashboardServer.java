package betterlaundry.server;

import betterlaundry.ai.GeminiAnalysisService;
import betterlaundry.config.DeviceConstants;
import betterlaundry.db.DatabaseManager;
import betterlaundry.model.*;
import betterlaundry.polling.DevicePollingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

// Javalin server for dashboard page, API routes, & SSE updates
public class DashboardServer {

    private static final Logger log = LoggerFactory.getLogger(DashboardServer.class);

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final int MAX_SSE_CLIENTS = 20;

    private final DevicePollingService polling;
    private final DatabaseManager db;
    private final GeminiAnalysisService ai;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();

    // active SSE clients
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();

    public DashboardServer(
            DevicePollingService polling,
            DatabaseManager db,
            GeminiAnalysisService ai,
            int port
    ) {
        this.polling = polling;
        this.db = db;
        this.ai = ai;
        this.port = port;
    }

    public void start() {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/static", io.javalin.http.staticfiles.Location.CLASSPATH);
            config.http.defaultContentType = "application/json";
        });

        // thees are't actual pages, they are just API endpoints that are
        // used to get the data for the dashboard and update the front-end
        app.get("/", this::handleDashboard);
        app.get("/api/status", this::handleStatus);
        app.get("/api/history", this::handleHistory);
        app.get("/api/ai", this::handleAi);
        app.sse("/api/events", this::handleSse);

        app.start(port);

        // start background loop to broadcast polling events over SSE
        startSseBroadcaster();
    }

    // returns a live washer + dryer snapshot
    private void handleStatus(Context ctx) {
        ObjectNode root = mapper.createObjectNode();
        root.put("serverTime", ISO_FMT.format(Instant.now()));

        // washer section
        WasherStatus washer = polling.getWasherStatus(DeviceConstants.WASHER_DEVICE_ID);
        ObjectNode washerNode = root.putObject("washer");
        washerNode.put("error", polling.hasDeviceError(DeviceConstants.WASHER_DEVICE_ID));
        
        washerNode.put("machineState", washer.getMachineState().displayLabel());
        washerNode.put("machineStateCss", washer.getMachineState().cssClass());
        washerNode.put("jobState", washer.formattedJobState());
        washerNode.put("cycleLabel", capFirst(washer.getCycleLabel()));
        washerNode.put("waterTemperature", capFirst(washer.getWaterTemperature()));
        washerNode.put("rinseCycles", capFirst(washer.getRinseCycles()));
        washerNode.put("soilLevel", capFirst(washer.getSoilLevel()));

        if (washer.getProgressPercent() != null) {
            washerNode.put("progressPercent", washer.getProgressPercent());
        } else {
            washerNode.putNull("progressPercent");
        }
    
        if (washer.getRemainingTimeMinutes() != null) {
            washerNode.put("remainingTimeMinutes", washer.getRemainingTimeMinutes());
        } else {
            washerNode.putNull("remainingTimeMinutes");
        }

        boolean washerActive = washer.getMachineState() == MachineState.RUNNING
                || washer.getMachineState() == MachineState.PAUSED;
        washerNode.put("completionTime",
                washerActive && washer.getCompletionTime() != null ? ISO_FMT.format(washer.getCompletionTime()) : null);
        washerNode.put("capturedAt", ISO_FMT.format(washer.getCapturedAt()));


        // dryer section
        DryerStatus dryer = polling.getDryerStatus(DeviceConstants.DRYER_DEVICE_ID);
        ObjectNode dryerNode = root.putObject("dryer");
        dryerNode.put("error", polling.hasDeviceError(DeviceConstants.DRYER_DEVICE_ID));

        dryerNode.put("machineState", dryer.getMachineState().displayLabel());
        dryerNode.put("machineStateCss", dryer.getMachineState().cssClass());
        dryerNode.put("jobState", dryer.formattedJobState());
        dryerNode.put("cycleLabel", capFirst(dryer.getCycleLabel()));
        dryerNode.put("dryerDryingTemperature", capFirst(dryer.getDryingTemperature()));
        dryerNode.put("wrinklePrevent", capFirst(dryer.getWrinklePrevent()));

        if (dryer.getProgressPercent() != null) {
            dryerNode.put("progressPercent", dryer.getProgressPercent());
        } else {
            dryerNode.putNull("progressPercent");
        }

        if (dryer.getRemainingTimeMinutes() != null) {
            dryerNode.put("remainingTimeMinutes", dryer.getRemainingTimeMinutes());
        } else {
            dryerNode.putNull("remainingTimeMinutes");
        }

        boolean dryerActive = dryer.getMachineState() == MachineState.RUNNING
                || dryer.getMachineState() == MachineState.PAUSED;
        dryerNode.put("completionTime",
                dryerActive && dryer.getCompletionTime() != null ? ISO_FMT.format(dryer.getCompletionTime()) : null);
        dryerNode.put("capturedAt",      ISO_FMT.format(dryer.getCapturedAt()));

        ctx.json(root.toString());
    }

    // returns cycle history JSON (?limit=N, default 20, max 100)
    private void handleHistory(Context ctx) {
        int limit = Math.min(100, Math.max(1,
                ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20)));

        List<CycleRecord> records = db.load(limit);
        ArrayNode arr = mapper.createArrayNode();
        for (CycleRecord r : records) {
            ObjectNode node = arr.addObject();
            node.put("deviceType", r.getDeviceType());
            node.put("device", displayDevice(r.getDeviceType()));
            node.put("startTime", ISO_FMT.format(r.getStartTime()));
            node.put("endTime", ISO_FMT.format(r.getEndTime()));
            node.put("durationMin", r.getDurationMinutes());
            node.put("cycle", capFirst(r.getCycleLabel()));
            node.put("temp", capFirst(r.getTemperatureLevel()));
            node.put("rinses", capFirst(r.getRinses()));
            node.put("soil", capFirst(r.getSoilLevel()));
            node.put("wrinklePrevent", capFirst(r.getWrinklePrevent()));
        }
        ctx.json(arr.toString());
    }

    // returns AI summary, which can take short amount of time to generate
    private void handleAi(Context ctx) {
        List<CycleRecord> recent = db.load(50);
        String summary = ai.generateSummary(recent);
        ObjectNode node = mapper.createObjectNode();
        node.put("summary", summary);
        ctx.json(node.toString());
    }

    // SSE endpoint for push notifications
    private void handleSse(SseClient client) {
        if (sseClients.size() >= MAX_SSE_CLIENTS) {
            // Prevent unbounded growth if a client gets stuck reconnecting.
            sseClients.remove(0);
            log.warn("SSE client cap reached ({}). Dropping oldest tracked client.", MAX_SSE_CLIENTS);
        }
        sseClients.add(client);
        client.onClose(() -> {
            sseClients.remove(client);
            log.info("SSE client disconnected. Total: {}", sseClients.size());
        });
        log.info("SSE client connected. Total: {}", sseClients.size());
    }

    private void startSseBroadcaster() {
        Thread broadcaster = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<DeviceEvent> events = polling.drainSseEvents();
                    for (DeviceEvent event : events) {
                        for (SseClient client : sseClients) {
                            try {
                                client.sendEvent("cycle-complete", event.message());
                            } catch (Exception e) {
                                log.debug("Failed to send SSE to client: {}", e.getMessage());
                                sseClients.remove(client);
                            }
                        }
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "sse-broadcaster");
        broadcaster.setDaemon(true);
        broadcaster.start();
    }

    private void handleDashboard(Context ctx) {
        ctx.redirect("/index.html");
    }

    /** Labels device_type from DB for the dashboard ("washer" → "Washer"). */
    private static String displayDevice(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return "-";
        }
        String d = deviceType.trim().toLowerCase(Locale.ROOT);
        if ("washer".equals(d)) {
            return "Washer";
        }
        if ("dryer".equals(d)) {
            return "Dryer";
        }
        return capFirst(deviceType);
    }

    /** Uppercase first character only; blank or "-" yields "-". */
    private static String capFirst(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        String s = raw.trim();
        if ("-".equals(s)) {
            return "-";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
