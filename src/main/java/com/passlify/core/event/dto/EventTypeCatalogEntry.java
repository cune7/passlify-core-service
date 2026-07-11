package com.passlify.core.event.dto;

import com.passlify.core.event.EventType;
import java.util.UUID;

/**
 * One entry of the public event-type catalog: enough for a UI to render the
 * category → leaf picker. Categories have {@code selectable=false}.
 */
public record EventTypeCatalogEntry(
        UUID id,
        String code,
        String name,
        UUID parentId,
        String parentCode,
        boolean selectable,
        int sortOrder) {

    public static EventTypeCatalogEntry from(EventType et) {
        EventType parent = et.getParent();
        return new EventTypeCatalogEntry(
                et.getId(), et.getCode(), et.getName(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getCode(),
                et.isSelectable(), et.getSortOrder());
    }
}
