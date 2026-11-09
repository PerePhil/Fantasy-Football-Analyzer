package org.example;

import org.example.models.League;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "fantasy-clinch-scenarios", mixinStandardHelpOptions = true, version = "1.0", description = "Fantasy Football Playoff Clinch Scenarios")
public class Main implements Callable<Integer> {

    @Option(names = { "-c", "--config" }, description = "Path to configuration file (default: config.json)")
    private String configPath = "config.json";

    @Option(names = { "--generate-config" }, description = "Generate a sample configuration file and exit")
    private boolean generateConfig;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            // Handle config generation
            if (generateConfig) {
                String outputPath = configPath;
                Config.createSampleConfig(outputPath);
                System.out.println("Sample configuration file created: " + outputPath);
                System.out.println("Edit this file with your league settings and run again.");
                return 0;
            }

            // Load configuration
            System.out.println("Fantasy Football Playoff Clinch Scenarios");
            System.out.println("========================================\n");
            System.out.printf("Loading configuration from: %s\n", configPath);

            Config config = Config.load(configPath);
            System.out.println("Configuration loaded: " + config);
            System.out.println();

            // Use current year if not specified in config
            int seasonYear = (config.getYear() != null) ? config.getYear() : java.time.Year.now().getValue();

            System.out.printf("Fetching league data (League ID: %d, Year: %d)...\n",
                    config.getLeagueId(), seasonYear);

            // Step 1: Fetch league data from ESPN API
            ESPNClient client = new ESPNClient(
                    config.getLeagueId(),
                    seasonYear,
                    config.getEspnS2(),
                    config.getSwid());
            League league = client.fetchLeagueData();

            System.out.println("League data loaded successfully!\n");

            if (league.getRemainingMatchups().isEmpty()) {
                System.out.println("No regular-season games remain. Showing final standings.\n");
            }

            // Step 2: Check every remaining win/loss outcome
            ScenarioEngine engine = new ScenarioEngine(league);
            System.out.printf("Checking %,d win/loss outcomes (%d remaining games)...%n",
                    engine.getTotalOutcomes(), engine.getGames().size());
            long startTime = System.nanoTime();
            ClinchAnalyzer analyzer = new ClinchAnalyzer(league, engine);
            System.out.printf("Done in %.2f seconds%n", (System.nanoTime() - startTime) / 1e9);

            // Step 3: Report
            System.out.println(analyzer.generateSummaryReport());
            System.out.println(analyzer.generateClinchScenariosReport());

            if (config.isVerbose()) {
                System.out.println("=".repeat(78));
                System.out.println("REMAINING SCHEDULE");
                System.out.println("=".repeat(78));
                System.out.println(analyzer.getRemainingSchedule());
            }

            return 0; // Success

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());

            if (e instanceof java.io.IOException && e.getMessage() != null && e.getMessage().contains("not found")) {
                System.err.println("\nTo create a sample configuration file, run:");
                System.err.println("  java -jar FF-1.0-SNAPSHOT.jar --generate-config");
            }

            e.printStackTrace();
            return 1; // Failure
        }
    }
}