package betterlaundry.model;

import java.time.Instant;

// event payload for SSE notifications
public record DeviceEvent( String deviceType, String deviceId, String message, Instant createdAt) {
        
}
