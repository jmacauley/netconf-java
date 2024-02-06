package net.es.netapps.netconf;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Discover {
    private static final String TYPE = "type";
    private static final String DEVICE = "device";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String NOKIA = "nokia";
    private static final String G30 = "g30";
    private static final String G40 = "g40";
    private static final String WAVESERVER = "waveserver";

    private static final Logger logger = LoggerFactory.getLogger(Discover.class);

    private Discover() {
        logger.info("[Discover] starting...");
    }

    public static void main(String[] args) throws ParseException {

        // Create Options object to hold our command line options.
        Options options = new Options();

        // Need to know the device type which we are connecting.
        Option typeOption = new Option(TYPE, true, "Type of netconf device.");
        typeOption.setRequired(true);
        options.addOption(typeOption);

        // Need to know the device to which we are connecting.
        Option deviceOption = new Option(DEVICE, true, "Name of the netconf device.");
        deviceOption.setRequired(true);
        options.addOption(deviceOption);

        // Need to know the username we can use for authentication with the device.
        Option usernameOption = new Option(USERNAME, true, "User name used to authenticate with device.");
        usernameOption.setRequired(true);
        options.addOption(usernameOption);

        // Need to know the password we can use for authentication with the device.
        Option pwOption = new Option(PASSWORD, true, "Password associated with user name.");
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
        String deviceType = cmd.getOptionValue(TYPE, NOKIA);
        String deviceName = cmd.getOptionValue(DEVICE, "localhost");
        String username = cmd.getOptionValue(USERNAME, "admin");
        String password = cmd.getOptionValue(PASSWORD, "admin");

        List<Document> documents = new ArrayList<>();
        if (NOKIA.equalsIgnoreCase(deviceType)) {
            // Discover a Nokia device.
            Nokia device = new Nokia(deviceName, username, password, 60 * 60 * 1000);
            documents.addAll(device.discover());
        } else if (WAVESERVER.equalsIgnoreCase(deviceType)) {
            // Discover a Ciena Waveserver Transponder.

        } else if (G30.equalsIgnoreCase(deviceType)) {
            // Discover an Infinera G30 Transponder.
        } else if (G40.equalsIgnoreCase(deviceType)) {
            // Discover an Infinera G40 Transponder.
        }

        documents.forEach(d -> {
            logger.debug("Namespace: {}, Element: {},\n{}", d.getNamespace(), d.getElement(), d.getDocument());
        });
    }
}