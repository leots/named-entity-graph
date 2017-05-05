import csv_export.CSVExporter;
import csv_export.ComparisonContainer;
import entity_extractor.*;
import utils.Methods;
import utils.Percentage;
import utils.VerySimpleFormatter;
import utils.tf_idf.DocumentParser;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

/**
 * Main program.
 * Reads a folder of text files, gets entities for each using the specified entity extractor, the compares all texts
 * with all other texts with all the available methods, and outputs the results to a CSV file (and a log file)
 */
public class TextComparator {
    private final static boolean cacheGraphs = true;    // Enable/disable caching of the graphs in memory for speed, but can use a lot of RAM
    private final boolean keepTopTerms;   // If true, will leave top terms (ranked by TF-IDF) in the text when making the graphs

    private final Logger LOGGER = Logger.getLogger("NamedEntityGraph");

    public TextComparator() {
        // Enable TF-IDF if placeholder method is enabled
        this.keepTopTerms = Methods.isEnabled(Methods.PLACEHOLDER);
    }

    public static void main(String[] args) {
        // Initialize the static hashmap
        new Methods();

        long totalTimeStart = System.currentTimeMillis();
        TextComparator neg = new TextComparator();

        try {
            neg.start();
        } catch (IOException e) {
            System.err.println("Problem writing log file");
        }

        long totalTimeEnd = System.currentTimeMillis();
        System.out.println("Total time: " + ((totalTimeEnd - totalTimeStart) / 1000.0) + " seconds");
    }

    private void start() throws IOException {
        // Setup logger
        LOGGER.setLevel(Level.FINEST);
        LOGGER.setUseParentHandlers(false);

        // Add handlers to the logger
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new VerySimpleFormatter());
        LOGGER.addHandler(consoleHandler);

        Handler fileHandler = new FileHandler("./neg.log");
        fileHandler.setLevel(Level.FINEST);
        fileHandler.setFormatter(new VerySimpleFormatter());
        LOGGER.addHandler(fileHandler);

        // Main variables
        String inputFolder = "texts/input";

        ArrayList<String> placeholders = new ArrayList<>();
//        placeholders.add(".");
//        placeholders.add("");
//        placeholders.add("-");
        placeholders.add("A");

        long startTime = 0;
        long endTime = 0;
        File input = new File(inputFolder);
        EntityExtractor entityExtractor = new OpenCalaisExtractor();
        ArrayList<TextEntities> texts = new ArrayList<>();
        Map<String, GraphCache> graphs = new HashMap<>();
        ArrayList<String> errors = new ArrayList<>();

        try {
            if (input.isDirectory()) {
                LOGGER.log(Level.INFO, "Working on all files in " + input.getAbsolutePath());

                // Calculate TF-IDF of documents, so we can keep top terms
                startTime = System.currentTimeMillis();
                DocumentParser dp = new DocumentParser();
                if (keepTopTerms) {
                    dp.parseFiles(input.getPath());
                    dp.tfIdfCalculator();
                }
                endTime = System.currentTimeMillis();

                // Get text entities and create graphs (if we should cache them)
                LOGGER.log(Level.INFO, "TF-IDF Calculation complete, getting text entities...");
                File[] files = input.listFiles();
                if (files != null) {
                    int i = 1;
                    int totalFiles = files.length;
                    double percentage = 0;
                    double currPercent;

                    for (File file : files) {
                        if (file.isFile()) {
                            // Log the progress so far
                            currPercent = Percentage.percent(i, totalFiles);
                            Level lvl = Level.FINE;
                            if (currPercent - percentage > 10 || i == totalFiles) {
                                lvl = Level.INFO;
                                percentage = currPercent;
                            }

                            LOGGER.log(lvl, String.format("[main] (" + i + "/" + files.length + " - %.2f%%) Getting entities for " + file + "", currPercent));

                            // Get entities for this file and save them
                            TextEntities entities = entityExtractor.getEntities(file);
//                            entities.printEntities();
                            texts.add(entities);

                            // Calculate graphs for this text and save them (if caching is enabled)
                            GraphCache cache = new GraphCache(entities, dp);
                            if (cacheGraphs) {
                                cache.calculateGraphs(entities, placeholders);
                            }
                            graphs.put(entities.getTitle(), cache);

                            LOGGER.log(Level.FINE, "[main] Got " + entities.getEntities().size() + " extracted entities from " + file + "\n");
                        } else {
                            LOGGER.log(Level.FINE, "Skipping " + file.getAbsolutePath());
                        }

                        i++;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, Arrays.toString(e.getStackTrace()));
            LOGGER.log(Level.SEVERE, "Not comparing anything, there was an error");
            return;
        }

        // Compare every text with every other text
        LOGGER.log(Level.INFO, "Starting text comparisons...");

        // List to keep all comparisons that were made to write them to CSV file
        List<ComparisonContainer> comparisons = Collections.synchronizedList(new ArrayList<ComparisonContainer>());

        int textsLen = texts.size();
        int cores = Runtime.getRuntime().availableProcessors();
        LOGGER.log(Level.INFO, "Using " + cores + " cores...");

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(cores);

        // Start a thread for each CPU core
        for (int i = 0; i < cores; i++) {
            ComparisonWorker r = new ComparisonWorker(i, cores, textsLen, placeholders, errors, texts, graphs, comparisons);
            executor.execute(r);
        }

        // Wait for threads to finish before continuing
        executor.shutdown();

        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Print any errors that occurred
        if (errors.size() > 0) {
            LOGGER.log(Level.SEVERE, "Errors:");

            for (String error : errors) {
                LOGGER.log(Level.SEVERE, error);
            }
        }

        // Export to CSV
        CSVExporter.exportCSV("out.csv", placeholders, comparisons);

        System.out.println("TF-IDF time: " + ((endTime - startTime) / 1000.0) + " seconds");
    }
}
