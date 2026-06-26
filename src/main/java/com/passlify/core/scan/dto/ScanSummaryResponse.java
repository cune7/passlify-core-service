package com.passlify.core.scan.dto;

/** Live entry counts for an event. */
public record ScanSummaryResponse(long issued, long valid, long used, long voided) {
}
