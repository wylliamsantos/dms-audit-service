package br.com.dms.audit.controller.response;

import java.time.Instant;
import java.util.Map;

public record AuditEventView(
    String id,
    String eventType,
    Instant occurredAt,
    Instant ingestedAt,
    String userId,
    String entityType,
    String entityId,
    String filename,
    Map<String, Object> metadata,
    Map<String, Object> attributes
) {
}
