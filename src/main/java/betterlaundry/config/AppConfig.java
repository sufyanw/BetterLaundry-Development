package betterlaundry.config;

import betterlaundry.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Locale;
import java.util.Map;

public final class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    // map samsungce.dryerCycle and samsungce.washerCycle raw values to actual display names
    // the SmartThings API doesn't actually return the display names. This is merely a workaround I came up with
    // based on what I noticed by looking at the actual machine labes and curling into the status of the machines. 
    // Check README.md for more details on what the JSON payloads look like for each device

    private static final Map<String, String> WASHER_LABELS = Map.of(
        "TABLE_02_COURSE_01", "Normal"
        );
    private static final Map<String, String> DRYER_LABELS = Map.of(
        "TABLE_03_COURSE_06", "Time Dry",
        "TABLE_03_COURSE_44", "Time Dry",
        "TABLE_03_COURSE_01", "Normal"
        );

    private final String smartThingsToken;
    private final String geminiApiKey;
    private final String washerDeviceId;
    private final String dryerDeviceId;
    private final int port;
    private final int pollingIntervalSeconds;

    // Private constructor: Forces use of AppConfig.load()
    private AppConfig(Map<String, String> env) {
        this.smartThingsToken = requireEnv(env, "SMARTTHINGS_TOKEN");
        this.geminiApiKey = requireEnv(env, "GEMINI_API_KEY");
        this.washerDeviceId = requireEnv(env, "WASHER_DEVICE_ID");
        this.dryerDeviceId = requireEnv(env, "DRYER_DEVICE_ID");
        this.port = 3000;
        this.pollingIntervalSeconds = 10;
    }

    public static AppConfig load() {
        return new AppConfig(System.getenv());
    }

    public static AppConfig load(Map<String, String> env) { // overloaded method, only used for testing
        return new AppConfig(env);
        }

    // Accessors
    public String smartThingsToken() {return smartThingsToken;}
    public String geminiApiKey() {return geminiApiKey;}
    public String washerDeviceId() {return washerDeviceId;}
    public String dryerDeviceId() {return dryerDeviceId;}
    public int port() {return port;}
    public int pollingIntervalSeconds() {return pollingIntervalSeconds;}

    // Logic Helpers
    public static String washerCycle(String raw) {return lookup(WASHER_LABELS, raw);}
    public static String dryerCycle(String raw) {return lookup(DRYER_LABELS, raw);}

    private static String lookup(Map<String, String> map, String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        return map.getOrDefault(raw.trim().toUpperCase(Locale.ROOT), raw.trim());
    }

    private static String requireEnv(Map<String, String> env, String name) {
        String val = env.get(name);
        if (val == null || val.isBlank()) throw new ConfigurationException(new IllegalArgumentException("Missing: " + name));
        return val.trim();
    }
}