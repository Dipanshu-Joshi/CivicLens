package com.civicsense.routing;

import java.util.UUID;

/** Published only after the complaint/AI transaction commits. */
public record RoutingRequestedEvent(UUID complaintId) {
}
