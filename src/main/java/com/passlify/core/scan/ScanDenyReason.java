package com.passlify.core.scan;

/** Why a scan was denied (DOMAIN §4.7). Stored on the audit row and returned to the operator. */
public enum ScanDenyReason {
    ALREADY_USED,
    VOID,
    WRONG_EVENT,
    BAD_SIGNATURE,
    NOT_FOUND
}
