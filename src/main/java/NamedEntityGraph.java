import entity_extractor.EntityExtractor;
import entity_extractor.MyRunnable;
import entity_extractor.OpenCalaisExtractor;
import entity_extractor.TextEntities;
import utils.Percentage;
import utils.VerySimpleFormatter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.*;

public class NamedEntityGraph {
    private final Logger LOGGER = Logger.getLogger("NamedEntityGraph");
    private ArrayList<String> placeholders;
    private Handler consoleHandler = null;
    private Handler fileHandler = null;

    public static void main(String[] args) {
        NamedEntityGraph neg = new NamedEntityGraph();

        try {
            neg.start();
        } catch(IOException e) {
            System.err.println("Problem writing log file");
        }
    }

    public void start() throws IOException {
        // Setup logger
        LOGGER.setLevel(Level.FINEST);
        LOGGER.setUseParentHandlers(false);

        // Add handlers to the logger
        consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new VerySimpleFormatter());
        LOGGER.addHandler(consoleHandler);

        fileHandler = new FileHandler("./neg.log");
        fileHandler.setLevel(Level.FINEST);
        fileHandler.setFormatter(new VerySimpleFormatter());
        LOGGER.addHandler(fileHandler);

        // Main variables
        String inputFolder = "texts/input";

        placeholders = new ArrayList<>();
//        placeholders.add(".");
//        placeholders.add("");
//        placeholders.add("-");
        placeholders.add("A");

        File input = new File(inputFolder);
        EntityExtractor entityExtractor = new OpenCalaisExtractor();
        ArrayList<TextEntities> texts = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();

        try {
            if (input.isDirectory()) {
                LOGGER.log(Level.INFO, "working on all files in " + input.getAbsolutePath());
                File[] files = input.listFiles();
                if (files != null) {
                    int i = 1;
                    int totalFiles = files.length;
                    double percentage = 0;
                    double currPercent;

                    for (File file : files) {
                        if (file.isFile()) {
                            currPercent = Percentage.percent(i, totalFiles);
                            Level lvl = Level.FINE;
                            if (currPercent - percentage > 1) {
                                lvl = Level.INFO;
                                percentage = currPercent;
                            }

                            LOGGER.log(lvl, String.format("[main] (" + i + "/" + files.length + " - %.2f%%) Getting entities for " + file + "", currPercent));

                            TextEntities entities = entityExtractor.getEntities(file);
//                            entities.printEntities();

                            texts.add(entities);

                            LOGGER.log(Level.FINE, "[main] Got " + entities.getEntities().size() + " extracted entities from " + file + "\n");
                        } else {
                            LOGGER.log(Level.FINE, "Skipping " + file.getAbsolutePath());
                        }

                        i++;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getStackTrace().toString());
            LOGGER.log(Level.SEVERE, "Not comparing anything, there was an error");
            return;
        }

        // Compare every text with every other text
        LOGGER.log(Level.INFO, "Starting text comparisons...");

        int textsLen = texts.size();
        int totalComparisons = textsLen * (textsLen - 1) / 2;
        int comparisonsDone = 0;
        ArrayList<Thread> myThreads = new ArrayList<>();

        int cores = Runtime.getRuntime().availableProcessors();
        LOGGER.log(Level.INFO, "Using " + cores + " cores...");

        for (int i = 0; i < textsLen - 1; i += cores) {
            int comparisonsForTheseThreads = 0;
            for (int h = 0; h < cores; h++) {
                if (i + h < textsLen) {
                    comparisonsForTheseThreads += textsLen - (i + 1);
                    MyRunnable r = new MyRunnable(i + h, textsLen, placeholders, errors, texts);
                    Thread t = new Thread(r);
                    myThreads.add(t);

                    t.start();
                }
            }

            // wait all threads to finish
            for (Thread t : myThreads) {
                try {
                    t.join();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }

            comparisonsDone += comparisonsForTheseThreads;
            LOGGER.log(Level.INFO, String.format("Progress: %.2f%% - " + comparisonsDone + "/" + totalComparisons + " comparisons", Percentage.percent(comparisonsDone, totalComparisons)));
        }

        // Print any errors that occurred
        if (errors.size() > 0) {
            LOGGER.log(Level.SEVERE, "Errors:");

            for (String error : errors) {
                LOGGER.log(Level.SEVERE, error);
            }
        }
    }
}
