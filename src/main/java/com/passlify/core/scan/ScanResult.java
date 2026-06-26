package com.passlify.core.scan;

/** Outcome of a scan attempt. A denial is still an HTTP 200 with a verdict. */
public enum ScanResult {
    ALLOWED,
    DENIED
}
