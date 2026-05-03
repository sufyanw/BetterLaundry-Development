package betterlaundry.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DeviceConstantsTest {
    // triple A here is just ensuring every single capability and attribute is present/isn't blank
    @Test
    void requiredCapability_Attributes_Present() {
        assertFalse(DeviceConstants.CAP_WASHER_OPERATING_STATE.isBlank());
        assertFalse(DeviceConstants.CAP_DRYER_OPERATING_STATE.isBlank());
        assertFalse(DeviceConstants.CAP_WASHER_CYCLE.isBlank());
        assertFalse(DeviceConstants.CAP_DRYER_CYCLE.isBlank());
        assertFalse(DeviceConstants.CAP_SAMSUNG_WASHER_OPERATING_STATE.isBlank());
        assertFalse(DeviceConstants.CAP_SAMSUNG_DRYER_OPERATING_STATE.isBlank());
        
        // attributes
        assertFalse(DeviceConstants.ATTR_MACHINE_STATE.isBlank());
        assertFalse(DeviceConstants.ATTR_WASHER_JOB_STATE.isBlank());
        assertFalse(DeviceConstants.ATTR_DRYER_JOB_STATE.isBlank());
        assertFalse(DeviceConstants.ATTR_COMPLETION_TIME.isBlank());
        assertFalse(DeviceConstants.ATTR_DRYER_PROGRESS.isBlank());
        assertFalse(DeviceConstants.ATTR_WASHER_PROGRESS.isBlank());
    }
}
