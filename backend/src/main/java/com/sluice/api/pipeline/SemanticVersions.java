package com.sluice.api.pipeline;

import java.math.BigInteger;

/** Semantic Versioning 2.0.0 precedence, excluding build metadata from comparison. */
public final class SemanticVersions {
    private SemanticVersions() {
    }

    public static int compare(String left, String right) {
        Version a = Version.parse(left);
        Version b = Version.parse(right);
        int core = compareCore(a, b);
        return core != 0 ? core : comparePreRelease(a.preRelease(), b.preRelease());
    }

    private static int compareCore(Version left, Version right) {
        int major = left.major().compareTo(right.major());
        if (major != 0) return major;
        int minor = left.minor().compareTo(right.minor());
        return minor != 0 ? minor : left.patch().compareTo(right.patch());
    }

    private static int comparePreRelease(String[] left, String[] right) {
        if (left.length == 0 || right.length == 0) {
            return Integer.compare(right.length, left.length);
        }
        for (int index = 0; index < Math.min(left.length, right.length); index++) {
            String a = left[index];
            String b = right[index];
            boolean aNumeric = a.chars().allMatch(Character::isDigit);
            boolean bNumeric = b.chars().allMatch(Character::isDigit);
            int result;
            if (aNumeric && bNumeric) {
                result = new BigInteger(a).compareTo(new BigInteger(b));
            } else if (aNumeric != bNumeric) {
                result = aNumeric ? -1 : 1;
            } else {
                result = a.compareTo(b);
            }
            if (result != 0) return result;
        }
        return Integer.compare(left.length, right.length);
    }

    private record Version(BigInteger major, BigInteger minor, BigInteger patch, String[] preRelease) {
        private static Version parse(String value) {
            String withoutBuild = value.split("\\+", 2)[0];
            String[] releaseAndPreRelease = withoutBuild.split("-", 2);
            String[] core = releaseAndPreRelease[0].split("\\.");
            String[] preRelease = releaseAndPreRelease.length == 1
                    ? new String[0]
                    : releaseAndPreRelease[1].split("\\.");
            return new Version(new BigInteger(core[0]), new BigInteger(core[1]),
                    new BigInteger(core[2]), preRelease);
        }
    }
}
