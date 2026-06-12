package com.sm.instagram.platform.auth.repository;

import com.sm.instagram.platform.auth.entity.DeletionRequestStatus;
import com.sm.instagram.platform.auth.entity.PendingDataDeletionRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PendingDataDeletionRequestRepository extends JpaRepository<PendingDataDeletionRequest, Long> {

    Optional<PendingDataDeletionRequest> findByConfirmationCode(String confirmationCode);

    @EntityGraph(attributePaths = "user")
    List<PendingDataDeletionRequest> findAllByStatus(DeletionRequestStatus status);

    Optional<PendingDataDeletionRequest> findByUserId(Long userId);
}
