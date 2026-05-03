package betterlaundry.model;

import java.time.Duration;
import java.time.Instant;

// Immutable data for one completed laundry cycle
public final class CycleRecord {

    private final String deviceType;
    private final String deviceId;
    private final Instant startTime;
    private final Instant endTime;
    private final String cycleLabel;
    private final String temperatureLevel;
    private final String rinses; // unique to washer
    private final String soilLevel; // unique to washer
    private final String wrinklePrevent; // unique to dryer

    public CycleRecord(
            String deviceType,
            String deviceId,
            Instant startTime,
            Instant endTime,
            String cycleLabel,
            String temperatureLevel,
            String rinses,
            String soilLevel,
            String wrinklePrevent
    ) {
        if (startTime == null) throw new IllegalArgumentException("startTime must not be null");
        if (endTime == null) throw new IllegalArgumentException("endTime must not be null");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime can't be before startTime");
        }

        this.deviceType = normalize(deviceType);
        this.deviceId = normalize(deviceId);
        this.startTime = startTime;
        this.endTime = endTime;
        this.cycleLabel = normalize(cycleLabel);
        this.temperatureLevel = normalize(temperatureLevel);
        this.rinses = normalize(rinses);
        this.soilLevel = normalize(soilLevel);
        this.wrinklePrevent = normalize(wrinklePrevent);
    }

    public String getDeviceType() { return deviceType; }
    public String getDeviceId() { return deviceId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getCycleLabel() { return cycleLabel; }
    public String getTemperatureLevel() { return temperatureLevel; }
    public String getRinses() { return rinses; }
    public String getSoilLevel() { return soilLevel; }
    public String getWrinklePrevent() { return wrinklePrevent; }
    public long getDurationMinutes() { return Duration.between(startTime, endTime).toMinutes(); }

    // if the value is null or blank, return "-"
    // this is done because there are some fields that are only unique to the washer or dryer, 
    // so we need to handle them differently
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    @Override
    public String toString() {
        return "CycleRecord{" +
               "deviceType='" + deviceType + '\'' +
               ", deviceId='" + deviceId + '\'' +
               ", cycleLabel='" + cycleLabel + '\'' +
               ", start=" + startTime +
               ", end=" + endTime +
               ", durationMin=" + getDurationMinutes() +
               '}';
    }
}
