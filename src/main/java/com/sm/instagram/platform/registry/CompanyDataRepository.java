package com.sm.instagram.platform.registry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyDataRepository extends JpaRepository<CompanyData, Long> {

    Optional<CompanyData> findByUserId(Long userId);

    Optional<CompanyData> findByNip(String nip);

    boolean existsByNip(String nip);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndDataVerifiedTrue(Long userId);
}
