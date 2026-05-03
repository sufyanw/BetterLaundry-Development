package betterlaundry.db;

public final class DbConfig {
    private DbConfig() {}
    public static final String JDBC_URL = System.getenv("DATABASE_URL")
            .replace("postgres://", "jdbc:postgresql://");
}