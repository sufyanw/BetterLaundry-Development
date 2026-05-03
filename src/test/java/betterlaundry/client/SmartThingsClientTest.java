package betterlaundry.client;

import betterlaundry.config.AppConfig;
import betterlaundry.exception.SmartThingsAPIException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
// typical arrange act assert testing methodology that we learned in class
final class SmartThingsClientTest {

    @Test
    void validateDevices_ThrowsWhenConfiguredDeviceIsUnreachable() {
        // arrange: create client with device ID that we know mock will fail on
        SmartThingsClient client = new SmartThingsClient(
                AppConfig.load(Map.of(
                        "SMARTTHINGS_TOKEN", "fake-token",
                        "GEMINI_API_KEY", "fake-key",
                        "WASHER_DEVICE_ID", "LG over samsung any time any day",
                        "DRYER_DEVICE_ID", "missing-device"
                ))
        ) {
            // act: override the getDeviceStatus method to throw an exception when the device ID is "missing-device"
            @Override
            public JsonNode getDeviceStatus(String deviceId) throws SmartThingsAPIException {
                if ("missing-device".equals(deviceId)) {
                    throw new SmartThingsAPIException(new RuntimeException("HTTP 404"));
                }
                return new ObjectMapper().createObjectNode();
            }
        };

        // assert: validateDevices should throw a RuntimeException because the device ID is "missing-device"
        assertThrows(RuntimeException.class, client::validateDevices);
    }
}
