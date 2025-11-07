package br.com.dms.audit.controller.response;

import java.util.List;

public record AuditEventSearchResponse(
    List<AuditEventView> events,
    long totalElements,
    int totalPages,
    int page,
    int size
) {
}
