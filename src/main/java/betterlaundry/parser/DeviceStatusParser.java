package betterlaundry.parser;

import betterlaundry.config.AppConfig;
import betterlaundry.config.DeviceConstants;
import betterlaundry.model.DryerStatus;
import betterlaundry.model.MachineState;
import betterlaundry.model.WasherStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;

// Parses SmartThings status JSON into washer/dryer status objects
// Missing or bad fields fall back to safe defaults
public class DeviceStatusParser {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusParser.class);

    // parse washer payload
    public WasherStatus parseWasher(JsonNode root) {
        JsonNode main = getMain(root);

        JsonNode opState = getCapability(main, DeviceConstants.CAP_WASHER_OPERATING_STATE);
        JsonNode washerTempNode = getCapability(main, DeviceConstants.CAP_WASHER_WATER_TEMPERATURE);
        JsonNode washerRinseNode = getCapability(main, DeviceConstants.CAP_WASHER_RINSE_CYCLES);
        JsonNode washerSoilNode = getCapability(main, DeviceConstants.CAP_WASHER_SOIL_LEVEL);
        JsonNode samsungWasherOp = getCapability(main, DeviceConstants.CAP_SAMSUNG_WASHER_OPERATING_STATE);
        JsonNode washerCycleNode = getCapability(main, DeviceConstants.CAP_WASHER_CYCLE);

        MachineState machineState = parseMachineState(
                opState, DeviceConstants.ATTR_MACHINE_STATE, DeviceConstants.ATTR_WASHER_JOB_STATE);
        String washerJobState = parseString(opState, DeviceConstants.ATTR_WASHER_JOB_STATE, "unknown");
        Instant completionTime = parseInstantFromAny(
                opState, DeviceConstants.ATTR_COMPLETION_TIME, "completionTime", "remainingTime");
        String waterTemp = parseString(washerTempNode, DeviceConstants.ATTR_WASHER_WATER_TEMPERATURE, "unknown");
        String rinseCycles = parseString(washerRinseNode, DeviceConstants.ATTR_WASHER_RINSE_CYCLES, "unknown");
        String soilLevel = parseString(washerSoilNode, DeviceConstants.ATTR_WASHER_SOIL_LEVEL, "unknown");

        String rawWasherCycle = parseStringFromAny(washerCycleNode, null, DeviceConstants.ATTR_WASHER_CYCLE);
        String cycleLabel = AppConfig.washerCycle(rawWasherCycle);
        Integer washerProgress = parseOptionalIntFromCapability(samsungWasherOp, DeviceConstants.ATTR_WASHER_PROGRESS);
        Integer washerRemaining = parseOptionalIntFromCapability(samsungWasherOp, DeviceConstants.ATTR_WASHER_REMAINING_TIME);

        return new WasherStatus(
                machineState, washerJobState, completionTime,
                cycleLabel, waterTemp, rinseCycles, soilLevel,
                washerProgress, washerRemaining, Instant.now()
        );
    }

    // parse dryer payload
    // same logic as parseWasher but the fields are specific to the dryer
    public DryerStatus parseDryer(JsonNode root) {
        JsonNode main = getMain(root);

        JsonNode opState = getCapability(main, DeviceConstants.CAP_DRYER_OPERATING_STATE);
        JsonNode samsungDryerOp = getCapability(main, DeviceConstants.CAP_SAMSUNG_DRYER_OPERATING_STATE);
        JsonNode dryTempNode = getCapability(main, DeviceConstants.CAP_DRYER_TEMPERATURE);
        JsonNode wrinklePreventNode = getCapability(main, DeviceConstants.CAP_DRYER_WRINKLE_PREVENT);
        JsonNode dryerCycleNode = getCapability(main, DeviceConstants.CAP_DRYER_CYCLE);

        MachineState machineState = parseMachineState(
                opState, DeviceConstants.ATTR_MACHINE_STATE, DeviceConstants.ATTR_DRYER_JOB_STATE);
        String dryerJobState = parseString(opState, DeviceConstants.ATTR_DRYER_JOB_STATE, "unknown");
        Instant completionTime = parseInstantFromAny(
                opState, DeviceConstants.ATTR_COMPLETION_TIME, "completionTime", "remainingTime");
        String rawCycle = parseStringFromAny(dryerCycleNode, null, DeviceConstants.ATTR_DRYER_CYCLE);
        String cycleLabel = AppConfig.dryerCycle(rawCycle);
        String dryingTemperature = parseStringFromAny(dryTempNode, "unknown",
                DeviceConstants.ATTR_DRYER_DRYING_TEMPERATURE, "dryerTemperature", "dryingTemperature");
        String wrinklePrevent = parseString(wrinklePreventNode, DeviceConstants.ATTR_DRYER_WRINKLE_PREVENT, "unknown");
        Integer progressPercent = parseOptionalIntFromCapability(samsungDryerOp, DeviceConstants.ATTR_DRYER_PROGRESS);
        Integer remainingMinutes = parseOptionalIntFromCapability(samsungDryerOp, DeviceConstants.ATTR_DRYER_REMAINING_TIME);

        return new DryerStatus(
                machineState, dryerJobState, completionTime,
                cycleLabel, dryingTemperature, wrinklePrevent,
                progressPercent, remainingMinutes, Instant.now()
        );
    }

    // reads numeric attribute value (e.g. progress, remainingTime) from capability object
    private Integer parseOptionalIntFromCapability(JsonNode capability, String attribute) {
        if (capability == null || capability.isMissingNode()) {
            return null;
        }
        JsonNode attr = capability.path(attribute);
        if (attr.isMissingNode() || attr.isNull()) {
            return null;
        }
        JsonNode value = attr.path("value");
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            if (value.isInt() || value.isLong()) {
                return value.intValue();
            }
            if (value.isNumber()) {
                return (int) Math.round(value.doubleValue());
            }
            String s = value.asText(null);
            if (s == null || s.isBlank()) {
                return null;
            }
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            log.debug("Could not parse integer for attribute '{}': {}", attribute, value);
            return null;
        }
    }

    // helper methods return defaults when data is missing or invalid
    private JsonNode getMain(JsonNode root) {
        if (root == null) return MissingNode;
        JsonNode components = root.path("components");
        return components.path("main");
    }

    private JsonNode getCapability(JsonNode main, String capabilityKey) {
        if (main == null || main.isMissingNode()) return MissingNode;
        return main.path(capabilityKey);
    }

    private MachineState parseMachineState(JsonNode capability, String attribute, String fallbackJobAttribute) {
        String raw = parseString(capability, attribute, null);
        MachineState state = MachineState.fromApiValue(raw);
        if (state == MachineState.UNKNOWN && fallbackJobAttribute != null) {
            String job = parseString(capability, fallbackJobAttribute, null);
            state = inferStateFromJobState(job);
        }
        if (state == MachineState.UNKNOWN && raw != null) {
            log.debug("Unrecognized machineState value: '{}'", raw);
        }
        return state;
    }

    private String parseString(JsonNode capability, String attribute, String defaultValue) {
        if (capability == null || capability.isMissingNode()) return defaultValue;
        JsonNode attr = capability.path(attribute);
        if (attr.isMissingNode() || attr.isNull()) return defaultValue;
        JsonNode value = attr.path("value");
        if (value.isMissingNode() || value.isNull()) return defaultValue;
        return value.asText(defaultValue);
    }

    private String parseStringFromAny(JsonNode capability, String defaultValue, String... attributes) {
        for (String attr : attributes) {
            String parsed = parseString(capability, attr, null);
            if (parsed != null && !parsed.isBlank()) {
                return parsed;
            }
        }
        return defaultValue;
    }

    private String formatAttributeValueAsText(JsonNode capability, String attribute) {
        if (capability == null || capability.isMissingNode()) {
            return "unknown";
        }
        JsonNode attrNode = capability.path(attribute);
        if (attrNode.isMissingNode() || attrNode.isNull()) {
            return "unknown";
        }
        JsonNode value = attrNode.path("value");
        if (value.isMissingNode() || value.isNull()) {
            return "unknown";
        }
        if (value.isArray()) {
            if (value.isEmpty()) {
                return "unknown";
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : value) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(n.asText(""));
            }
            return sb.isEmpty() ? "unknown" : sb.toString();
        }
        String s = value.asText();
        return (s == null || s.isBlank()) ? "unknown" : s;
    }

    private Instant parseInstant(JsonNode capability, String attribute) {
        String raw = parseString(capability, attribute, null);
        if (raw == null) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            log.warn("could not parse completionTime '{}': {}", raw, e.getMessage());
            return null;
        }
    }

    private Instant parseInstantFromAny(JsonNode capability, String... attributes) {
        for (String attr : attributes) {
            Instant parsed = parseInstant(capability, attr);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private MachineState inferStateFromJobState(String rawJobState) {
        if (rawJobState == null || rawJobState.isBlank()) return MachineState.UNKNOWN;
        String normalized = rawJobState.toLowerCase();
        if (normalized.contains("pause")) return MachineState.PAUSED;
        if (normalized.contains("finish") || normalized.contains("end") || normalized.contains("complete")) {
            return MachineState.FINISHED;
        }
        if (normalized.contains("stop") || normalized.contains("idle") || normalized.contains("none")) {
            return MachineState.STOP;
        }
        return MachineState.RUNNING;
    }

    // reused missing-node instance to avoid null checks everywhere
    private static final JsonNode MissingNode = com.fasterxml.jackson.databind.node.MissingNode.getInstance();

}
