package br.com.dms.audit.service;

import java.time.Instant;
import java.util.Map;

public record AuditAlert(
    String code,
    String severity,
    String title,
    String description,
    Instant detectedAt,
    Map<String, Object> context
) {
}
