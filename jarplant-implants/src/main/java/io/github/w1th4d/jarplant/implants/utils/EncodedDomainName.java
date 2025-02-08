package io.github.w1th4d.jarplant.implants.utils;

import java.util.*;

public class EncodedDomainName implements Comparator<EncodedDomainName> {
    private final String topDomain;
    private final String exfilId;
    private final int seqNo;
    private final List<String> subdomains;

    EncodedDomainName(String topDomain, String exfilId, int seqNo, Collection<String> subdomains) {
        this.topDomain = topDomain;
        this.exfilId = exfilId;
        this.seqNo = seqNo;
        this.subdomains = Collections.unmodifiableList(new LinkedList<>(subdomains));
    }

    public static EncodedDomainName parse(String topDomain, String domainName) throws DecoderException {
        domainName = domainName.toLowerCase();
        topDomain = topDomain.toLowerCase();

        int indexOfTopDomainParts = domainName.indexOf(topDomain);
        if (indexOfTopDomainParts < 0) {
            throw new DecoderException("Domain name does not belong to top domain '" + topDomain + "'.");
        }
        String excludingTopDomain = domainName.substring(0, indexOfTopDomainParts);

        String[] subdomains = excludingTopDomain.split("\\.");
        String metadataPart = subdomains[subdomains.length - 1];
        subdomains = Arrays.copyOfRange(subdomains, 0, subdomains.length - 1);
        String[] metadataSplit = metadataPart.split("-");
        if (metadataSplit.length != 2) {
            throw new DecoderException("Invalid metadata field '" + metadataPart + "' in domain name '" + domainName + "'.");
        }
        String uniqueId = metadataSplit[0];
        int seqNo;
        try {
            seqNo = Integer.parseInt(metadataSplit[1]);
        } catch (NumberFormatException e) {
            throw new DecoderException("Failed to parse sequence number for domain name '" + domainName + "'.");
        }

        return new EncodedDomainName(topDomain, uniqueId, seqNo, Arrays.asList(subdomains));
    }

    public static Collection<EncodedDomainName> parseMany(String topDomain, Iterable<String> domainNames) {
        List<EncodedDomainName> results = new ArrayList<>();
        for (String domainName : domainNames) {
            try {
                results.add(parse(topDomain, domainName));
            } catch (DecoderException ignored) {
            }
        }
        return results;
    }

    public static Collection<EncodedDomainName> parseMany(String topDomain, String... domainNames) {
        List<EncodedDomainName> results = new ArrayList<>();
        for (String domainName : domainNames) {
            try {
                results.add(parse(topDomain, domainName));
            } catch (DecoderException ignored) {
            }
        }
        return results;
    }

    public static boolean isSameQuery(EncodedDomainName first, EncodedDomainName second) {
        boolean sameTopDomain = Objects.equals(first.topDomain, second.topDomain);
        boolean sameExfilId = Objects.equals(first.exfilId, second.exfilId);
        boolean sameSeqNo = Objects.equals(first.seqNo, second.seqNo);
        return sameTopDomain && sameExfilId && sameSeqNo;
    }

    public static boolean subdomainsMatches(EncodedDomainName first, EncodedDomainName second) {
        Iterator<String> firstIter = first.subdomains.iterator();
        Iterator<String> secondIter = second.subdomains.iterator();

        while (firstIter.hasNext() && secondIter.hasNext()) {
            String subdomainOfFirst = firstIter.next();
            String subdomainOfSecond = secondIter.next();

            if (!subdomainOfFirst.equals(subdomainOfSecond)) {
                return false;
            }
        }

        return true;
    }

    public static Optional<EncodedDomainName> max(EncodedDomainName first, EncodedDomainName second) {
        if (!isSameQuery(first, second)) {
            return Optional.empty();
        }

        if (!subdomainsMatches(first, second)) {
            return Optional.empty();
        }

        int firstSize = first.subdomains.size();
        int secondSize = second.subdomains.size();
        if (firstSize > secondSize) {
            return Optional.of(first);
        } else if (firstSize < secondSize) {
            return Optional.of(second);
        } else {
            // They're the same
            return Optional.of(first);
        }
    }

    public String getTopDomain() {
        return topDomain;
    }

    public String getExfilId() {
        return exfilId;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public String getEncodedData() {
        StringBuilder ret = new StringBuilder();
        subdomains.forEach(ret::append);
        return ret.toString();
    }

    @Override
    public int compare(EncodedDomainName first, EncodedDomainName second) {
        return second.subdomains.size() - first.subdomains.size();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (String encodedSubdomain : subdomains) {
            str.append(encodedSubdomain).append(".");
        }
        str.append(exfilId).append("-").append(seqNo).append(".").append(topDomain);
        return str.toString();
    }
}
