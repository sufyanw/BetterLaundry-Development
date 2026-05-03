package betterlaundry.model;

import java.time.Instant;

// Point-in-time snapshot of washer state
public final class WasherStatus {

    private final MachineState machineState;
    private final String washerJobState;
    private final Instant completionTime;
    /** Display label from samsungce.washerCycle (e.g. Normal). */
    private final String cycleLabel;
    private final String waterTemperature;
    private final String rinseCycles;
    private final String soilLevel;
    private final Integer progressPercent;
    private final Integer remainingTimeMinutes;
    private final Instant capturedAt;

    public WasherStatus(
            MachineState machineState,
            String washerJobState,
            Instant completionTime,
            String cycleLabel,
            String waterTemperature,
            String rinseCycles,
            String soilLevel,
            Integer progressPercent,
            Integer remainingTimeMinutes,
            Instant capturedAt
    ) {
        this.machineState = machineState != null ? machineState : MachineState.UNKNOWN;
        this.washerJobState = washerJobState != null ? washerJobState : "Unknown";
        this.completionTime = completionTime;
        this.cycleLabel = cycleLabel != null ? cycleLabel : "unknown";
        this.waterTemperature = waterTemperature != null ? waterTemperature : "unknown";
        this.rinseCycles = rinseCycles != null ? rinseCycles : "unknown";
        this.soilLevel = soilLevel != null ? soilLevel : "unknown";
        this.progressPercent = progressPercent;
        this.remainingTimeMinutes = remainingTimeMinutes;
        this.capturedAt = capturedAt != null ? capturedAt : Instant.now();
    }

    public MachineState getMachineState() { return machineState; }
    public String getWasherJobState() { return washerJobState; }
    public Instant getCompletionTime() { return completionTime; }
    public String getCycleLabel() { return cycleLabel; }
    public String getWaterTemperature() { return waterTemperature; }
    public String getRinseCycles() { return rinseCycles; }
    public String getSoilLevel() { return soilLevel; }
    public Integer getProgressPercent() { return progressPercent; }
    public Integer getRemainingTimeMinutes() { return remainingTimeMinutes; }
    public Instant getCapturedAt() { return capturedAt; }

    public String formattedJobState() {
        if (washerJobState == null || washerJobState.isBlank()) return "—";
        return Character.toUpperCase(washerJobState.charAt(0)) + washerJobState.substring(1).toLowerCase();
    }

    public boolean isActive() {
        return machineState == MachineState.RUNNING || machineState == MachineState.PAUSED;
    }

    @Override
    public String toString() {
        return "WasherStatus{" +
               "machineState=" + machineState +
               ", jobState='" + washerJobState + '\'' +
               ", capturedAt=" + capturedAt +
               '}';
    }
}
