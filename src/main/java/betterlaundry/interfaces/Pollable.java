package betterlaundry.interfaces;

import betterlaundry.exception.SmartThingsAPIException;

public interface Pollable {
    // fetch latest state from SmartThings
    void poll() throws SmartThingsAPIException;
}
