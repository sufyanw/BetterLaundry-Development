package betterlaundry.model;

// Concrete base type for laundry devices used by app -> helpful so that validations for device ID and display name can be done in one place

// making it an abstract class since every device must be a washer or dryer
public abstract class LaundryDevice {

    private final String deviceId;
    private final String displayName;

    public LaundryDevice(String deviceId, String displayName) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.deviceId = deviceId;
        this.displayName = displayName;
    }

    public String getDeviceId() { return deviceId; }
    public String getDisplayName() { return displayName; }
    @Override
    public String toString() { return displayName + "[" + deviceId + "]"; }
}