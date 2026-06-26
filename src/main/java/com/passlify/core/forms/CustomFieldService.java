package com.passlify.core.forms;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.EventService;
import com.passlify.core.forms.dto.CreateCustomFieldRequest;
import com.passlify.core.forms.dto.CustomFieldResponse;
import com.passlify.core.forms.dto.UpdateCustomFieldRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Organizer management of an event's custom fields (ownership via {@link EventService}). */
@Service
public class CustomFieldService {

    private final CustomFieldRepository fields;
    private final EventService eventService;
    private final ObjectMapper objectMapper;

    public CustomFieldService(CustomFieldRepository fields, EventService eventService, ObjectMapper objectMapper) {
        this.fields = fields;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CustomFieldResponse create(UUID eventId, CreateCustomFieldRequest req) {
        eventService.getOwned(eventId);   // ownership + existence
        String key = req.fieldKey().toLowerCase(Locale.ROOT);
        if (fields.existsByEventIdAndFieldKey(eventId, key)) {
            throw ApiException.conflict("A field with key '" + key + "' already exists for this event");
        }
        CustomField field = new CustomField();
        field.setEventId(eventId);
        field.setFieldKey(key);
        field.setLabel(req.label());
        field.setType(req.type());
        field.setScope(req.scope());
        field.setRequired(Boolean.TRUE.equals(req.required()));
        field.setOptions(optionsToJson(req.options()));
        field.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        return toResponse(fields.save(field));
    }

    @Transactional(readOnly = true)
    public List<CustomFieldResponse> list(UUID eventId) {
        eventService.getOwned(eventId);
        return fields.findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Field definitions for the public checkout form (no ownership; the caller already gated the event). */
    @Transactional(readOnly = true)
    public List<CustomFieldResponse> publicList(UUID eventId) {
        return fields.findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CustomFieldResponse update(UUID id, UpdateCustomFieldRequest req) {
        CustomField field = loadOwned(id);
        if (req.label() != null) {
            field.setLabel(req.label());
        }
        if (req.required() != null) {
            field.setRequired(req.required());
        }
        if (req.options() != null) {
            field.setOptions(optionsToJson(req.options()));
        }
        if (req.sortOrder() != null) {
            field.setSortOrder(req.sortOrder());
        }
        return toResponse(field);
    }

    @Transactional
    public void delete(UUID id) {
        fields.delete(loadOwned(id));
    }

    // ---- helpers -----------------------------------------------------------

    private CustomField loadOwned(UUID id) {
        CustomField field = fields.findById(id)
                .orElseThrow(() -> ApiException.notFound("Custom field not found: " + id));
        eventService.getOwned(field.getEventId());   // ownership check
        return field;
    }

    private CustomFieldResponse toResponse(CustomField field) {
        return CustomFieldResponse.from(field, optionsFromJson(field.getOptions()));
    }

    private String optionsToJson(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(options);
    }

    private List<String> optionsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return Arrays.asList(objectMapper.readValue(json, String[].class));
    }
}
