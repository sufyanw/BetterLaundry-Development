package betterlaundry.parser;

import betterlaundry.config.DeviceConstants;
import betterlaundry.model.DryerStatus;
import betterlaundry.model.MachineState;
import betterlaundry.model.WasherStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

final class DeviceStatusParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DeviceStatusParser parser = new DeviceStatusParser();

    @Test
    void parseWasher_ValidPayLoadMapsExpectedFields() throws Exception {
        // arrange: minimal-but-complete washer JSON shaped like SmartThings "main" component output
        // only doing this for the washer because it would be the same thing for the dryer, just with different fields. the logic would be the same for both, just with different fields
        // because some fields are required for the washer, but not for the dryer, and vice versa
        JsonNode root = mapper.readTree("""
                {
                  "components": { "main": {
                    "washerOperatingState": {
                      "machineState": {"value":"run"},
                      "washerJobState": {"value":"washing"},
                      "completionTime": {"value":"2026-05-02T02:00:00Z"}
                    },
                    "samsungce.washerOperatingState": {
                      "progress": {"value": 45},
                      "remainingTime": {"value": 24}
                    },
                    "samsungce.washerCycle": {"washerCycle": {"value":"Table_02_Course_01"}},
                    "custom.washerWaterTemperature": {"washerWaterTemperature":{"value":"warm"}},
                    "custom.washerRinseCycles": {"washerRinseCycles":{"value":"2"}},
                    "custom.washerSoilLevel": {"washerSoilLevel":{"value":"normal"}}
                  }}
                }
                """);

        // act: parse the same payload production polling receives
        WasherStatus status = parser.parseWasher(root);

        // assert: machine state and mapped cycle label must agree with AppConfig; progress fields prove SmartThingsAPI
        // capability wiring without actually hitting the real API
        assertEquals(MachineState.RUNNING, status.getMachineState());
        assertEquals("Normal", status.getCycleLabel());
        assertEquals("warm", status.getWaterTemperature());
        assertEquals(Integer.valueOf(45), status.getProgressPercent());
        assertEquals(Integer.valueOf(24), status.getRemainingTimeMinutes());
    }

    @Test
    void parseDryer_MissingFieldsUsesSafeDefaults() throws Exception {
        // arrange: empty dryerOperatingState object, worst case scenario for missing fields
        JsonNode root = mapper.readTree("""
                { "components": { "main": { "dryerOperatingState": {} } } }
                """);

        // act: parse dryer payload with missing fields
        DryerStatus status = parser.parseDryer(root);

        // assert: ensure we get unknown state and "unknown" labels prevent NullPointerExceptions and blank frontend crashes
        assertEquals(MachineState.UNKNOWN, status.getMachineState());
        assertEquals("unknown", status.getCycleLabel());
        assertEquals("Unknown", status.getDryingTemperature());
        assertNull(status.getCompletionTime());
    }

     @Test
    void parseWasher_MissingFieldsUsesSafeDefaults() throws Exception {
        // arrange: empty washerOperatingState object, worst case scenario for missing fields
        JsonNode root = mapper.readTree("""
                { "components": { "main": { "washerOperatingState": {} } } }
                """);

        // act: parse washer payload with missing fields
        WasherStatus status = parser.parseWasher(root);

        // assert: ensure we get unknown state and "unknown" labels prevent NullPointerExceptions and blank frontend crashes
        assertEquals(MachineState.UNKNOWN, status.getMachineState());
        assertEquals("unknown", status.getCycleLabel());
        assertEquals("unknown", status.getSoilLevel());
        assertEquals("unknown", status.getRinseCycles());
        assertEquals("unknown", status.getWaterTemperature());
        assertNull(status.getCompletionTime());
    }
}
