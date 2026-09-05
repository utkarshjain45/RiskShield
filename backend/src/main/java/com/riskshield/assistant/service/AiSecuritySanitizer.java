package com.riskshield.assistant.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Neutralizes potential prompt injection and jailbreak payloads originating from untrusted
 * customer-supplied text, transaction notes, or database records.
 * Delimits untrusted content into strict sandboxed XML data tags (<untrusted_record_data>).
 */
@Component
public class AiSecuritySanitizer {

    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?previous\\s+instructions|system\\s*:|system\\s+prompt|you\\s+are\\s+now|override\\s+instructions|disregard\\s+all|\\badmin\\s+override\\b)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Sanitizes untrusted text and encloses it safely inside <untrusted_record_data> tags.
     */
    public String sanitizeAndWrap(String rawContent, String contextLabel) {
        if (rawContent == null || rawContent.isBlank()) {
            return "<untrusted_record_data context=\"" + sanitizeAttribute(contextLabel) + "\">None</untrusted_record_data>";
        }

        // Neutralize common injection tokens
        String sanitized = DANGEROUS_PATTERNS.matcher(rawContent).replaceAll("[REDACTED_INJECTION_TOKEN]");

        // Escape XML delimiter tags if maliciously present in user text
        sanitized = sanitized.replace("<untrusted_record_data>", "&lt;untrusted_record_data&gt;")
                             .replace("</untrusted_record_data>", "&lt;/untrusted_record_data&gt;");

        return String.format(
                "<untrusted_record_data context=\"%s\">\n%s\n</untrusted_record_data>",
                sanitizeAttribute(contextLabel),
                sanitized.trim()
        );
    }

    private String sanitizeAttribute(String attr) {
        return attr != null ? attr.replaceAll("[^a-zA-Z0-9_-]", "_") : "untrusted";
    }
}
