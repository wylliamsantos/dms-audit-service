package br.com.dms.audit.service;

import br.com.dms.audit.model.AuditEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditEventIndexer auditEventIndexer;

    public AuditEventListener(AuditEventIndexer auditEventIndexer) {
        this.auditEventIndexer = auditEventIndexer;
    }

    @RabbitListener(queues = "${dms.audit.queue}")
    public void receive(@Payload AuditEventMessage message) {
        if (message == null) {
            logger.warn("Received null audit event message");
            return;
        }
        logger.debug("Received audit event {} for entity {}", message.eventType(), message.entityId());
        auditEventIndexer.index(message);
    }
}
