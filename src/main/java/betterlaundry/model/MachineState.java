package betterlaundry.model;

import java.util.EnumMap;
import java.util.Map;

// although these are constants, I chose not to include it in config/ DeviceConstants.java just because
// it makes exposing the labels and CSS classes to the front-end easier
public enum MachineState {
    RUNNING, PAUSED, STOP, FINISHED, UNKNOWN;

    private static final EnumMap<MachineState, String> DISPLAY_LABELS = new EnumMap<>(Map.of(
        RUNNING, "Running",
        PAUSED, "Paused",
        STOP, "Idle",
        FINISHED, "Finished",
        UNKNOWN, "Unknown"
    ));

    private static final EnumMap<MachineState, String> CSS_CLASSES = new EnumMap<>(Map.of(
        RUNNING, "state-running",
        PAUSED, "state-paused",
        STOP, "state-idle",
        FINISHED, "state-finished",
        UNKNOWN, "state-unknown"
    ));

    public String displayLabel() { return DISPLAY_LABELS.get(this); }
    public String cssClass() { return CSS_CLASSES.get(this); }

    // I have this because when we get the machine state from the API, 
    // we can get values like "run" and "running," so it's best to just normalize them all to the same value here

    /* example of the API response (this can also be found in the README.md):

    "dryerOperatingState": {
          "machineState": {
            "value": "run",
            "timestamp": "2026-05-02T01:27:49.022Z"
          },
        },
        "samsungce.dryerOperatingState": {
          "operatingState": {
            "value": "running",
            "timestamp": "2026-05-02T01:27:49.022Z"
          },

    */

    public static MachineState fromApiValue(String raw) {
        if (raw == null) return UNKNOWN;
        return switch (raw.toLowerCase()) {
            case "run", "running" -> RUNNING;
            case "pause", "paused" -> PAUSED;
            case "stop", "idle", "ready", "standby" -> STOP;
            case "end", "finished", "complete", "completed", "done" -> FINISHED;
            default -> UNKNOWN;
        };
    }
}