package betterlaundry.exception;

// Checked exception for SmartThings API request failures
public class SmartThingsAPIException extends Exception {
    public SmartThingsAPIException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
