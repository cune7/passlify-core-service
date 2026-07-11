package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationTokenServiceTest {

    private final InvitationTokenService service =
            new InvitationTokenService("dev-only-change-me-min-32-bytes-invite!!");

    @Test
    void signedTokenRoundTrips() {
        UUID id = UUID.randomUUID();
        Instant expires = Instant.ofEpochMilli(1_800_000_000_000L);
        InvitationTokenService.VerifiedInvite v = service.verify(service.issue(id, expires));
        assertThat(v.collaboratorId()).isEqualTo(id);
        assertThat(v.expiresAt()).isEqualTo(expires);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.issue(UUID.randomUUID(), Instant.ofEpochMilli(1_800_000_000_000L));
        assertThatThrownBy(() -> service.verify(token + "AA")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.verify("not-a-token")).isInstanceOf(ApiException.class);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        String token = new InvitationTokenService("a-different-secret-value-32-bytes-xx")
                .issue(UUID.randomUUID(), Instant.ofEpochMilli(1_800_000_000_000L));
        assertThatThrownBy(() -> service.verify(token)).isInstanceOf(ApiException.class);
    }
}
