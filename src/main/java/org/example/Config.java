package org.example;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration loader for the Fantasy Football Playoff Odds Simulator.
 * Reads configuration from a JSON file.
 */
public class Config {
    private int leagueId;
    private Integer year;
    private String espnS2;
    private String swid;
    private boolean verbose;

    // Default config file name
    private static final String DEFAULT_CONFIG_FILE = "config.json";

    /**
     * Load configuration from the default config file.
     */
    public static Config load() throws IOException {
        return load(DEFAULT_CONFIG_FILE);
    }

    /**
     * Load configuration from a specified file.
     */
    public static Config load(String configPath) throws IOException {
        Path path = Paths.get(configPath);

        if (!Files.exists(path)) {
            throw new IOException("Configuration file not found: " + configPath);
        }

        String content = Files.readString(path);
        JSONObject json = new JSONObject(content);

        Config config = new Config();

        // Required field
        if (!json.has("leagueId")) {
            throw new IllegalArgumentException("Configuration must contain 'leagueId'");
        }
        config.leagueId = json.getInt("leagueId");

        // Optional fields
        if (json.has("year")) {
            config.year = json.getInt("year");
        }

        if (json.has("espnS2")) {
            config.espnS2 = json.getString("espnS2");
        }

        if (json.has("swid")) {
            config.swid = json.getString("swid");
        }

        if (json.has("verbose")) {
            config.verbose = json.getBoolean("verbose");
        } else {
            config.verbose = false;
        }

        return config;
    }

    /**
     * Create a sample configuration file for users.
     */
    public static void createSampleConfig(String outputPath) throws IOException {
        JSONObject sample = new JSONObject();
        sample.put("leagueId", 123456);
        sample.put("year", 2024);
        sample.put("espnS2", "YOUR_ESPN_S2_COOKIE_HERE (optional, for private leagues)");
        sample.put("swid", "YOUR_SWID_COOKIE_HERE (optional, for private leagues)");
        sample.put("verbose", false);

        String prettyJson = sample.toString(2); // 2-space indentation
        Files.writeString(Paths.get(outputPath), prettyJson);
    }

    // Getters
    public int getLeagueId() {
        return leagueId;
    }

    public Integer getYear() {
        return year;
    }

    public String getEspnS2() {
        return espnS2;
    }

    public String getSwid() {
        return swid;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public String toString() {
        return String.format("Config{leagueId=%d, year=%s, hasAuth=%s, verbose=%s}",
                leagueId,
                year != null ? year : "current",
                (espnS2 != null && swid != null) ? "yes" : "no",
                verbose);
    }
}
