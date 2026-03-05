package br.com.dms.audit.controller.request;

import java.time.Instant;
import java.util.Optional;

public record AuditEventSearchParams(
    Optional<String> userId,
    Optional<String> tenantId,
    Optional<String> entityId,
    Optional<String> entityType,
    Optional<String> eventType,
    Optional<String> ip,
    Optional<Instant> occurredAtFrom,
    Optional<Instant> occurredAtTo
) {
    public static AuditEventSearchParams of(String userId,
                                            String tenantId,
                                            String entityId,
                                            String entityType,
                                            String eventType,
                                            String ip,
                                            Instant occurredAtFrom,
                                            Instant occurredAtTo) {
        return new AuditEventSearchParams(
            Optional.ofNullable(trimToNull(userId)),
            Optional.ofNullable(trimToNull(tenantId)),
            Optional.ofNullable(trimToNull(entityId)),
            Optional.ofNullable(trimToNull(entityType)),
            Optional.ofNullable(trimToNull(eventType)),
            Optional.ofNullable(trimToNull(ip)),
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
