package com.civicsense.reference;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Component;

import com.civicsense.complaint.exception.ComplaintExceptions.InvalidBoundaryException;

/**
 * Structural WKT check used before persistence. This is NOT a substitute for
 * PostGIS ST_IsValid: the production migration adds a trigger that converts
 * boundary_wkt to a geometry(4326) column, and real spatial validation remains
 * an external verification item until Supabase/PostGIS credentials exist.
 */
@Component
public class GeometryValidator {

    public void validateMultiPolygon(String wkt) {
        if (wkt == null || wkt.isBlank()) {
            throw new InvalidBoundaryException("boundaryWkt is required");
        }
        String value = wkt.trim().toUpperCase();
        if (!value.startsWith("MULTIPOLYGON") && !value.startsWith("POLYGON")) {
            throw new InvalidBoundaryException("boundaryWkt must be a POLYGON or MULTIPOLYGON");
        }
        if (!value.contains("((") || !value.endsWith(")")) {
            throw new InvalidBoundaryException("boundaryWkt is not a valid WKT polygon");
        }
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth < 0) {
                    throw new InvalidBoundaryException("Unbalanced parentheses in boundaryWkt");
                }
            }
        }
        if (depth != 0) {
            throw new InvalidBoundaryException("Unbalanced parentheses in boundaryWkt");
        }
        validateCoordinates(value);

        try {
            Geometry geometry = new WKTReader().read(value);
            if (!(geometry instanceof Polygon || geometry instanceof MultiPolygon)) {
                throw new InvalidBoundaryException("boundaryWkt must be a POLYGON or MULTIPOLYGON");
            }
            if (geometry.isEmpty() || !geometry.isValid()) {
                throw new InvalidBoundaryException("boundaryWkt is not a valid geometry");
            }
        } catch (ParseException ex) {
            throw new InvalidBoundaryException("boundaryWkt is not a valid WKT polygon");
        }
    }

    /**
     * Parses every coordinate pair as two finite numbers. This is a structural
     * guard before JTS parsing; PostGIS remains authoritative in production.
     */
    private void validateCoordinates(String value) {
        String body = value.substring(value.indexOf('(') + 1, value.lastIndexOf(')'))
                .replace('(', ' ')
                .replace(')', ' ');
        int pairs = 0;
        for (String token : body.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length != 2) {
                throw new InvalidBoundaryException("boundaryWkt contains malformed coordinates");
            }
            try {
                double longitude = Double.parseDouble(parts[0]);
                double latitude = Double.parseDouble(parts[1]);
                if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                        || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                    throw new InvalidBoundaryException("boundaryWkt contains out-of-range coordinates");
                }
            } catch (NumberFormatException ex) {
                throw new InvalidBoundaryException("boundaryWkt contains non-numeric coordinates");
            }
            pairs++;
        }
        if (pairs < 3) {
            throw new InvalidBoundaryException("boundaryWkt must contain at least three coordinate pairs");
        }
    }
}
