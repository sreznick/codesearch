package codegrep.util;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Settings {
    // TODO: case sensitive search (Lucene doesn't seem to like it a lot)
    // TODO: match entire literal (same as above)
    private String syntax = "codegrep [options...] [stringToSearch]";

    private final Options options;

    private String searchString = null;
    public String searchString() {
        return searchString;
    }

    private boolean regex = false;
    public boolean regex() {
        return regex;
    }

    private boolean onlyCount = false;
    public boolean onlyCount() {
        return onlyCount;
    }

    private boolean onlyFileNames = false;
    public boolean onlyFileNames() {
        return onlyFileNames;
    }

    private boolean quiet = false;
    public boolean quiet() {
        return quiet;
    }

    private boolean debug = false;
    public boolean debug() {
        return debug;
    }

    private boolean verboseDebug = false;
    public boolean verboseDebug() {
        return verboseDebug;
    }

    private boolean useCache = true;
    public boolean useCache() {
        return useCache;
    }

    public Settings(String[] args) {
        options = new Options();
        options.addOption("R", "regex", false, "Enable regular expressions");
        options.addOption("c", "count", false, "Only count literals in each file, but do not print them");
        options.addOption("l", "files-with-matches", false, "Only print matched file names, but not literals");
        options.addOption("q", "quiet", false, "Do not print anything, exit with 0 or 1 status depending if any matches were found");
        options.addOption("D", "debug", false, "Show debug information");
        options.addOption(null, "verbose-debug", false, "Show more debug information");
        options.addOption("h", "help", false, "Show the help and quit");
        options.addOption(null, "skip-cache", false, "Skip cache and search in files directly");

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            List<String> remainders = cmd.getArgList();
            if (remainders.size() != 1 || cmd.hasOption("help")) {
                if (cmd.hasOption("help")) {
                    printHelp();
                } else {
                    printUsage();
                }
            } else {
                searchString = remainders.get(0);
                if (cmd.hasOption("regex")) regex = true;
                if (cmd.hasOption("count")) onlyCount = true;
                if (cmd.hasOption("files-with-matches")) onlyFileNames = true;
                if (cmd.hasOption("quiet")) quiet = true;
                if (cmd.hasOption("debug")) debug = true;
                if (cmd.hasOption("debug") && cmd.hasOption("verbose-debug")) verboseDebug = true;
                if (cmd.hasOption("skip-cache")) useCache = false;
            }
        } catch (ParseException e) {
            System.err.println("Parsing failed. Reason: " + e.getMessage());
        }
    }

    private void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(syntax, options);
    }

    private void printUsage() {
        System.out.println("Usage: " + syntax);
        System.out.println("Use -h or --help to display the full help message");
    }
}
