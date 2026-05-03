package betterlaundry.config;

import betterlaundry.config.AppConfig;
import betterlaundry.exception.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class AppConfigTest {
    // arrange: helper to create a valid environment map with default values
    private static Map<String, String> validEnvironmentVariables(Map<String, String> overrides) {
        Map<String, String> env = new HashMap<>(Map.of(

                "SMARTTHINGS_TOKEN", "fake-token",
                "GEMINI_API_KEY", "fake-key",
                "WASHER_DEVICE_ID", "w-123",
                "DRYER_DEVICE_ID", "d-123"));
        env.putAll(overrides);
        return env;
    }

    @Test
    void missingSmartThingsToken_ThrowsConfigurationException() {
        // arrange: prepare an environment map with a blank token
        Map<String, String> env = validEnvironmentVariables(Map.of("SMARTTHINGS_TOKEN", "   "));

        // act: attempt to initialize configuration
        ConfigurationException ex = assertThrows(ConfigurationException.class,
                () -> AppConfig.load(env));

        // assert: verify exception message identifies missing SmartThings token
        assertTrue(ex.getMessage().contains("SMARTTHINGS_TOKEN"));
    }

    @Test
    void missingGeminiApiKey_ThrowsConfigurationException() {
        // arrange: prepare an environment map with a blank API key
        Map<String, String> env = validEnvironmentVariables(Map.of("GEMINI_API_KEY", "   "));

        // act: attempt to initialize configuration
        ConfigurationException ex = assertThrows(ConfigurationException.class,
                () -> AppConfig.load(env));

        // assert: verify exception message identifies that GEMINI_API_KEY is missing
        assertTrue(ex.getMessage().contains("GEMINI_API_KEY"));
    }

    @Test
    void validEnvironment_PortAndPollingAreFixed() {
        // arrange: initialize a minimal valid configuration
        // act: no explicit action needed beyond construction
        AppConfig config = AppConfig.load(validEnvironmentVariables(Map.of()));

        // assert: verify port and polling interval are correctly set since they're fixed
        assertEquals(3000, config.port());
        assertEquals(10, config.pollingIntervalSeconds());
    }

    @Test
    void washerCycle_BlankRawReturnsUnknown() {
        // arrange: prepare null and blank inputs
        // act & assert: directly verify that invalid inputs return "unknown"
        assertEquals("unknown", AppConfig.washerCycle(null));
        assertEquals("unknown", AppConfig.washerCycle("  "));
    }

    @Test
    void washerCycle_KnownMappingReturnsLabel() {
        // arrange: identify a known raw washer cycle string
        String rawCycle = "Table_02_Course_01";

        // act: map the raw string to a label
        String label = AppConfig.washerCycle(rawCycle);

        // assert: verify the mapping matches the expected "Normal" label
        assertEquals("Normal", label);
    }

    @Test
    void dryerCycle_KnownMappingReturnsLabel() {
        // arrange: identify a known raw dryer cycle string
        String rawCycle = "Table_03_Course_44";

        // act: map the raw string to a label
        String label = AppConfig.dryerCycle(rawCycle);

        // assert: verify the mapping matches the expected "Time Dry" label
        assertEquals("Time Dry", label);
    }
}
