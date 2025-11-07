package br.com.dms.audit.service;

import br.com.dms.audit.model.AuditEventDocument;

public record AuditEventSearchHit(String id, AuditEventDocument document) {
}
