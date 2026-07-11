package com.passlify.core.payment.dto;

import com.passlify.core.payment.CapabilityStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Admin change to a capability's status (suspend/revoke/re-activate) and/or currencies. */
public record UpdateCapabilityRequest(
        @NotNull CapabilityStatus status,
        List<String> allowedCurrencies) {
}
