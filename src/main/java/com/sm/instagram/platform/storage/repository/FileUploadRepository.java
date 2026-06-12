package com.sm.instagram.platform.storage.repository;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.storage.entity.FileUpload;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileUploadRepository extends BaseRepository<FileUpload, String> {

    Optional<FileUpload> findByFilePath(String filePath);

    List<FileUpload> findByUserId(String userId);

    List<FileUpload> findByUserIdAndStatusIn(String userId, List<FileUpload.UploadStatus> statuses);

    List<FileUpload> findByStatusAndCreatedAtBefore(FileUpload.UploadStatus status, Instant cutoff);

    @Query("SELECT SUM(f.fileSize) FROM FileUpload f WHERE f.userId = ?1 AND f.status IN ('CONFIRMED', 'WEBHOOK')")
    Long getTotalStorageByUser(String userId);

    @Query("SELECT COUNT(f) FROM FileUpload f WHERE f.userId = ?1 AND f.uploadTime > ?2 AND f.status IN ('CONFIRMED', 'WEBHOOK')")
    Long getUploadCountSince(String userId, Instant since);

    @Query("SELECT COUNT(f) FROM FileUpload f WHERE f.userId = ?1 AND f.uploadTime BETWEEN ?2 AND ?3 AND f.status IN ('CONFIRMED', 'WEBHOOK')")
    Long getUploadCountBetween(String userId, Instant start, Instant end);
}
