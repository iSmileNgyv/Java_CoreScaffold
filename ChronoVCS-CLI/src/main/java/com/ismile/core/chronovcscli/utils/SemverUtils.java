package com.ismile.core.chronovcscli.utils;

public final class SemverUtils {
    private SemverUtils() {
    }

    public static int compare(String a, String b) {
        int[] av = parse(a);
        int[] bv = parse(b);

        int max = Math.max(av.length, bv.length);
        for (int i = 0; i < max; i++) {
            int ai = i < av.length ? av[i] : 0;
            int bi = i < bv.length ? bv[i] : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }

        return 0;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return new int[] {0, 0, 0};
        }

        String base = version.trim();
        int dash = base.indexOf('-');
        if (dash >= 0) {
            base = base.substring(0, dash);
        }
        int plus = base.indexOf('+');
        if (plus >= 0) {
            base = base.substring(0, plus);
        }

        String[] parts = base.split("\\.");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].replaceAll("[^0-9]", "");
            if (part.isEmpty()) {
                values[i] = 0;
            } else {
                try {
                    values[i] = Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    values[i] = 0;
                }
            }
        }
        return values;
    }
}
