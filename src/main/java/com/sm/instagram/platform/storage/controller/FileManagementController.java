package com.sm.instagram.platform.storage.controller;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import io.jsonwebtoken.Claims;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.storage.model.DeleteFileRequest;
import com.sm.instagram.platform.storage.model.DeleteFilesRequest;
import com.sm.instagram.platform.storage.model.DeleteFolderRequest;
import com.sm.instagram.platform.storage.model.FileOperationResponse;
import com.sm.instagram.platform.storage.service.FirebaseStorageService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import com.sm.instagram.platform.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

/**
 * File Management Controller for Firebase Storage operations.
 * 
 * Provides secure endpoints for file deletion operations including:
 * - Single file deletion
 * - Batch file deletion
 * - Folder deletion
 * 
 * All operations include:
 * - Ownership verification
 * - Audit logging
 * - Rate limiting
 * 
 * @author CheckItOut Platform Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileManagementController {

    private final Storage storage;
    private final UserRepository userRepository;
    private final FirebaseStorageService storageService;
    private final StorageRateLimitService storageRateLimitService;
    
    @Value("${gcp.bucket-name}")
    private String bucketName;

    /**
     * Delete a single file from Firebase Storage.
     * 
     * @param fileUrl URL of the file to delete
     * @param servletRequest HTTP request for context
     * @return Response indicating success or failure
     */
    @DeleteMapping("/delete")
    @RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<FileOperationResponse> deleteFile(
            @RequestParam String fileUrl,
            HttpServletRequest servletRequest) {
        
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("operation", "file.delete");
        
        try {
            // Get authenticated user from Spring Security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new ResourceNotFoundException("error.auth.not_authenticated");
            }
            
            // Get user details - auth.getPrincipal() is the Firebase UID
            String userId = auth.getPrincipal().toString();
            
            // Get claims from auth details if available
            String userEmail = "unknown";
            if (auth.getDetails() instanceof Claims) {
                Claims claims = (Claims) auth.getDetails();
                userEmail = claims.get("email", String.class);
            }
            
            log.info("GDPR: Operation=deleteFile, FirebaseUID={}, Email={}, FileName={}, Purpose=file_management", 
                userId, maskEmail(userEmail), extractFileName(fileUrl));
            
            // Verify file ownership
            if (!verifyFileOwnership(fileUrl, userId)) {
                log.warn("GDPR: Operation=deleteFile_unauthorized, FirebaseUID={}, Email={}, FileName={}, Purpose=security", 
                    userId, maskEmail(userEmail), extractFileName(fileUrl));
                
                throw new BusinessRuleTranslatableException("error.business.insufficient_permissions");
            }
            
            // Extract blob path from URL
            String blobPath = extractBlobPath(fileUrl);

            // Get file size BEFORE deletion for storage quota reclamation
            BlobId blobId = BlobId.of(bucketName, blobPath);
            Blob blob = storage.get(blobId);
            Long fileSize = blob != null ? blob.getSize() : null;

            // Delete from Firebase Storage
            boolean deleted = storage.delete(blobId);

            if (!deleted) {
                log.warn("GDPR: Operation=deleteFile_notFound, FirebaseUID={}, FileName={}, Purpose=file_management",
                    userId, extractFileName(fileUrl));

                throw new ResourceNotFoundException("error.business.item_not_found", "File");
            }

            // Reclaim storage quota AFTER successful deletion
            if (fileSize != null && fileSize > 0) {
                storageRateLimitService.decreaseUserStorage(userId, fileSize);
                log.info("GDPR: Operation=storageReclaimed, FirebaseUID={}, FileSize={}, Purpose=quota_management",
                    userId, fileSize);
            }

            // Audit log - success
            log.warn("GDPR: DELETION Operation=deleteFile_success, FirebaseUID={}, FileName={}, FileSize={}, CorrelationID={}, Purpose=data_deletion",
                userId, extractFileName(fileUrl), fileSize, correlationId);
            
            return ResponseEntity.ok(FileOperationResponse.success(
                "File deleted successfully", 
                Map.of("deletedFile", fileUrl, "correlationId", correlationId)
            ));
            
        } catch (ResourceNotFoundException | BusinessRuleTranslatableException e) {
            throw e;
        } catch (Exception e) {
            log.error("File deletion failed - error: {}", e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.delete_failed");
        } finally {
            MDC.clear();
        }
    }

    /**
     * Delete multiple files from Firebase Storage.
     * 
     * @param request List of file URLs to delete
     * @param servletRequest HTTP request for context
     * @return Response with results for each file
     */
    @DeleteMapping("/batch-delete")
    @RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<FileOperationResponse> deleteFiles(
            @Valid @RequestBody DeleteFilesRequest request,
            HttpServletRequest servletRequest) {
        
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("operation", "file.batch-delete");
        
        try {
            // Get authenticated user from Spring Security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new ResourceNotFoundException("error.auth.not_authenticated");
            }
            
            // Get user details - auth.getPrincipal() is the Firebase UID
            String userId = auth.getPrincipal().toString();
            
            // Get claims from auth details if available
            String userEmail = "unknown";
            if (auth.getDetails() instanceof Claims) {
                Claims claims = (Claims) auth.getDetails();
                userEmail = claims.get("email", String.class);
            }
            
            if (request.getFileUrls() == null || request.getFileUrls().isEmpty()) {
                throw new ValidationTranslatableException("error.validation.empty_list", "File URLs");
            }
            
            if (request.getFileUrls().size() > 100) {
                throw new ValidationTranslatableException("error.validation.list_too_large", "100");
            }
            
            log.info("GDPR: Operation=batchDeleteFiles, FirebaseUID={}, Email={}, FileCount={}, Purpose=bulk_file_management", 
                userId, maskEmail(userEmail), request.getFileUrls().size());
            
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            
            for (String fileUrl : request.getFileUrls()) {
                Map<String, Object> result = new HashMap<>();
                result.put("fileUrl", fileUrl);
                
                try {
                    // Verify ownership for each file
                    if (!verifyFileOwnership(fileUrl, userId)) {
                        result.put("status", "UNAUTHORIZED");
                        result.put("error", "No permission to delete this file");
                        failureCount++;
                    } else {
                        // Get file size before deletion for storage quota reclamation
                        String blobPath = extractBlobPath(fileUrl);
                        BlobId blobId = BlobId.of(bucketName, blobPath);
                        Blob blob = storage.get(blobId);
                        Long fileSize = blob != null ? blob.getSize() : null;

                        // Delete the file
                        boolean deleted = storage.delete(blobId);

                        if (deleted) {
                            result.put("status", "SUCCESS");
                            successCount++;

                            // Reclaim storage quota after successful deletion
                            if (fileSize != null && fileSize > 0) {
                                storageRateLimitService.decreaseUserStorage(userId, fileSize);
                                result.put("sizeReclaimed", fileSize);
                            }
                        } else {
                            result.put("status", "NOT_FOUND");
                            result.put("error", "File not found");
                            failureCount++;
                        }
                    }
                } catch (Exception e) {
                    result.put("status", "ERROR");
                    result.put("error", e.getMessage());
                    failureCount++;
                }
                
                results.add(result);
            }
            
            // Audit log - batch operation complete
            log.warn("GDPR: DELETION Operation=batchDelete_complete, FirebaseUID={}, SuccessCount={}, FailedCount={}, CorrelationID={}, Purpose=bulk_deletion", 
                userId, successCount, failureCount, correlationId);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("results", results);
            responseData.put("summary", Map.of(
                "total", request.getFileUrls().size(),
                "success", successCount,
                "failed", failureCount
            ));
            responseData.put("correlationId", correlationId);
            
            return ResponseEntity.ok(FileOperationResponse.success(
                "Batch deletion completed", responseData
            ));
            
        } catch (ResourceNotFoundException | ValidationTranslatableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Batch deletion failed - error: {}", e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.delete_failed");
        } finally {
            MDC.clear();
        }
    }

    /**
     * Delete all files in a folder from Firebase Storage.
     * 
     * @param path Folder path to delete
     * @param servletRequest HTTP request for context
     * @return Response indicating success or failure
     */
    @DeleteMapping("/folder")
    @RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<FileOperationResponse> deleteFolder(
            @RequestParam String path,
            HttpServletRequest servletRequest) {
        
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("operation", "folder.delete");
        
        try {
            // Get authenticated user from Spring Security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new ResourceNotFoundException("error.auth.not_authenticated");
            }
            
            // Get user details - auth.getPrincipal() is the Firebase UID
            String userId = auth.getPrincipal().toString();
            
            // Get claims from auth details if available
            String userEmail = "unknown";
            if (auth.getDetails() instanceof Claims) {
                Claims claims = (Claims) auth.getDetails();
                userEmail = claims.get("email", String.class);
            }
            
            if (path == null || path.trim().isEmpty()) {
                throw new ValidationTranslatableException("error.validation.missing_parameter", "path");
            }
            
            log.info("GDPR: Operation=deleteFolder, FirebaseUID={}, Email={}, FolderPath={}, Purpose=folder_management", 
                userId, maskEmail(userEmail), path);
            
            // Ensure path includes user ID for ownership verification
            String userPath = "users/" + userId + "/";
            String contentPath = "content/" + userId + "/";
            if (!path.startsWith(userPath) && !path.startsWith(contentPath)) {
                log.warn("Unauthorized folder deletion attempt - user: {}, path: {}",
                    maskEmail(userEmail), path);

                throw new BusinessRuleTranslatableException("error.business.insufficient_permissions");
            }

            // Calculate total folder size BEFORE deletion for storage quota reclamation
            long totalFolderSize = calculateFolderSize(path);

            // List and delete all blobs in the folder
            int deletedCount = storageService.deleteFolder(bucketName, path);

            // Reclaim storage quota AFTER successful folder deletion
            if (deletedCount > 0 && totalFolderSize > 0) {
                storageRateLimitService.decreaseUserStorage(userId, totalFolderSize);
                log.info("GDPR: Operation=folderStorageReclaimed, FirebaseUID={}, FolderPath={}, TotalSize={}, FilesDeleted={}, Purpose=quota_management",
                    userId, path, totalFolderSize, deletedCount);
            }

            // Audit log - folder deletion
            log.warn("GDPR: DELETION Operation=deleteFolder_success, FirebaseUID={}, FolderPath={}, FilesDeleted={}, TotalSizeReclaimed={}, CorrelationID={}, Purpose=folder_deletion",
                userId, path, deletedCount, totalFolderSize, correlationId);
            
            return ResponseEntity.ok(FileOperationResponse.success(
                "Folder deleted successfully", 
                Map.of(
                    "deletedPath", path,
                    "filesDeleted", deletedCount,
                    "correlationId", correlationId
                )
            ));
            
        } catch (ResourceNotFoundException | BusinessRuleTranslatableException | ValidationTranslatableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Folder deletion failed - error: {}", e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.delete_failed");
        } finally {
            MDC.clear();
        }
    }

    /**
     * Verify that the user owns the file.
     * Files may be stored under users/{userId}/ or content/{userId}/ path.
     */
    private boolean verifyFileOwnership(String fileUrl, String userId) {
        try {
            String blobPath = extractBlobPath(fileUrl);
            return blobPath.startsWith("users/" + userId + "/")
                    || blobPath.startsWith("content/" + userId + "/");
        } catch (Exception e) {
            log.error("Failed to verify file ownership", e);
            return false;
        }
    }

    /**
     * Extract blob path from Firebase Storage URL.
     *
     * SECURITY: This method normalizes paths and rejects path traversal attempts.
     * Attack vector prevented: users/ATTACKER/../VICTIM/file.jpg
     */
    private String extractBlobPath(String fileUrl) {
        try {
            String rawPath;

            // Handle Firebase Storage URLs
            if (fileUrl.contains("firebasestorage.googleapis.com")) {
                // Extract path after /o/ and before ?
                int startIndex = fileUrl.indexOf("/o/") + 3;
                int endIndex = fileUrl.indexOf("?", startIndex);
                if (endIndex == -1) {
                    endIndex = fileUrl.length();
                }
                String encodedPath = fileUrl.substring(startIndex, endIndex);
                // Decode the URL-encoded path
                rawPath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8);
            } else {
                // Handle direct paths
                rawPath = fileUrl;
            }

            // SECURITY: Reject null-byte injection attempts
            if (rawPath.contains("\0")) {
                log.warn("SECURITY: Null-byte injection attempt detected in path: {}",
                    rawPath.replace("\0", "\\0"));
                throw new IllegalArgumentException("Invalid file path: contains illegal characters");
            }

            // SECURITY: Normalize path to resolve any ../ sequences
            // Use Paths.get().normalize() to resolve relative path components
            String normalizedPath = Paths.get(rawPath).normalize().toString().replace("\\", "/");

            // SECURITY: Reject paths that try to escape the expected structure
            // After normalization, the path should NOT contain .. (all should be resolved)
            // Also reject absolute paths (starting with /) and paths that resolved outside intended scope
            if (normalizedPath.contains("..") || normalizedPath.startsWith("/")) {
                log.warn("SECURITY: Path traversal attempt detected. Raw: '{}', Normalized: '{}'",
                    rawPath, normalizedPath);
                throw new IllegalArgumentException("Invalid file path: path traversal not allowed");
            }

            // SECURITY: Verify the normalized path still looks valid (starts with expected prefix)
            // If the original path was users/abc/../xyz/file.jpg, normalized would be xyz/file.jpg
            // which would fail the ownership check, but we reject it earlier for clarity
            if (!normalizedPath.startsWith("users/") && !normalizedPath.startsWith("content/") && !rawPath.equals(normalizedPath)) {
                log.warn("SECURITY: Path normalization changed path structure. Raw: '{}', Normalized: '{}'",
                    rawPath, normalizedPath);
                throw new IllegalArgumentException("Invalid file path: path manipulation detected");
            }

            return normalizedPath;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract blob path from URL: {}", fileUrl, e);
            throw new IllegalArgumentException("Invalid file URL");
        }
    }

    /**
     * Extract file name from URL for logging.
     */
    private String extractFileName(String fileUrl) {
        try {
            String blobPath = extractBlobPath(fileUrl);
            int lastSlash = blobPath.lastIndexOf('/');
            return lastSlash >= 0 ? blobPath.substring(lastSlash + 1) : blobPath;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Calculate total size of all files in a folder.
     * Used for storage quota reclamation during folder deletion.
     *
     * @param folderPath The folder path in the bucket
     * @return Total size in bytes, or 0 if calculation fails
     */
    private long calculateFolderSize(String folderPath) {
        try {
            long totalSize = 0;
            // List all blobs with the folder prefix
            com.google.api.gax.paging.Page<Blob> blobs = storage.list(bucketName,
                Storage.BlobListOption.prefix(folderPath));

            for (Blob blob : blobs.iterateAll()) {
                // Only count actual files (not directory markers)
                if (blob.getSize() != null && blob.getSize() > 0) {
                    totalSize += blob.getSize();
                }
            }

            log.debug("Calculated folder size for '{}': {} bytes", folderPath, totalSize);
            return totalSize;
        } catch (Exception e) {
            log.warn("Failed to calculate folder size for '{}': {} - quota may not be fully reclaimed",
                folderPath, e.getMessage());
            return 0; // Fail open - proceed with deletion even if size calculation fails
        }
    }

    /**
     * Create structured audit log entry.
     */
    private String createAuditLog(String event, String userId, String email, 
                                  String details, String correlationId) {
        Map<String, Object> log = new HashMap<>();
        log.put("timestamp", System.currentTimeMillis());
        log.put("service", "file-management");
        log.put("event", event);
        log.put("userId", userId);
        log.put("email", maskEmail(email));
        log.put("details", details);
        log.put("correlationId", correlationId);
        
        try {
            return log.toString();
        } catch (Exception e) {
            return String.format("{\"event\":\"%s\",\"error\":\"log_format_failed\"}", event);
        }
    }

    /**
     * Mask email for logging.
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 4 || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String local = parts[0].length() > 3 ? 
            parts[0].substring(0, 3) + "***" : "***";
        return local + "@" + parts[1];
    }
}
