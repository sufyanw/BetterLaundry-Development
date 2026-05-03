package betterlaundry.exception;

// Startup config error for missing or invalid environment variables
public class ConfigurationException extends RuntimeException {
    public ConfigurationException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
