package net.es.netapps.netconf;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.cli.*;


public class Discover {
    private static final Logger logger = LoggerFactory.getLogger(Discover.class);

    private Discover() {
        logger.info("[Discover] starting...");
    }

    public static void main(String[] args) throws ParseException, ParserConfigurationException, IOException {

        // Create Options object to hold our command line options.
        Options options = new Options();

        // Need to know the device to which we are connecting.
        Option deviceOption = new Option("device", true, "Name of the netconf device.");
        deviceOption.setRequired(true);
        options.addOption(deviceOption);

        // Need to know the username we can use for authentication with the device.
        Option usernameOption = new Option("username", true, "User name used to authenticate with device.");
        usernameOption.setRequired(true);
        options.addOption(usernameOption);

        // Need to know the password we can use for authentication with the device.
        Option pwOption = new Option("password", true, "Password associated with user name.");
        pwOption.setRequired(true);
        options.addOption(pwOption);

        // Parse the command line arguments provided.
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error: You did not provide the correct arguments, see usage below.");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("netconf-java -device <device dns or ip>> -username <username> -password <password>", options);
            throw e;
        }

        // Get the command line arguments provided.
        String deviceName = cmd.getOptionValue("device", "localhost");
        String username = cmd.getOptionValue("username", "admin");
        String password = cmd.getOptionValue("password", "admin");

        // Create a Nokia device.
        Nokia device = new Nokia(deviceName, username, password, 60*60*1000);
        device.discover();
    }
}