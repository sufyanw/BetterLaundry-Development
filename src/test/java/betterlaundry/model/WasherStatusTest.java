package betterlaundry.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

final class WasherStatusTest {

    @Test
    void constructor_MapsFieldsAndFormatting() {
        // arrange: timestamps ordered to be realistic for a live poll
        Instant completionTime = Instant.parse("2026-05-02T02:00:00Z");
        Instant startTime = Instant.parse("2026-05-02T01:50:00Z");

        // arrange: PAUSED + "WASHING" keeps the machine in an "active" state for isActive(), and
        // the constructor reflects the persisted snapshot shape used when building CycleRecords
        WasherStatus status = new WasherStatus(MachineState.PAUSED, "WASHING", completionTime, "Normal", "warm", "2", "Normal",
        80, 10, startTime);

        // act & assert: getters should correctly return values passed into constructor
        assertEquals("Normal", status.getCycleLabel());
        assertEquals("warm", status.getWaterTemperature());
        assertEquals(Integer.valueOf(80), status.getProgressPercent());
        assertEquals(Integer.valueOf(10), status.getRemainingTimeMinutes());
        assertEquals("Washing", status.formattedJobState());
        assertTrue(status.isActive());
    }
}
