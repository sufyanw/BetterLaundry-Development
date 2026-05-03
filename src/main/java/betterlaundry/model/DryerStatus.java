package betterlaundry.model;

import java.time.Instant;

// Point-in-time snapshot of dryer state
public final class DryerStatus {

    private final MachineState machineState;
    private final String dryerJobState;
    private final Instant completionTime;
    /** Human-readable cycle name from samsungce.dryerCycle (e.g. Time Dry for Table_03_Course_44). */
    private final String cycleLabel;
    private final String dryingTemperature;
    private final String wrinklePrevent;
    /** samsungce.dryerOperatingState progress value (0–100), null when idle / unavailable */
    private final Integer progressPercent;
    /** samsungce.dryerOperatingState remaining time in minutes, null when idle / unavailable */
    private final Integer remainingTimeMinutes;
    private final Instant capturedAt;

    public DryerStatus(
            MachineState machineState,
            String dryerJobState,
            Instant completionTime,
            String cycleLabel,
            String dryingTemperature,
            String wrinklePrevent,
            Integer progressPercent,
            Integer remainingTimeMinutes,
            Instant capturedAt
    ) {
        this.machineState = machineState != null ? machineState : MachineState.UNKNOWN;
        this.dryerJobState = dryerJobState != null ? dryerJobState : "unknown";
        this.completionTime = completionTime;
        this.cycleLabel = cycleLabel != null ? cycleLabel : "unknown";
        this.dryingTemperature = dryingTemperature != null ? dryingTemperature : "unknown";
        this.wrinklePrevent = wrinklePrevent != null ? wrinklePrevent : "unknown";
        this.progressPercent = progressPercent;
        this.remainingTimeMinutes = remainingTimeMinutes;
        this.capturedAt = capturedAt != null ? capturedAt : Instant.now();
    }

    public MachineState getMachineState() { return machineState; }
    public String getDryerJobState() { return dryerJobState; }
    public Instant getCompletionTime() { return completionTime; }
    public String getCycleLabel() { return cycleLabel; }
    public String getDryingTemperature() {
        String result = Character.toUpperCase(dryingTemperature.charAt(0)) + dryingTemperature.substring(1);
        return result.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
    public String getWrinklePrevent() { return wrinklePrevent; }
    public Integer getProgressPercent() { return progressPercent; }
    public Integer getRemainingTimeMinutes() { return remainingTimeMinutes; }
    public Instant getCapturedAt() { return capturedAt; }

    public String formattedJobState() {
        if (dryerJobState == null || dryerJobState.isBlank()) return "";
        return Character.toUpperCase(dryerJobState.charAt(0)) + dryerJobState.substring(1).toLowerCase();
    }

    public boolean isActive() {
        return machineState == MachineState.RUNNING || machineState == MachineState.PAUSED;
    }

    @Override
    public String toString() {
        return "DryerStatus{" +
               "machineState=" + machineState +
               ", jobState='" + dryerJobState + '\'' +
               ", capturedAt=" + capturedAt +
               '}';
    }
}
