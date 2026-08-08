package com.storyplatform.identity.domain;

import java.net.IDN;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailNormalizer {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_LOCAL_LENGTH = 64;
    private static final Pattern LOCAL_PART = Pattern.compile(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+"
    );
    private static final Pattern DOMAIN = Pattern.compile(
            "(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                    + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
    );

    public String normalize(String value) {
        if (value == null) {
            throw new InvalidEmailException();
        }

        String normalized = Normalizer.normalize(
                value.strip(),
                Normalizer.Form.NFC
        ).toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('@');
        if (separator <= 0 || separator != normalized.indexOf('@')) {
            throw new InvalidEmailException();
        }

        String localPart = normalized.substring(0, separator);
        String domainPart = asciiDomain(
                normalized.substring(separator + 1)
        );
        String result = localPart + "@" + domainPart;
        if (localPart.length() > MAX_LOCAL_LENGTH
                || result.length() > MAX_EMAIL_LENGTH
                || !LOCAL_PART.matcher(localPart).matches()
                || !DOMAIN.matcher(domainPart).matches()
                || localPart.startsWith(".")
                || localPart.endsWith(".")
                || localPart.contains("..")) {
            throw new InvalidEmailException();
        }
        return result;
    }

    private static String asciiDomain(String domain) {
        try {
            return IDN.toASCII(
                    domain,
                    IDN.USE_STD3_ASCII_RULES
            ).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new InvalidEmailException();
        }
    }

    public static final class InvalidEmailException
            extends IllegalArgumentException {
    }
}
