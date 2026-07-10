package com.passlify.core.organization;

import com.passlify.core.organization.dto.OrganizationResponse;
import com.passlify.core.organization.dto.UpsertOrganizationRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organizer profile management. Every authenticated user may view/set their own
 * organization ({@code /me/organization}); admins can list all ({@code /admin/organizations}).
 */
@RestController
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/api/v1/me/organization")
    @PreAuthorize("isAuthenticated()")
    public OrganizationResponse mine() {
        return OrganizationResponse.from(organizationService.getMine());
    }

    @PutMapping("/api/v1/me/organization")
    @PreAuthorize("isAuthenticated()")
    public OrganizationResponse upsertMine(@Valid @RequestBody UpsertOrganizationRequest req) {
        return OrganizationResponse.from(organizationService.upsertMine(req));
    }

    @GetMapping("/api/v1/admin/organizations")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrganizationResponse> listAll(@PageableDefault(size = 20) Pageable pageable) {
        return organizationService.listAll(pageable).map(OrganizationResponse::from);
    }
}
