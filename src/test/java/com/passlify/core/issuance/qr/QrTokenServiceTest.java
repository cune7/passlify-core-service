package com.passlify.core.issuance.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Pure unit test (no Spring/DB): HMAC sign/verify round-trip and tamper rejection. */
class QrTokenServiceTest {

    private final QrTokenService service =
            new QrTokenService(JsonMapper.builder().build(), "unit-test-secret-at-least-32-bytes-long!!");

    @Test
    void signThenVerifyRoundTrips() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String token = service.sign(ticketId, eventId, "SUMM-A1B2C3-001");

        QrTokenService.VerifiedToken verified = service.verify(token);

        assertThat(verified.ticketId()).isEqualTo(ticketId);
        assertThat(verified.eventId()).isEqualTo(eventId);
        assertThat(verified.serialNumber()).isEqualTo("SUMM-A1B2C3-001");
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = service.sign(UUID.randomUUID(), UUID.randomUUID(), "S-1-001");
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_SIGNATURE));
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = service.sign(UUID.randomUUID(), UUID.randomUUID(), "S-1-001");
        int dot = token.indexOf('.');
        // Flip a character in the payload half — signature no longer matches.
        char[] chars = token.toCharArray();
        chars[0] = chars[0] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        // Ensure we actually changed the payload portion.
        assertThat(tampered.substring(0, dot)).isNotEqualTo(token.substring(0, dot));

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> service.verify("not-a-valid-token"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_SIGNATURE));
    }
}
