package com.company.payroll.trailers;

/**
 * Enumeration of possible trailer statuses. Mirrors the statuses
 * used for trucks so filtering and data entry remain consistent.
 */
public enum TrailerStatus {
    /** Trailer is active and available for use. */
    ACTIVE,
    /** Trailer is available but currently not assigned. */
    AVAILABLE,
    /** Trailer is undergoing maintenance. */
    MAINTENANCE,
    /** Trailer is out of service. */
    OUT_OF_SERVICE
}
