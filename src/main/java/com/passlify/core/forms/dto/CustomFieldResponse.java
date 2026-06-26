package com.passlify.core.forms.dto;

import com.passlify.core.forms.CustomField;
import com.passlify.core.forms.FieldScope;
import com.passlify.core.forms.FieldType;
import java.util.List;
import java.util.UUID;

/** Custom field definition view (options already parsed from JSON). */
public record CustomFieldResponse(
        UUID id,
        UUID eventId,
        String fieldKey,
        String label,
        FieldType type,
        FieldScope scope,
        boolean required,
        List<String> options,
        int sortOrder) {

    public static CustomFieldResponse from(CustomField f, List<String> options) {
        return new CustomFieldResponse(
                f.getId(), f.getEventId(), f.getFieldKey(), f.getLabel(),
                f.getType(), f.getScope(), f.isRequired(), options, f.getSortOrder());
    }
}
