package betterlaundry.interfaces;

import betterlaundry.model.CycleRecord;

import java.util.List;

// save/load contract for cycle history records
public interface Persistable {
    // save one completed cycle record
    void save(CycleRecord record);

    // load newest records first, up to limit
    List<CycleRecord> load(int limit);
}
