package br.com.dms.audit.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventDocument(
    @Id String id,
    String eventType,
    Long occurredAt,
    Long ingestedAt,
    String userId,
    String tenantId,
    String entityType,
    String entityId,
    String filename,
    Map<String, Object> metadata,
    Map<String, Object> attributes
) {
    public static AuditEventDocument fromMessage(AuditEventMessage message, Instant ingestedAt) {
        return new AuditEventDocument(
            idempotencyKey(message),
            message.eventType(),
            message.occurredAt() != null ? message.occurredAt().toEpochMilli() : null,
            ingestedAt != null ? ingestedAt.toEpochMilli() : null,
            message.userId(),
            message.tenantId(),
            message.entityType(),
            message.entityId(),
            message.filename(),
            message.metadata(),
            message.attributes()
        );
    }

    private static String idempotencyKey(AuditEventMessage message) {
        Object metadataKey = message.metadata() != null ? message.metadata().get("idempotencyKey") : null;
        if (metadataKey instanceof String key && !key.isBlank()) {
            return key;
        }

        Object attributesKey = message.attributes() != null ? message.attributes().get("idempotencyKey") : null;
        if (attributesKey instanceof String key && !key.isBlank()) {
            return key;
        }

        return null;
    }
}
