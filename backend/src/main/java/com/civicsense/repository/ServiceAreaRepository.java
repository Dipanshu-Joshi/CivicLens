package com.civicsense.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.ServiceArea;

public interface ServiceAreaRepository extends JpaRepository<ServiceArea, UUID> {

    List<ServiceArea> findAllByDepartmentIdAndActiveTrue(UUID departmentId);

    Optional<ServiceArea> findByIdAndDepartmentId(UUID id, UUID departmentId);

    List<ServiceArea> findAllByActiveTrue();

    List<ServiceArea> findAllByJurisdictionIdAndActiveTrue(UUID jurisdictionId);
}
