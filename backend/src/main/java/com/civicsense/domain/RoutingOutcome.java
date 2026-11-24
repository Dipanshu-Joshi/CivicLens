package com.civicsense.domain;

/**
 * Terminal outcome of one deterministic routing run. Values are constrained by
 * both the production CHECK constraint and the H2 test schema.
 */
public enum RoutingOutcome {
    ASSIGNED,
    UNASSIGNED,
    AMBIGUOUS,
    LOW_CONFIDENCE,
    NON_CIVIC,
    NO_CAPACITY
}
