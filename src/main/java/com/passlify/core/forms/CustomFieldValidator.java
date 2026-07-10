package com.passlify.core.forms;

import com.passlify.core.common.error.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Custom-field precondition checks. Injected into {@link CustomFieldService}.
 */
@Component
public class CustomFieldValidator {

    private final CustomFieldRepository fields;

    public CustomFieldValidator(CustomFieldRepository fields) {
        this.fields = fields;
    }

    /** Field keys are unique within an event. */
    public void assertKeyAvailable(UUID eventId, String key) {
        if (fields.existsByEventIdAndFieldKey(eventId, key)) {
            throw ApiException.conflict("A field with key '" + key + "' already exists for this event");
        }
    }
}
