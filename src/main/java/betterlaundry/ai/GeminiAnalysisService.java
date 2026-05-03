package betterlaundry.ai;

import betterlaundry.config.AppConfig;
import betterlaundry.interfaces.AIAnalyzable;
import betterlaundry.model.CycleRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

// Calls Gemini to create a laundry usage summary
public class GeminiAnalysisService implements AIAnalyzable {

    private static final Logger log = LoggerFactory.getLogger(GeminiAnalysisService.class);

    // 2.5 flash limits to 5 calls per minute on free tier level
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    // headers for the sections of the response -> we need to ensure that Gemini's generated response includes these headers exactly
    private static final String HEADER_ENERGY = "Energy Summary";
    private static final String HEADER_USAGE = "Usage Observations";
    private static final String HEADER_RECOMENDATIONS = "Recommendations";
    
    private final AppConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper; // used to parse the JSON response from Gemini API

    public GeminiAnalysisService(AppConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String generateSummary(List<CycleRecord> records) {
        // check if there's recent cycle history first because calling buildPrompt(records) is an expensive operation
        // this is an edge case as well
        if (records == null || records.isEmpty()) {
            return "No cycle history available. Do some laundry first!";
        }

        try {
            // we now know there's recent cycle history, so we can build the prompt, call Gemini, & return its response
            String prompt = buildPrompt(records);
            String response = callGemini(prompt);
            if (!validateHeaders(response)) {
                log.warn("Gemini response missing required headers.");
                return "Gemini response is missing the required headers. Please try again later.";
            }
            return response.isBlank() ? "AI Insights could not be generated - empty response." : response;
        } catch (Exception e) {
            log.warn("Gemini API call failed: {}", e.getMessage()); // logging to help in debugging process
            return "AI Insights are unavailable. Please try again later.";
        }
    }

    private boolean validateHeaders(String response) {
        return response.contains(HEADER_ENERGY) && 
               response.contains(HEADER_USAGE) && 
               response.contains(HEADER_RECOMENDATIONS);
    }

    private String formatRecordForPrompt(CycleRecord record) {
        if ("washer".equalsIgnoreCase(record.getDeviceType())) {
            return String.format(
                "- Washer: %s | Temp: %s | Rinses: %s | Soil: %s | Duration: %d mins",
                record.getCycleLabel(), record.getTemperatureLevel(), record.getRinses(),
                record.getSoilLevel(), record.getDurationMinutes()
            );
        } else {
            return String.format(
                "- Dryer: %s | Temp: %s | Wrinkle Prevent: %s | Duration: %d mins",
                record.getCycleLabel(), record.getTemperatureLevel(),
                record.getWrinklePrevent(), record.getDurationMinutes()
            );
        }
    }

    private String buildPrompt(List<CycleRecord> records) {
        // Count the number of washer and dryer cycles
        long washerCycles = records.stream().filter(r -> "washer".equals(r.getDeviceType())).count();
        long dryerCycles = records.stream().filter(r -> "dryer".equals(r.getDeviceType())).count();
        // Calculate the average cycle duration
        double avgDuration = records.stream()
                .mapToLong(CycleRecord::getDurationMinutes)
                .average()
                .orElse(0);
        // Build the cycle details string
        String cycleDetails = records.stream()
                .limit(10)
                .map(this::formatRecordForPrompt)
                .collect(Collectors.joining("\n"));

        // Build prompt for the Gemini API call
        return """
                You are helping members of a household optimize their laundry settings.
                Analyze cycle settings and duration only. Do not use watt or watt-hour values.
                The household owns a Samsung WF50BG8300AE washer and Samsung DVG50BG8300E gas dryer.

                Aggregates from recent cycle history (%d records):
                - Washer cycles: %d | Dryer cycles: %d
                - Average cycle duration: %.0f minutes

                Recent cycle setting details (newest first):
                %s

                Use this information when giving recommendations:
                - washer: temperature level, rinses, spin level, soil level, and cycle duration.
                - dryer: temperature level, dry level, wrinkle prevent, and cycle duration.
                - If data is missing, say that explicitly.
                - Focus recommendations on setting optimization and consistency.
                - Include at least one concrete example like reducing rinses/spin/soil or lowering dryer temperature.

                Respond ONLY with the following three sections (include the headers exactly):

                Energy Summary
                This needs to be 2-3 sentences about relative efficiency inferred from settings and durations

                Usage Observations
                This needs to be 2-3 sentences on pattern quality, including high/low setting use and long cycle durations. Note that the washer doesn't have wrinkle prevent, and the dryer doesn't have rinses or soil level, so don't include it in your response.

                Recommendations
                This needs to be 1-3 concise recommendations based on the recorded settings. Emphasize the importance of saving energy, water, time, and money.

                Keep the entire response under 3000 tokens. Do not add any extra sections. Do not use italics or bold formatting.
                """.formatted(
                // Pass # of records, #  of washer cycles, # of dryer cycles, 
                // average cycle duration, & cycle details to formatted string to fill in placeholder values
                // values can change from run to run, so we need to use the latest values
                records.size(),
                washerCycles, 
                dryerCycles,
                avgDuration,
                cycleDetails
        );
    }

    // Calls Gemini API and returns the response
    private String callGemini(String prompt) throws IOException {
        // Build JSON request body required by Gemini API
        ObjectNode body = mapper.createObjectNode();
        body.putArray("contents")
            .addObject()
            .putArray("parts")
            .addObject()
            .put("text", prompt);
        // I noted 400 token limit in my design proposal, but it needed to be increased because the response was too short with 400 tokens.
        // I also tried with 800 and 1000 tokens, but the response was still wasn't enough, so I just settled on 3000 tokens. This might be overkill though
        body.putObject("generationConfig").put("maxOutputTokens", 3000);

        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL + "?key=" + System.getenv("GEMINI_API_KEY")))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20)) // set a timeout for the API call if it takes too long. 20 seconds just to be safe. 
                // Anything longer than that will confuse users if the call takes that long
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // making the request to gemini, if successful, will generate a response which can 
        // be found in the nested JSON structure: candidates[0] -> content -> parts[0] -> text
        final HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("Gemini API error. HTTP status: {} - Body: {}", response.statusCode(), response.body());
                throw new IOException("Gemini API error. HTTP status: " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body()); // only response if successful
            return root.path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText("");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini request interrupted", e);
        }
    }
}
