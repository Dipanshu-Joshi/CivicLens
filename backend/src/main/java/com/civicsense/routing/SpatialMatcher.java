package com.civicsense.routing;

import java.util.List;
import java.util.UUID;

/**
 * Point-in-boundary lookup for routing. Implementations are profile-specific:
 * production uses PostGIS; the H2 test double uses JTS over WKT.
 */
public interface SpatialMatcher {

    /** Returns the ids of service areas whose authoritative boundary contains the point. */
    List<UUID> matchingServiceAreaIds(double longitude, double latitude);

    /** Returns the ids of jurisdictions whose authoritative boundary contains the point. */
    List<UUID> matchingJurisdictionIds(double longitude, double latitude);
}
