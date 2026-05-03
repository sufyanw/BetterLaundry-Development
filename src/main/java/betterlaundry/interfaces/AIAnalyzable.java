package betterlaundry.interfaces;

import betterlaundry.model.CycleRecord;

import java.util.List;

public interface AIAnalyzable {
    // generate summary text from cycle history
    String generateSummary(List<CycleRecord> records);
}
