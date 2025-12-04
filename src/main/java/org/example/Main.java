package org.example;

import org.example.models.League;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "fantasy-playoff-odds", mixinStandardHelpOptions = true, version = "1.0", description = "Fantasy Football Playoff Odds Simulator")
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
            System.out.println("Fantasy Football Playoff Odds Simulator");
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

            // Check if there are any remaining games
            if (league.getRemainingMatchups().isEmpty()) {
                System.out.println("No remaining games to simulate. Season is complete!");
                System.out.println("\nFinal Standings:");
                // Still run simulation with 0 games to get final standings
            }

            // Step 2: Run simulation
            System.out.println("Starting simulation...");
            SimulationEngine simulator = new SimulationEngine(league);
            long startTime = System.currentTimeMillis();
            simulator.runSimulation();
            long endTime = System.currentTimeMillis();

            System.out.printf("Simulation completed in %.2f seconds\n",
                    (endTime - startTime) / 1000.0);

            // Step 3: Analyze results
            ClinchAnalyzer analyzer = new ClinchAnalyzer(league, simulator);

            // Step 4: Display results
            System.out.println(analyzer.generateSummaryReport());

            // Step 5: Show clinch scenarios for teams in contention
            System.out.println(analyzer.generateClinchScenariosReport());

            // Show detailed analysis if verbose mode
            if (config.isVerbose()) {
                System.out.println("\n" + "=".repeat(70));
                System.out.println("DETAILED TEAM ANALYSIS");
                System.out.println("=".repeat(70));

                for (var entry : analyzer.getSortedOdds()) {
                    System.out.println(analyzer.getDetailedAnalysis(entry.getKey()));
                }
            }

            return 0; // Success

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());

            if (e instanceof java.io.IOException && e.getMessage().contains("not found")) {
                System.err.println("\nTo create a sample configuration file, run:");
                System.err.println("  java -jar FF-1.0-SNAPSHOT.jar --generate-config");
            }

            e.printStackTrace();
            return 1; // Failure
        }
    }
}