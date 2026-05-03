package betterlaundry.db;

import betterlaundry.interfaces.Persistable;
import betterlaundry.model.CycleRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager implements Persistable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    // create database, provide schema 
    public static void initialize() {

        String createTableSql = """
        CREATE TABLE IF NOT EXISTS cycle_records ( 
            id SERIAL PRIMARY KEY,
            device_type TEXT NOT NULL,
            device_id TEXT NOT NULL,
            start_time BIGINT NOT NULL,
            end_time BIGINT NOT NULL,
            cycle_type TEXT,
            temperature_level TEXT,
            rinses TEXT,
            soil_level TEXT,
            wrinkle_prevent TEXT,
            UNIQUE (device_id, start_time, end_time)
        );
        """;

        try (Connection conn = getConnection(); 
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            log.info("Database created.");
        } catch (SQLException e) {
            log.error("Failed to create cycle_records table", e);
        }
    }
    // save the cycle record to the database -> cycle record is in memory while machine is running,
    // but we need to store it in the database so we can analyze the data later
    @Override
    public void save(CycleRecord record) {
        String sql = """
                INSERT INTO cycle_records
                    (device_type, device_id, start_time, end_time, cycle_type,
                     temperature_level, rinses, soil_level, wrinkle_prevent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (device_id, start_time, end_time)
                DO UPDATE SET
                    device_type = EXCLUDED.device_type,
                    cycle_type = EXCLUDED.cycle_type,
                    temperature_level = EXCLUDED.temperature_level,
                    rinses = EXCLUDED.rinses,
                    soil_level = EXCLUDED.soil_level,
                    wrinkle_prevent = EXCLUDED.wrinkle_prevent;
                """;
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, record.getDeviceType());
            ps.setString(2, record.getDeviceId());
            ps.setLong(3, record.getStartTime().toEpochMilli());
            ps.setLong(4, record.getEndTime().toEpochMilli());
            ps.setString(5, record.getCycleLabel());
            ps.setString(6, record.getTemperatureLevel());
            ps.setString(7, record.getRinses());
            ps.setString(8, record.getSoilLevel());
            ps.setString(9, record.getWrinklePrevent());

            ps.executeUpdate();
            log.info("Saved/Updated CycleRecord for device: {}", record.getDeviceId());
        } catch (SQLException e) {
            log.error("Failed to save CycleRecord: {}", record.getDeviceId(), e);
        }
    }

    // load essentially does the opposite of save -> it pulls the cycle records from the database and returns them as a list
    // this is how we can get the data out of the database and into the application
    @Override
    public List<CycleRecord> load(int limit) {
        String sql = """
                SELECT device_type, device_id, start_time, end_time, cycle_type,
                       temperature_level, rinses, soil_level, wrinkle_prevent
                FROM cycle_records
                ORDER BY end_time DESC LIMIT ?
                """;
        List<CycleRecord> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit); // prevent SQL injection by limiting the number of records returned to 1 at a time
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new CycleRecord(
                        rs.getString("device_type"),
                        rs.getString("device_id"),
                        // getting start time and end time as Instant objects:
                        // Obtains an instance of Instant using seconds from the epoch of 1970-01-01T00:00:00Z.
                        Instant.ofEpochMilli(rs.getLong("start_time")),
                        Instant.ofEpochMilli(rs.getLong("end_time")),
                        rs.getString("cycle_type"),
                        rs.getString("temperature_level"),
                        rs.getString("rinses"),
                        rs.getString("soil_level"),
                        rs.getString("wrinkle_prevent")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load cycle records", e);
        }
        return results;
    }

    private static Connection getConnection() throws SQLException {
    String dbUrl = System.getenv("DATABASE_URL");
    // fallback for local development if DATABASE_URL isn't set
    if (dbUrl == null) {
        return DriverManager.getConnection(DbConfig.JDBC_URL);
    }
    try {
        java.net.URI dbUri = new java.net.URI(dbUrl);
        String username = dbUri.getUserInfo().split(":")[0];
        String password = dbUri.getUserInfo().split(":")[1];
        String jdbcUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + dbUri.getPort() + dbUri.getPath();
        // essential plan requires SSL
        return DriverManager.getConnection(jdbcUrl + "?sslmode=require", username, password);
    } catch (java.net.URISyntaxException e) {
        log.error("Unable to parse DATABASE_URL: {}", dbUrl);
        throw new SQLException("Invalid DATABASE_URL syntax", e);
    }
}
}