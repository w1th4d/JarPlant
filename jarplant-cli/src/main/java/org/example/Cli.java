package org.example;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.example.implants.ClassImplant;
import org.example.implants.SpringImplantConfiguration;
import org.example.implants.SpringImplantController;
import org.example.injector.ClassInjector;
import org.example.injector.SpringInjector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Cli {
    enum Command {
        CLASS_INJECTOR, SPRING_INJECTOR
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("JarPlant").build()
                .defaultHelp(true)
                .description("Java Archive Implant Toolkit.");
        Subparsers subparsers = parser.addSubparsers()
                .metavar("command");

        Subparser classInjectorParser = subparsers.addParser("class-injector")
                .help("Inject a regular class implant into a JAR containing any classes.")
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
                .choices("ClassImplant")
                .setDefault("ClassImplant");

        Subparser springInjectorParser = subparsers.addParser("spring-injector")
                .help("Inject a Spring component into a JAR packaged Spring application.")
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
        springInjectorParser.addArgument("--implant-config")
                .choices("SpringImplantConfiguration")
                .setDefault("SpringImplantConfiguration");
        springInjectorParser.addArgument("--implant-component")
                .choices("SpringImplantController")
                .setDefault("SpringImplantController");

        Namespace namespace;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }

        Path targetPath;
        Path outputPath;
        try {
            targetPath = Path.of(namespace.getString("target"));
            outputPath = Path.of(namespace.getString("output"));
            if (Files.exists(outputPath) && targetPath.toRealPath().equals(outputPath.toRealPath())) {
                System.out.println("[!] Target JAR and output JAR cannot be the same.");
                System.exit(1);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Command command = namespace.get("command");
        switch (command) {
            case CLASS_INJECTOR -> {
                String implantClassName = namespace.getString("implant_class");
                if (implantClassName.equals("ClassImplant")) {
                    runClassInjector(targetPath, outputPath, ClassImplant.class);
                } else {
                    System.out.println("[!] Unknown --implant-class.");
                    System.exit(1);
                }
            }
            case SPRING_INJECTOR -> {
                String implantComponent = namespace.getString("implant_component");
                String implantConfClass = namespace.getString("implant_config");
                if (implantComponent.equals("SpringImplantController") && implantConfClass.equals("SpringImplantConfiguration")) {
                    runSpringInjector(targetPath, outputPath, SpringImplantController.class, SpringImplantConfiguration.class);
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

    public static void runClassInjector(Path targetPath, Path outputPath, Class<?> implantClass) {
        ClassInjector injector = new ClassInjector(implantClass);
        System.out.println("[i] Implant class: " + implantClass);
        System.out.println("[i] Target JAR: " + targetPath);
        System.out.println("[i] Output JAR: " + outputPath);
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

    public static void runSpringInjector(Path targetPath, Path outputPath, Class<?> implantComponent, Class<?> implantConfClass) {
        SpringInjector injector = new SpringInjector(implantComponent, implantConfClass);
        System.out.println("[i] Implant Spring component: " + implantComponent);
        System.out.println("[i] Implant Spring config class: " + implantConfClass);
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
}
