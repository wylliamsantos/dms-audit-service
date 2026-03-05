package br.com.dms.audit.controller.response;

import java.time.Instant;
import java.util.Map;

public record AuditAlertView(
    String code,
    String severity,
    String title,
    String description,
    Instant detectedAt,
    Map<String, Object> context
) {
}
