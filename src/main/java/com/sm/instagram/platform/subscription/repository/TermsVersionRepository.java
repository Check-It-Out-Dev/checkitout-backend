package com.sm.instagram.platform.subscription.repository;

import com.sm.instagram.platform.subscription.entity.TermsVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {

    Optional<TermsVersion> findTopByOrderByVersionDesc();
}
