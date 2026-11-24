package com.civicsense.routing;

import java.util.List;
import java.util.UUID;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.civicsense.complaint.exception.ComplaintExceptions.InvalidBoundaryException;
import com.civicsense.domain.Jurisdiction;
import com.civicsense.domain.ServiceArea;
import com.civicsense.repository.JurisdictionRepository;
import com.civicsense.repository.ServiceAreaRepository;

/**
 * H2-only TEST DOUBLE. H2 has no PostGIS, so this uses JTS over the WKT companion
 * columns. It proves routing decisions and scoring deterministically in tests;
 * it is NOT evidence that hosted PostGIS geometry queries work.
 */
@Component
@Profile("test")
public class JtsTestSpatialMatcher implements SpatialMatcher {

    private final ServiceAreaRepository serviceAreas;
    private final JurisdictionRepository jurisdictions;

    public JtsTestSpatialMatcher(ServiceAreaRepository serviceAreas,
                                 JurisdictionRepository jurisdictions) {
        this.serviceAreas = serviceAreas;
        this.jurisdictions = jurisdictions;
    }

    @Override
    public List<UUID> matchingServiceAreaIds(double longitude, double latitude) {
        Point point = point(longitude, latitude);
        return serviceAreas.findAllByActiveTrue().stream()
                .filter(area -> contains(area, point))
                .map(ServiceArea::getId)
                .toList();
    }

    @Override
    public List<UUID> matchingJurisdictionIds(double longitude, double latitude) {
        Point point = point(longitude, latitude);
        return jurisdictions.findAllByActiveTrueOrderByCodeAsc().stream()
                .filter(jurisdiction -> contains(jurisdiction, point))
                .map(Jurisdiction::getId)
                .toList();
    }

    private boolean contains(ServiceArea area, Point point) {
        if (area.getBoundaryWkt() == null || area.getBoundaryWkt().isBlank()) {
            return false;
        }
        return intersects(area.getBoundaryWkt(), point);
    }

    private boolean contains(Jurisdiction jurisdiction, Point point) {
        return false; // H2 has no jurisdiction WKT companion column.
    }

    private static boolean intersects(String wkt, Point point) {
        try {
            Geometry geometry = new WKTReader().read(wkt);
            return geometry.isValid() && geometry.intersects(point);
        } catch (ParseException ex) {
            throw new InvalidBoundaryException("Stored boundary WKT is invalid: " + ex.getMessage());
        }
    }

    private static Point point(double longitude, double latitude) {
        var factory = new org.locationtech.jts.geom.GeometryFactory();
        return new Point(new CoordinateArraySequence(
                new org.locationtech.jts.geom.Coordinate[] {
                        new org.locationtech.jts.geom.Coordinate(longitude, latitude)
                }), factory);
    }
}
