package net.internetmemory.genreclassification;
import org.apache.commons.cli.*;

import java.io.IOException;

/**
 * Created by jie on 04/07/17.
 */
public class Predict {
    private static String url;
    private static String modelPath;

    private static void processInput(String[] args) throws ParseException, IOException {
        Options options = new Options();
        options.addOption("url", true, "url to predict");
        options.addOption("modelPath", true, "path to model.bin");
        options.addOption("h", false, "display this help");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Predict", options);
            System.exit(1);
        }

        if (!cmd.hasOption("modelPath") || cmd.getOptionValue("modelPath").isEmpty()) {
            System.out.println("Please specify model path.");
            System.exit(1);
        }

        if(!cmd.hasOption("url") || cmd.getOptionValue("url").isEmpty()){
            System.out.println("Please specify the url to predict.");
            System.exit(1);
        }

        url = cmd.getOptionValue("url");
        modelPath = cmd.getOptionValue("modelPath");
    }

    public static void main(String[] args) throws Exception {
        processInput(args);
        ClassificationWekaModel model = new ClassificationWekaModel();
        model.loadModel(modelPath);
        model.predict(url);
        model.destroy();

    }

}
