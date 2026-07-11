package com.passlify.core.event.dto;

import com.passlify.core.event.EventType;
import java.util.UUID;

/**
 * Lightweight event-type view for event responses: the selected leaf plus its
 * parent category (name/code) for display.
 */
public record EventTypeDto(UUID id, String code, String name, String parentCode, String parentName) {

    public static EventTypeDto from(EventType et) {
        if (et == null) {
            return null;
        }
        EventType parent = et.getParent();
        return new EventTypeDto(
                et.getId(), et.getCode(), et.getName(),
                parent == null ? null : parent.getCode(),
                parent == null ? null : parent.getName());
    }
}
