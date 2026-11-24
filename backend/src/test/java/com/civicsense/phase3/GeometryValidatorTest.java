package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.civicsense.complaint.exception.ComplaintExceptions.InvalidBoundaryException;
import com.civicsense.reference.GeometryValidator;

class GeometryValidatorTest {

    private final GeometryValidator validator = new GeometryValidator();

    @Test
    void acceptsPolygonAndMultiPolygonWkt() {
        assertThatCode(() -> validator.validateMultiPolygon(
                "POLYGON((77.1 28.5, 77.3 28.5, 77.3 28.7, 77.1 28.7, 77.1 28.5))"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateMultiPolygon(
                "MULTIPOLYGON(((77.1 28.5, 77.2 28.5, 77.2 28.6, 77.1 28.5)))"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankAndWrongShapeTypes() {
        assertThatThrownBy(() -> validator.validateMultiPolygon("  "))
                .isInstanceOf(InvalidBoundaryException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> validator.validateMultiPolygon("POINT(77 28)"))
                .isInstanceOf(InvalidBoundaryException.class)
                .hasMessageContaining("POLYGON or MULTIPOLYGON");
    }

    @Test
    void rejectsMalformedAndUnbalancedBoundaries() {
        assertThatThrownBy(() -> validator.validateMultiPolygon("POLYGON(77 28)"))
                .isInstanceOf(InvalidBoundaryException.class);
        assertThatThrownBy(() -> validator.validateMultiPolygon(
                "POLYGON((77.1 28.5, 77.3 28.5, 77.3 28.7, 77.1 28.5)"))
                .isInstanceOf(InvalidBoundaryException.class)
                .hasMessageContaining("Unbalanced");
    }

    @Test
    void rejectsNonNumericCoordinates() {
        assertThatThrownBy(() -> validator.validateMultiPolygon(
                "POLYGON((77.1 28.5, 77.3 north, 77.3 28.7, 77.1 28.5))"))
                .isInstanceOf(InvalidBoundaryException.class)
                .hasMessageContaining("non-numeric");
    }
}
