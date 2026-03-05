package br.com.dms.audit.controller.response;

import java.util.List;

public record AuditAlertListResponse(
    List<AuditAlertView> alerts,
    long total
) {
}
