package com.passlify.core.forms.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Partial update of a custom field. {@code fieldKey}, {@code type} and
 * {@code scope} are immutable (changing them would orphan stored values).
 */
public record UpdateCustomFieldRequest(
        @Size(max = 200) String label,
        Boolean required,
        List<String> options,
        Integer sortOrder) {
}
