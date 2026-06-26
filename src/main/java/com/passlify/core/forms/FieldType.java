package com.passlify.core.forms;

/**
 * Custom field input type (master spec §5.9). Values are stored as strings; type
 * drives validation/rendering. MVP subset — file upload, country/VAT, multi-select
 * and conditional fields are deferred.
 */
public enum FieldType {
    TEXT,
    TEXTAREA,
    EMAIL,
    PHONE,
    NUMBER,
    DATE,
    SELECT,
    CHECKBOX
}
