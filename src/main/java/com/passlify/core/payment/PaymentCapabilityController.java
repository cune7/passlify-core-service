package com.passlify.core.payment;

import com.passlify.core.payment.dto.GrantCapabilityRequest;
import com.passlify.core.payment.dto.PaymentCapabilityResponse;
import com.passlify.core.payment.dto.UpdateCapabilityRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment-capability management. Admins grant/manage which providers an organization
 * may use (EVENT_DOMAIN_SPEC §10.3); organizers can see their own (§10.4).
 */
@RestController
public class PaymentCapabilityController {

    private final PaymentCapabilityService capabilities;

    public PaymentCapabilityController(PaymentCapabilityService capabilities) {
        this.capabilities = capabilities;
    }

    @PostMapping("/api/v1/admin/organizations/{organizationId}/payment-capabilities")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentCapabilityResponse grant(@PathVariable UUID organizationId,
                                           @Valid @RequestBody GrantCapabilityRequest req) {
        return capabilities.grant(organizationId, req);
    }

    @GetMapping("/api/v1/admin/organizations/{organizationId}/payment-capabilities")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentCapabilityResponse> listForOrganization(@PathVariable UUID organizationId) {
        return capabilities.listForOrganization(organizationId);
    }

    @PatchMapping("/api/v1/admin/payment-capabilities/{capabilityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentCapabilityResponse updateStatus(@PathVariable UUID capabilityId,
                                                  @Valid @RequestBody UpdateCapabilityRequest req) {
        return capabilities.updateStatus(capabilityId, req);
    }

    @GetMapping("/api/v1/me/payment-capabilities")
    @PreAuthorize("isAuthenticated()")
    public List<PaymentCapabilityResponse> mine() {
        return capabilities.listMine();
    }
}
