package com.civicsense.ai;

import java.util.UUID;

/** Published inside the complaint transaction; analysis runs only after commit. */
public record ComplaintSubmittedEvent(UUID complaintId) {
}
