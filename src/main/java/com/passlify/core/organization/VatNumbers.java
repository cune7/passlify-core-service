package com.passlify.core.organization;

import java.util.Locale;

/**
 * Company identifier validation, Serbia-first.
 *
 * <p>Currently syntactic (format) validation:
 * <ul>
 *   <li><b>PIB</b> (tax id): 9 digits. An optional {@code RS} prefix and spaces are tolerated.</li>
 *   <li><b>Matični broj (MBR)</b> (company registration number): 8 digits.</li>
 * </ul>
 *
 * <p>The Serbian PIB also carries an ISO 7064 MOD 11,10 check digit; a
 * {@code passesPibCheckDigit} step can be layered on once verified against known-good
 * PIBs (a wrong checksum would reject valid companies, so it is intentionally not
 * enforced yet).
 */
public final class VatNumbers {

    private VatNumbers() {
    }

    /** Serbia is the default when no country is given (Serbia-first). */
    public static boolean isSerbianCountry(String country) {
        return country == null || country.isBlank() || country.trim().equalsIgnoreCase("RS");
    }

    /** Normalizes a PIB: trims, uppercases, drops an optional leading {@code RS} and inner spaces. */
    public static String normalizePib(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (s.startsWith("RS")) {
            s = s.substring(2);
        }
        return s;
    }

    /** True for a syntactically valid Serbian PIB (9 digits, not all zeros). */
    public static boolean isValidSerbianPib(String raw) {
        String s = normalizePib(raw);
        return s != null && s.matches("\\d{9}") && !s.equals("000000000");
    }

    /** True for a syntactically valid Serbian matični broj (8 digits, not all zeros). */
    public static boolean isValidSerbianRegistrationNo(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.trim().replaceAll("\\s+", "");
        return s.matches("\\d{8}") && !s.equals("00000000");
    }
}
