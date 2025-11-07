package br.com.dms.audit.controller.request;

import java.time.Instant;
import java.util.Optional;

public record AuditEventSearchParams(
    Optional<String> userId,
    Optional<String> entityId,
    Optional<String> entityType,
    Optional<String> eventType,
    Optional<Instant> occurredAtFrom,
    Optional<Instant> occurredAtTo
) {
    public static AuditEventSearchParams of(String userId,
                                            String entityId,
                                            String entityType,
                                            String eventType,
                                            Instant occurredAtFrom,
                                            Instant occurredAtTo) {
        return new AuditEventSearchParams(
            Optional.ofNullable(trimToNull(userId)),
            Optional.ofNullable(trimToNull(entityId)),
            Optional.ofNullable(trimToNull(entityType)),
            Optional.ofNullable(trimToNull(eventType)),
            Optional.ofNullable(occurredAtFrom),
            Optional.ofNullable(occurredAtTo)
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
