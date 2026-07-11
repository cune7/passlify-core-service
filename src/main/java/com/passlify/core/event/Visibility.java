package com.passlify.core.event;

/**
 * Whether and how an event is publicly reachable (EVENT_DOMAIN_SPEC §8).
 *
 * <ul>
 *   <li>{@code PUBLIC} — listed in discovery and reachable by URL.</li>
 *   <li>{@code UNLISTED} — not listed in discovery, but reachable via its direct
 *       slug link.</li>
 *   <li>{@code PRIVATE} — not listed and not publicly reachable; the public API
 *       returns 404 to avoid leaking existence. (Full invitation-based access is
 *       a later phase.)</li>
 * </ul>
 */
public enum Visibility {
    PUBLIC,
    UNLISTED,
    PRIVATE
}
