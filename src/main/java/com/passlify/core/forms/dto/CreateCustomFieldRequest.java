package com.passlify.core.forms.dto;

import com.passlify.core.forms.FieldScope;
import com.passlify.core.forms.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Define a custom field on an event. {@code fieldKey} is the stable identifier
 * used in submitted values (lowercase letters, digits, underscores).
 * {@code options} are the choices for a SELECT field.
 */
public record CreateCustomFieldRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "[a-z0-9_]+", message = "must be lowercase letters, digits or underscores") String fieldKey,
        @NotBlank @Size(max = 200) String label,
        @NotNull FieldType type,
        @NotNull FieldScope scope,
        Boolean required,
        List<String> options,
        Integer sortOrder) {
}
