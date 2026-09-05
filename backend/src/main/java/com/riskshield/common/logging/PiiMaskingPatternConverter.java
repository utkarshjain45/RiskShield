package com.riskshield.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback converter that automatically redacts and masks sensitive PII and secret values
 * before log messages are written to stdout or files.
 */
public class PiiMaskingPatternConverter extends CompositeConverter<ILoggingEvent> {

    // Credit / Debit Card PANs (13 to 19 digits)
    private static final Pattern CARD_PATTERN = Pattern.compile("\\b(\\d{4})[ -]?(\\d{4})[ -]?(\\d{4})[ -]?(\\d{1,4})\\b");

    // Email addresses
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b([a-zA-Z0-9_.+-]{1,2})[a-zA-Z0-9_.+-]*(@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)\\b");

    // Phone / Contact numbers (10 to 14 digits)
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d{2,3})\\d{4,6}(\\d{3,4})");

    // Sensitive Auth Tokens & Secrets
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(Bearer\\s+[a-zA-Z0-9_\\-\\.]+|(?<=key_secret[\"=:\b])[^\",\r\n\\s]+|(?<=webhook_secret[\"=:\b])[^\",\r\n\\s]+|(?<=password[\"=:\b])[^\",\r\n\\s]+)"
    );

    @Override
    protected String transform(ILoggingEvent event, String in) {
        if (in == null || in.isBlank()) {
            return in;
        }

        String masked = in;

        // 1. Redact Secrets & API Keys
        masked = SECRET_PATTERN.matcher(masked).replaceAll("[REDACTED_SECRET]");

        // 2. Mask Credit Card PANs: 4111-****-****-1234
        Matcher cardMatcher = CARD_PATTERN.matcher(masked);
        if (cardMatcher.find()) {
            masked = cardMatcher.replaceAll("$1-****-****-$4");
        }

        // 3. Mask Email Addresses: j***@domain.com
        Matcher emailMatcher = EMAIL_PATTERN.matcher(masked);
        if (emailMatcher.find()) {
            masked = emailMatcher.replaceAll("$1***$2");
        }

        // 4. Mask Phone Numbers: +91******3210
        Matcher phoneMatcher = PHONE_PATTERN.matcher(masked);
        if (phoneMatcher.find()) {
            masked = phoneMatcher.replaceAll("$1******$2");
        }

        return masked;
    }
}
