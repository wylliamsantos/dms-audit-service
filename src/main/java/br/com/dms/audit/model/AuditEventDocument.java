package br.com.dms.audit.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventDocument(
    String eventType,
    Long occurredAt,
    Long ingestedAt,
    String userId,
    String entityType,
    String entityId,
    String filename,
    Map<String, Object> metadata,
    Map<String, Object> attributes
) {
    public static AuditEventDocument fromMessage(AuditEventMessage message, Instant ingestedAt) {
        return new AuditEventDocument(
            message.eventType(),
            message.occurredAt() != null ? message.occurredAt().toEpochMilli() : null,
            ingestedAt != null ? ingestedAt.toEpochMilli() : null,
            message.userId(),
            message.entityType(),
            message.entityId(),
            message.filename(),
            message.metadata(),
            message.attributes()
        );
    }
}
