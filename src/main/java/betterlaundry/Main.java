package betterlaundry;

import betterlaundry.ai.GeminiAnalysisService;
import betterlaundry.client.SmartThingsClient;
import betterlaundry.config.AppConfig;
import betterlaundry.db.DatabaseManager;
import betterlaundry.db.DbConfig;
import betterlaundry.model.DryerDevice;
import betterlaundry.model.LaundryDevice;
import betterlaundry.model.WasherDevice;
import betterlaundry.parser.DeviceStatusParser;
import betterlaundry.polling.DevicePollingService;
import betterlaundry.server.DashboardServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("BetterLaundry starting up...");
        
        AppConfig config = AppConfig.load();
        log.info("Config loaded. Polling every {}s.", config.pollingIntervalSeconds());

        String herokuPort = System.getenv("PORT"); // check if we're running on Heroku and use its port, if not then use port from config
        int port = (herokuPort != null) ? Integer.parseInt(herokuPort) : config.port();

        DatabaseManager db = new DatabaseManager();

        SmartThingsClient smartThings = new SmartThingsClient(config);

        DeviceStatusParser parser = new DeviceStatusParser();
        List<LaundryDevice> devices = List.of(
                new WasherDevice(config.washerDeviceId()),
                new DryerDevice(config.dryerDeviceId())
        );

        DevicePollingService polling = new DevicePollingService(
                smartThings, parser, devices, db, config.pollingIntervalSeconds()
        );
        polling.start();
        log.info("Polling service started for washer and dryer.");

        GeminiAnalysisService ai = new GeminiAnalysisService(config);
        DashboardServer server = new DashboardServer(polling, db, ai, port);
        server.start();
        log.info("Dashboard live on port {}.", port);
    }
}
