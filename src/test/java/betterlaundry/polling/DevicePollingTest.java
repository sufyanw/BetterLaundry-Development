package betterlaundry.polling;

import betterlaundry.client.SmartThingsClient;
import betterlaundry.config.AppConfig;
import betterlaundry.db.DatabaseManager;
import betterlaundry.exception.SmartThingsAPIException;
import betterlaundry.model.*;
import betterlaundry.parser.DeviceStatusParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class DevicePollingTest {

    private static AppConfig pollingAppConfig() {
        return AppConfig.load(Map.of(
                "SMARTTHINGS_TOKEN", "fake-token",
                "GEMINI_API_KEY", "fake-key",
                "WASHER_DEVICE_ID", "w-123",
                "DRYER_DEVICE_ID", "d-123"));
    }

    @Test
    void runningToFinishedTransitionSavesCycleRecord() throws Exception {
        CapturingDatabaseManager db = new CapturingDatabaseManager();
        DevicePollingService service = new DevicePollingService(
                new NoopClient(),
                new DeviceStatusParser(),
                List.of(new WasherDevice("washer-1")),
                db,
                10
        );
        putInMap(service, "washerStatuses", "washer-1", new WasherStatus(
                MachineState.RUNNING, "washing", Instant.parse("2026-05-02T02:00:00Z"),
                "Normal", "warm", "2", "normal",
                80, 10, Instant.parse("2026-05-02T01:50:00Z")));
        putInMap(service, "previousStates", "washer-1", MachineState.RUNNING);
        putInMap(service, "cycleStartTimes", "washer-1", Instant.parse("2026-05-02T01:10:00Z"));

        invokePrivate(service, "handleTransition",
                new Class[]{LaundryDevice.class, MachineState.class, String.class},
                new WasherDevice("washer-1"), MachineState.FINISHED, "washer");

        assertEquals(1, db.saved.size());
        CycleRecord record = db.saved.get(0);
        assertEquals("washer-1", record.getDeviceId());
        assertEquals("Normal", record.getCycleLabel());
        assertEquals("warm", record.getTemperatureLevel());
    }

    @Test
    void nonTwoHundredResponse_setsDeviceErrorFlag() throws Exception {
        DevicePollingService service = new DevicePollingService(
                new FailingClient(),
                new DeviceStatusParser(),
                List.of(new WasherDevice("washer-1")),
                new CapturingDatabaseManager(),
                10
        );

        invokePrivate(service, "poll", new Class[]{});

        assertTrue(service.hasDeviceError("washer-1"));
    }

    @SuppressWarnings("unchecked")
    private static void putInMap(DevicePollingService service, String fieldName, String key, Object value) throws Exception {
        Field f = DevicePollingService.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        ((Map<String, Object>) f.get(service)).put(key, value);
    }

    private static void invokePrivate(DevicePollingService service, String methodName, Class<?>[] argTypes, Object... args) throws Exception {
        Method m = DevicePollingService.class.getDeclaredMethod(methodName, argTypes);
        m.setAccessible(true);
        m.invoke(service, args);
    }

    private static final class CapturingDatabaseManager extends DatabaseManager {
        private final List<CycleRecord> saved = new ArrayList<>();

        @Override
        public void save(CycleRecord record) {
            saved.add(record);
        }
    }

    private static class NoopClient extends SmartThingsClient {
        NoopClient() {
            super(pollingAppConfig());
        }

        @Override
        public JsonNode getDeviceStatus(String deviceId) {
            return new ObjectMapper().createObjectNode();
        }
    }

    private static final class FailingClient extends SmartThingsClient {
        FailingClient() {
            super(pollingAppConfig());
        }

        @Override
        public JsonNode getDeviceStatus(String deviceId) throws SmartThingsAPIException {
            throw new SmartThingsAPIException(new RuntimeException("HTTP 429 Too Many Requests"));
        }
    }
}
