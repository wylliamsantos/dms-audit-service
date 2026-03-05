package br.com.dms.audit.service;

import br.com.dms.audit.controller.request.AuditEventSearchParams;
import br.com.dms.audit.model.AuditEventDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AuditEventQueryService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final String indexPrefix;

    public AuditEventQueryService(ElasticsearchOperations elasticsearchOperations,
                                  @Value("${dms.audit.index-prefix:audit-events}") String indexPrefix) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.indexPrefix = indexPrefix;
    }

    public Page<AuditEventSearchHit> search(AuditEventSearchParams params, Pageable pageable) {
        Query query = buildQuery(params, pageable);
        SearchHits<AuditEventDocument> hits = searchInternal(query);
        List<AuditEventSearchHit> content = hits.getSearchHits().stream()
            .filter(hit -> hit.getContent() != null)
            .map(hit -> new AuditEventSearchHit(hit.getId(), hit.getContent()))
            .toList();
        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }

    public List<AuditEventDocument> findRecentByTenant(String tenantId, Instant from, int limit) {
        Criteria criteria = new Criteria("tenantId").is(tenantId)
            .and(new Criteria("occurredAt").greaterThanEqual(from.toEpochMilli()));
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.addSort(Sort.by(Sort.Direction.DESC, "occurredAt"));
        query.setPageable(PageRequest.of(0, Math.max(1, Math.min(limit, 2000))));
        SearchHits<AuditEventDocument> hits = searchInternal(query);
        return hits.getSearchHits().stream()
            .map(org.springframework.data.elasticsearch.core.SearchHit::getContent)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private SearchHits<AuditEventDocument> searchInternal(Query query) {
        String indexPattern = indexPrefix + "-*";
        return elasticsearchOperations.search(query, AuditEventDocument.class, IndexCoordinates.of(indexPattern));
    }

    private Query buildQuery(AuditEventSearchParams params, Pageable pageable) {
        Criteria criteria = null;

        criteria = addCriteria(criteria, params.userId(), "userId");
        criteria = addCriteria(criteria, params.tenantId(), "tenantId");
        criteria = addCriteria(criteria, params.entityId(), "entityId");
        criteria = addCriteria(criteria, params.entityType(), "entityType");
        criteria = addCriteria(criteria, params.eventType(), "eventType");
        criteria = addIpCriteria(criteria, params.ip());

        if (params.occurredAtFrom().isPresent() || params.occurredAtTo().isPresent()) {
            Instant from = params.occurredAtFrom().orElse(null);
            Instant to = params.occurredAtTo().orElse(null);
            Criteria rangeCriteria = new Criteria("occurredAt");
            if (from != null) {
                rangeCriteria = rangeCriteria.greaterThanEqual(from.toEpochMilli());
            }
            if (to != null) {
                rangeCriteria = rangeCriteria.lessThanEqual(to.toEpochMilli());
            }
            criteria = and(criteria, rangeCriteria);
        }

        Query query = (criteria == null) ? Query.findAll() : new CriteriaQuery(criteria);
        query.addSort(Sort.by(Sort.Direction.DESC, "ingestedAt"));
        query.setPageable(pageable);
        return query;
    }

    private Criteria addCriteria(Criteria base, Optional<String> value, String field) {
        if (value.isPresent()) {
            return and(base, new Criteria(field).is(value.get()));
        }
        return base;
    }

    private Criteria addIpCriteria(Criteria base, Optional<String> ip) {
        if (ip.isEmpty()) {
            return base;
        }

        Criteria ipCriteria = new Criteria("attributes.ip").is(ip.get())
            .or(new Criteria("attributes.clientIp").is(ip.get()))
            .or(new Criteria("attributes.remoteIp").is(ip.get()))
            .or(new Criteria("metadata.ip").is(ip.get()))
            .or(new Criteria("metadata.clientIp").is(ip.get()))
            .or(new Criteria("metadata.remoteIp").is(ip.get()));

        return and(base, ipCriteria);
    }

    private Criteria and(Criteria base, Criteria addition) {
        if (base == null) {
            return addition;
        }
        return base.and(addition);
    }
}
