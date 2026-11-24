package com.civicsense.routing;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Production spatial matcher. Every query can use the GiST indexes created in
 * V2/V6 because the predicate compares the point against the stored geometry
 * column directly; no SQL-side conversion of the column is performed.
 */
@Component
@Profile("!test")
public class PostgisSpatialMatcher implements SpatialMatcher {

    private final JdbcTemplate jdbc;

    public PostgisSpatialMatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> matchingServiceAreaIds(double longitude, double latitude) {
        return jdbc.queryForList("""
                SELECT id FROM service_areas
                WHERE is_active
                  AND boundary IS NOT NULL
                  AND ST_IsValid(boundary)
                  AND ST_Intersects(ST_SetSRID(ST_MakePoint(?, ?), 4326), boundary)
                """, UUID.class, longitude, latitude);
    }

    @Override
    public List<UUID> matchingJurisdictionIds(double longitude, double latitude) {
        return jdbc.queryForList("""
                SELECT id FROM jurisdictions
                WHERE is_active AND boundary IS NOT NULL
                  AND ST_IsValid(boundary)
                  AND ST_Intersects(ST_SetSRID(ST_MakePoint(?, ?), 4326), boundary)
                """, UUID.class, longitude, latitude);
    }
}
