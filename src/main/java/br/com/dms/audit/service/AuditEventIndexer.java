package br.com.dms.audit.service;

import br.com.dms.audit.model.AuditEventDocument;
import br.com.dms.audit.model.AuditEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class AuditEventIndexer {

    private static final Logger logger = LoggerFactory.getLogger(AuditEventIndexer.class);
    private static final DateTimeFormatter INDEX_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final ElasticsearchOperations elasticsearchOperations;
    private final String indexPrefix;

    public AuditEventIndexer(ElasticsearchOperations elasticsearchOperations,
                             @Value("${dms.audit.index-prefix:audit-events}") String indexPrefix) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.indexPrefix = indexPrefix;
    }

    public void index(AuditEventMessage message) {
        Instant occurredAt = message.occurredAt() != null ? message.occurredAt() : Instant.now();
        String indexName = String.format("%s-%s", indexPrefix, INDEX_SUFFIX_FORMATTER.format(occurredAt));

        ensureIndexExists(indexName);

        AuditEventDocument document = AuditEventDocument.fromMessage(message, Instant.now());

        try {
            elasticsearchOperations.save(document, IndexCoordinates.of(indexName));
        } catch (Exception exception) {
            logger.error("Failed to index audit event {} in index {}", message.eventType(), indexName, exception);
        }
    }

    private void ensureIndexExists(String indexName) {
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
            if (!indexOperations.exists()) {
                indexOperations.create();
                indexOperations.putMapping(indexOperations.createMapping(AuditEventDocument.class));
            }
        } catch (Exception exception) {
            logger.warn("Failed to create or verify index {}", indexName, exception);
        }
    }
}
