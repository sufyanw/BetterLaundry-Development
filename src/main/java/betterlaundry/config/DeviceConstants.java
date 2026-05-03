package betterlaundry.config;

// Shared SmartThings keys and fixed device ids
public final class DeviceConstants {

    private DeviceConstants() {}

    // safe default for getting the device ids from the environment variables
    // if it's 1, then we know the device id is not set in the environment variables
    public static final String WASHER_DEVICE_ID = System.getenv().getOrDefault(
            "WASHER_DEVICE_ID", "1");
    public static final String DRYER_DEVICE_ID  = System.getenv().getOrDefault(
            "DRYER_DEVICE_ID", "1");

    // SmartThings capability keys
    public static final String CAP_WASHER_OPERATING_STATE = "washerOperatingState";
    public static final String CAP_DRYER_OPERATING_STATE = "dryerOperatingState";
    public static final String CAP_WASHER_WATER_TEMPERATURE = "custom.washerWaterTemperature";
    public static final String CAP_WASHER_RINSE_CYCLES = "custom.washerRinseCycles";
    public static final String CAP_WASHER_SOIL_LEVEL = "custom.washerSoilLevel";
    public static final String CAP_DRYER_TEMPERATURE = "samsungce.dryerDryingTemperature";
    public static final String CAP_DRYER_CYCLE = "samsungce.dryerCycle";
    public static final String CAP_DRYER_WRINKLE_PREVENT = "custom.dryerWrinklePrevent";
    public static final String CAP_WASHER_CYCLE = "samsungce.washerCycle";
    public static final String CAP_SAMSUNG_WASHER_OPERATING_STATE = "samsungce.washerOperatingState";
    public static final String CAP_SAMSUNG_DRYER_OPERATING_STATE = "samsungce.dryerOperatingState";

    // SmartThings attribute names
    public static final String ATTR_MACHINE_STATE = "machineState";
    public static final String ATTR_WASHER_JOB_STATE = "washerJobState";
    public static final String ATTR_DRYER_JOB_STATE = "dryerJobState";
    public static final String ATTR_COMPLETION_TIME = "completionTimeReport";
    public static final String ATTR_WASHER_WATER_TEMPERATURE = "washerWaterTemperature";
    public static final String ATTR_WASHER_RINSE_CYCLES = "washerRinseCycles";
    public static final String ATTR_WASHER_SOIL_LEVEL = "washerSoilLevel";
    public static final String ATTR_DRYER_DRYING_TEMPERATURE = "dryerDryingTemperature";
    public static final String ATTR_DRYER_CYCLE = "dryerCycle";
    public static final String ATTR_DRYER_PROGRESS = "progress";
    public static final String ATTR_DRYER_REMAINING_TIME = "remainingTime";
    public static final String ATTR_DRYER_WRINKLE_PREVENT = "dryerWrinklePrevent";
    public static final String ATTR_WASHER_CYCLE = "washerCycle";
    public static final String ATTR_WASHER_PROGRESS = "progress";
    public static final String ATTR_WASHER_REMAINING_TIME = "remainingTime";
}
