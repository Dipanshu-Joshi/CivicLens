package com.civicsense.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.civicsense.complaint.exception.ComplaintExceptions.InvalidBoundaryException;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.LocationSource;
import com.civicsense.reference.GeometryValidator;

class Phase6RoutingUnitTest {

    @Test
    void missingAndOutOfRangeCoordinatesAreRejected() {
        Complaint complaint = new Complaint();
        assertThat(RoutingEngine.validCoordinates(complaint)).isFalse();

        complaint.setLatitude(91.0);
        complaint.setLongitude(77.0);
        assertThat(RoutingEngine.validCoordinates(complaint)).isFalse();

        complaint.setLatitude(28.6);
        complaint.setLongitude(Double.NaN);
        assertThat(RoutingEngine.validCoordinates(complaint)).isFalse();

        complaint.setLongitude(77.2);
        complaint.setLocationSource(LocationSource.MANUAL);
        assertThat(RoutingEngine.validCoordinates(complaint)).isTrue();
    }

    @Test
    void structurallyParsableButInvalidGeometryIsRejected() {
        GeometryValidator validator = new GeometryValidator();
        String bowTie = "POLYGON ((0 0, 10 10, 0 10, 10 0, 0 0))";

        assertThatThrownBy(() -> validator.validateMultiPolygon(bowTie))
                .isInstanceOf(InvalidBoundaryException.class)
                .hasMessageContaining("valid geometry");
    }

    @Test
    void validPolygonIsAccepted() {
        new GeometryValidator().validateMultiPolygon(
                "POLYGON ((77.0 28.0, 78.0 28.0, 78.0 29.0, 77.0 29.0, 77.0 28.0))");
    }
}
