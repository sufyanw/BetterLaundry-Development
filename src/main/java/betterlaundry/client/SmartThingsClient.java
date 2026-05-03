package betterlaundry.client;

import betterlaundry.config.AppConfig;
import betterlaundry.exception.SmartThingsAPIException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// Small HTTP wrapper for SmartThings API calls
// Returns raw JSON; parsing is handled in parser/DeviceStatusParser.java
public class SmartThingsClient {

    private static final Logger log = LoggerFactory.getLogger(SmartThingsClient.class);

    private static final String BASE_URL = "https://api.smartthings.com/v1";
    private static final String HISTORY_BASE_URL = "https://api.smartthings.com/history";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final AppConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public SmartThingsClient(AppConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.mapper = new ObjectMapper();
    }

    // get full status payload for one device
    public JsonNode getDeviceStatus(String deviceId) throws SmartThingsAPIException {
        String url = BASE_URL + "/devices/" + deviceId + "/status";
        return get(url);
    }

    public String getDeviceLocationId(String deviceId) throws SmartThingsAPIException {
        JsonNode details = get(BASE_URL + "/devices/" + encode(deviceId));
        String locId = details.path("locationId").asText("");
        if (locId.isBlank()) {
            throw new SmartThingsAPIException(new RuntimeException("No locationId for device: " + deviceId));
        }
        return locId;
    }
    
    // We have to get history for the device because SmartThingsAPI only provides live status; history is required to identify state transitions 
    // (e.g., when a machine started or finished), calculate cycle durations, and provide 
    // a dataset for Gemini AI analysis/database records
    // without this, once the load of laundry is complete, the API won't provide any specific details about the cycle which is needed for analysis
    public JsonNode getDeviceHistory(String locationId, String deviceId, Instant since) throws SmartThingsAPIException {
        String url = String.format("%s/devices?locationId=%s&deviceId=%s&since=%s",
                HISTORY_BASE_URL, encode(locationId), encode(deviceId), encode(since.toString()));
        return get(url);
    }

    // Validation logic using IDs from config
    public void validateDevices() {
        var washer = CompletableFuture.runAsync(() -> check(config.washerDeviceId()));
        var dryer = CompletableFuture.runAsync(() -> check(config.dryerDeviceId()));
        CompletableFuture.allOf(washer, dryer).join();
        log.info("Startup validation successful.");
    }

    private void check(String id) {
        try {
            getDeviceStatus(id);
            log.info("Device {} is reachable.", id);
        } catch (SmartThingsAPIException e) {
            throw new RuntimeException("Validation failed for: " + id, e);
        }
    }

    private JsonNode get(String url) throws SmartThingsAPIException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.smartThingsToken())
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET().build();

        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new SmartThingsAPIException(new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body()));
            }
            return mapper.readTree(resp.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new SmartThingsAPIException(new RuntimeException("Request failed: " + url, e));
        }
    }

    private String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}
