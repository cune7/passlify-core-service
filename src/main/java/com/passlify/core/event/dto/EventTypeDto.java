package com.passlify.core.event.dto;

import com.passlify.core.event.EventType;
import java.util.UUID;

/** Lightweight event-type view for responses. */
public record EventTypeDto(UUID id, String category, String type) {

    public static EventTypeDto from(EventType et) {
        if (et == null) {
            return null;
        }
        return new EventTypeDto(et.getId(), et.getCategory(), et.getType());
    }
}
