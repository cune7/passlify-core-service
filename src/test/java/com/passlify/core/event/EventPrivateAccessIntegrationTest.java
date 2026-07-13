package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.CreateAccessGrantRequest;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.order.CheckoutService;
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeService;
import com.passlify.core.ticket.dto.CreateTicketTypeRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class EventPrivateAccessIntegrationTest extends AbstractIntegrationTest {

    @Autowired EventService eventService;
    @Autowired TicketTypeService ticketTypeService;
    @Autowired EventAccessService accessService;
    @Autowired PublicCatalogService catalog;
    @Autowired CheckoutService checkoutService;
    @Autowired EventContactService contactService;
    @Autowired MockMvc mvc;

    private UUID eventId;
    private UUID ticketTypeId;
    private String slug;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void privateDetailNeedsAValidTokenAndRevokeRemovesAccess() throws Exception {
        setUpPrivatePublishedEvent();
        var grant = accessService.create(eventId, new CreateAccessGrantRequest("VIP list"));
        String token = grant.token();

        assertThat(catalog.findAccessibleBySlug(slug, null)).isEmpty();
        assertThat(catalog.findAccessibleBySlug(slug, token)).isPresent();

        SecurityContextHolder.clearContext(); // public visitor
        mvc.perform(get("/api/v1/public/events/{slug}", slug))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
        mvc.perform(get("/api/v1/public/events/{slug}", slug).param("access", token))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

        authenticate("organizer-1");
        accessService.revoke(eventId, grant.id());
        assertThat(catalog.findAccessibleBySlug(slug, token)).isEmpty();
    }

    @Test
    void privateCheckoutRequiresTheToken() {
        setUpPrivatePublishedEvent();
        String token = accessService.create(eventId, new CreateAccessGrantRequest(null)).token();

        CreateOrderRequest order = new CreateOrderRequest(
                new CreateOrderRequest.Buyer("buyer@example.com", "Buyer", null),
                List.of(new CreateOrderRequest.Line(ticketTypeId, 1, null)), null);

        assertThatThrownBy(() -> checkoutService.createOrder(order, null)).isInstanceOf(ApiException.class);
        assertThat(checkoutService.createOrder(order, token).getId()).isNotNull();
    }

    private void setUpPrivatePublishedEvent() {
        authenticate("organizer-1");
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        Event event = eventService.create(new CreateEventRequest(
                "Private Fest", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null,
                com.passlify.core.support.EventFixtures.TEST_LOCATION, 500, List.of(),
                "RSD", Visibility.PRIVATE, null));
        eventId = event.getId();
        slug = event.getSlug();
        TicketType tt = ticketTypeService.create(eventId, new CreateTicketTypeRequest(
                "Free", null, 0L, null, 100, null, null, null, null, null, null, null, null));
        ticketTypeId = tt.getId();
        com.passlify.core.support.EventFixtures.addContact(contactService, eventId);
        eventService.publish(eventId);
    }

    private void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
    }
}
