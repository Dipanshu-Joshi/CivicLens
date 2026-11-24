package com.civicsense.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.Jurisdiction;

public interface JurisdictionRepository extends JpaRepository<Jurisdiction, UUID> {

    Optional<Jurisdiction> findByCode(String code);

    List<Jurisdiction> findAllByOrderByCodeAsc();

    List<Jurisdiction> findAllByActiveTrueOrderByCodeAsc();
}
