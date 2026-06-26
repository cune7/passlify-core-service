package com.passlify.core.forms;

import com.passlify.core.forms.dto.CreateCustomFieldRequest;
import com.passlify.core.forms.dto.CustomFieldResponse;
import com.passlify.core.forms.dto.UpdateCustomFieldRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Organizer management of an event's custom fields. */
@RestController
@PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
public class CustomFieldController {

    private final CustomFieldService customFieldService;

    public CustomFieldController(CustomFieldService customFieldService) {
        this.customFieldService = customFieldService;
    }

    @PostMapping("/api/v1/events/{eventId}/custom-fields")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomFieldResponse create(@PathVariable UUID eventId,
                                      @Valid @RequestBody CreateCustomFieldRequest req) {
        return customFieldService.create(eventId, req);
    }

    @GetMapping("/api/v1/events/{eventId}/custom-fields")
    public List<CustomFieldResponse> list(@PathVariable UUID eventId) {
        return customFieldService.list(eventId);
    }

    @PatchMapping("/api/v1/custom-fields/{id}")
    public CustomFieldResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateCustomFieldRequest req) {
        return customFieldService.update(id, req);
    }

    @DeleteMapping("/api/v1/custom-fields/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customFieldService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
