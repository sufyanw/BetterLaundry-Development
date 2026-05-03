package betterlaundry.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// typical arrange act assert testing methodology that we learned in class
final class LaundryDeviceTest {

    @Test
    void washerInheritsIdentityFromBase() {
        // arrange: device id that's valid 
        WasherDevice washer = new WasherDevice("abc-123");

        // act: read the surface API the dashboard and logs rely on
        String id = washer.getDeviceId();
        String displayName = washer.getDisplayName();
        String text = washer.toString();

        // assert: subclass should fix human-readable names while keeping the SmartThings id verbatim
        // if we don't do this, polling and database rows will disagree with what is configured for the environment variables
        assertEquals("abc-123", id);
        assertEquals("Washer", displayName);
        assertTrue(text.contains("Washer"));
        assertTrue(text.contains("abc-123"));
    }

    @Test
    void dryerInheritsIdentityFromBase() {
        // arrange: mirror the washer case but this time for a "dryer"
        DryerDevice dryer = new DryerDevice("def-456");

        // act: read the surface API the dashboard and logs rely on
        String id = dryer.getDeviceId();
        String displayName = dryer.getDisplayName();
        String text = dryer.toString();

        // assert: subclass should fix human-readable names while keeping the SmartThings id verbatim
        // if we don't do this, polling and database rows will disagree with what is configured for the environment variables
        assertEquals("def-456", id);
        assertEquals("Dryer", displayName);
        assertTrue(text.contains("Dryer"));
        assertTrue(text.contains("def-456"));
    }

    @Test
    void blankIdThrowsIllegalArgumentException() {
        // arrange: nothing to construct—invalid ids should fail fast before we register devices with SmartThings
        // act & assert: reject blank/null ids because empty ids cannot be polled safely
        assertThrows(IllegalArgumentException.class, () -> new WasherDevice("  "));
        assertThrows(IllegalArgumentException.class, () -> new DryerDevice(null));
    }
}
