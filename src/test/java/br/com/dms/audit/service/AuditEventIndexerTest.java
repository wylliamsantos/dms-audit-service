package br.com.dms.audit.service;

import br.com.dms.audit.model.AuditEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditEventIndexerTest {

    @Test
    void shouldSkipDuplicateWhenIdempotencyKeyAlreadyIndexed() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(operations.exists(eq("idem-123"), any(IndexCoordinates.class))).thenReturn(true);

        AuditEventIndexer indexer = new AuditEventIndexer(operations, "audit-events");

        AuditEventMessage message = new AuditEventMessage(
            "watcher.file.discovered",
            Instant.parse("2026-02-20T00:00:00Z"),
            "system",
            "tenant-dev",
            "document",
            "doc-1",
            "test.pdf",
            Map.of("idempotencyKey", "idem-123"),
            Map.of()
        );

        boolean indexed = indexer.index(message);

        assertThat(indexed).isFalse();
        verify(operations, never()).save(any(), any(IndexCoordinates.class));
    }

    @Test
    void shouldIndexWhenIdempotencyKeyDoesNotExist() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(operations.exists(eq("idem-456"), any(IndexCoordinates.class))).thenReturn(false);

        AuditEventIndexer indexer = new AuditEventIndexer(operations, "audit-events");

        AuditEventMessage message = new AuditEventMessage(
            "watcher.file.discovered",
            Instant.parse("2026-02-20T00:00:00Z"),
            "system",
            "tenant-dev",
            "document",
            "doc-2",
            "test2.pdf",
            Map.of(),
            Map.of("idempotencyKey", "idem-456")
        );

        boolean indexed = indexer.index(message);

        assertThat(indexed).isTrue();
    }
}
