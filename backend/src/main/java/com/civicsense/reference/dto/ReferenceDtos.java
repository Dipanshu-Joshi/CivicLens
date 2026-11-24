package com.civicsense.reference.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response records for categories, jurisdictions and service areas. */
public final class ReferenceDtos {

    private ReferenceDtos() {
    }

    public record CategoryResponse(
            UUID id,
            String code,
            String name,
            String description,
            String icon,
            String color,
            int displayOrder,
            boolean active,
            List<CategoryResponse> children) {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @Size(max = 60) String parentCode,
            @Size(max = 80) String icon,
            @Size(max = 20) String color,
            int displayOrder) {
    }

    public record CategoryPatchRequest(
            @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @Size(max = 80) String icon,
            @Size(max = 20) String color,
            Integer displayOrder,
            Boolean active) {
    }

    public record JurisdictionResponse(
            UUID id,
            String code,
            String name,
            String level,
            String city,
            String state,
            String country,
            String parentCode) {
    }

    public record JurisdictionRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String level,
            @Size(max = 120) String city,
            @Size(max = 120) String state,
            @Size(max = 120) String country,
            @Size(max = 50) String parentCode) {
    }

    public record ServiceAreaRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 4000) String boundaryWkt,
            @Size(max = 50) String coversWard,
            @Size(max = 50) String coversZone,
            UUID jurisdictionId) {
    }

    public record ServiceAreaResponse(
            UUID id,
            UUID departmentId,
            String name,
            String coversWard,
            String coversZone,
            String jurisdictionCode,
            boolean active,
            Instant createdAt) {
    }
}
