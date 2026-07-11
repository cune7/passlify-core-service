package com.passlify.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.event.dto.EventAuditResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes immutable {@link EventAuditEntry} rows in the same transaction as the change
 * being audited, and reads an event's history (EVENT_DOMAIN_SPEC §28). Request context
 * (id / IP / user-agent) is captured best-effort when a servlet request is bound.
 */
@Service
public class EventAuditService {

    /** Local mapper: the audit diff is only simple maps of strings, so no app config is needed. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EventAuditEntryRepository entries;
    private final CurrentUser currentUser;

    public EventAuditService(EventAuditEntryRepository entries, CurrentUser currentUser) {
        this.entries = entries;
        this.currentUser = currentUser;
    }

    /** Records an audited change. {@code changedFields} may be null/empty for actions with no diff. */
    public void record(Event event, EventAuditAction action, Map<String, ?> changedFields, String reason) {
        EventAuditEntry entry = new EventAuditEntry();
        entry.setEventId(event.getId());
        entry.setEventPublicId(event.getPublicId());
        entry.setActorUserId(currentUser.requireSubject());
        entry.setAction(action);
        entry.setChangedFields(toJson(changedFields));
        entry.setReason(reason);
        entry.setOccurredAt(Instant.now());
        applyRequestContext(entry);
        entries.save(entry);
    }

    public Page<EventAuditResponse> list(UUID eventId, Pageable pageable) {
        return entries.findByEventIdOrderByOccurredAtDesc(eventId, pageable)
                .map(EventAuditResponse::from);
    }

    private String toJson(Map<String, ?> changedFields) {
        if (changedFields == null || changedFields.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(changedFields);
        } catch (JsonProcessingException ex) {
            throw ApiException.of(ErrorCode.INTERNAL_ERROR, "Could not serialize audit diff");
        }
    }

    private void applyRequestContext(EventAuditEntry entry) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            entry.setRequestId(req.getHeader("X-Request-Id"));
            entry.setIpAddress(req.getRemoteAddr());
            entry.setUserAgent(req.getHeader("User-Agent"));
        }
    }
}
