package betterlaundry.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

final class DryerStatusTest {

    @Test
    void constructor_MapsFieldsAndFormatting() {
        // arrange: completion/start instants are ordered like a real poll window so later tests that depend
        // on duration or ordering stay realistic if we extend this class.
        Instant completionTime = Instant.parse("2026-05-02T02:00:00Z");
        Instant startTime = Instant.parse("2026-05-02T01:50:00Z");

        // arrange: use RUNNING plus camelCase "mediumLow" on purpose—SmartThings sends baked-in
        // camelCase temperature tokens, and getDryingTemperature() is the place we normalize that for UI text,
        // so the regression value here is "Medium Low", not the raw token.
        DryerStatus status = new DryerStatus(
                MachineState.RUNNING,
                "DRYING",
                completionTime,
                "Time Dry",
                "mediumLow",
                "off",
                77,
                10,
                startTime
        );

        // act & assert: snapshot getters should round-trip what we constructed; remaining assertions split
        // display helpers vs. activity flag because those code paths change independently in production.
        assertEquals("Time Dry", status.getCycleLabel());
        assertEquals("Medium Low", status.getDryingTemperature());
        assertEquals(Integer.valueOf(77), status.getProgressPercent());
        assertEquals(Integer.valueOf(10), status.getRemainingTimeMinutes());
        assertEquals("Drying", status.formattedJobState());
        assertTrue(status.isActive());
    }
}
