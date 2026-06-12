package com.sm.instagram.platform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * GDPR compliance service for GeoLocation data.
 * 
 * Implements:
 * - Right to erasure (Article 17)
 * - Right to data portability (Article 20)
 * - Data minimization (Article 5)
 * - Storage limitation (Article 5)
 * 
 * This service handles GDPR requests for location data stored in Redis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "geoip.cache.type", havingValue = "redis")
public class GeoLocationGdprService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String GEO_PREFIX = "geo:ip:";
    private static final String TRAVEL_PREFIX = "geo:travel:";
    private static final String AUDIT_PREFIX = "geo:audit:";
    
    /**
     * Delete all location data for a specific IP address.
     * Implements GDPR Article 17 - Right to erasure.
     * 
     * @param ip IP address to delete data for
     * @return Number of entries deleted
     */
    public int deleteIpData(String ip) {
        if (ip == null || ip.isEmpty()) {
            return 0;
        }
        
        // GDPR: Log deletion request (Note: no Firebase UID in service layer)
        log.warn("GDPR: DELETION Operation=deleteIpData, TargetIP={}, Purpose=gdpr_erasure_request, LegalBasis=gdpr_article_17, DataToRemove=ip.location.data",
                maskIp(ip));
        
        log.info("Processing GDPR deletion request for IP: {}", maskIp(ip));
        
        // Find all keys related to this IP
        String pattern = GEO_PREFIX + ip.replace(".", "_") + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        
        int deleted = 0;
        // Redis keys() returns empty Set, not null, but check defensively
        if (keys != null && !keys.isEmpty()) {
            Long count = redisTemplate.delete(keys);
            // delete() returns number of keys deleted, never null in Spring Data Redis
            deleted = count != null ? count.intValue() : 0;
            
            log.info("Deleted {} geo entries for IP (GDPR Article 17)", deleted);
            
            // GDPR: Log successful deletion
            log.warn("GDPR: DELETION_COMPLETE Operation=deleteIpData_SUCCESS, EntriesDeleted={}, DataRemoved=ip.location.cache",
                    deleted);
            
            // Audit the deletion
            auditDeletion("IP_DATA", ip, deleted);
        }
        
        return deleted;
    }
    
    /**
     * Delete all location data for a specific user.
     * Implements GDPR Article 17 - Right to erasure.
     * 
     * @param userId User ID to delete data for
     * @return Number of entries deleted
     */
    public int deleteUserLocationData(Long userId) {
        if (userId == null) {
            return 0;
        }
        
        // GDPR: Log user data deletion request
        log.warn("GDPR: DELETION Operation=deleteUserLocationData, UserID={}, Purpose=gdpr_erasure_request, LegalBasis=gdpr_article_17, DataToRemove=user.travel.patterns",
                userId);
        
        log.info("Processing GDPR deletion request for user: {}", userId);
        
        // Find all travel patterns for this user
        String pattern = TRAVEL_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        
        int deleted = 0;
        // Defensive null check - keys() may return null in edge cases
        if (keys != null && !keys.isEmpty()) {
            Long count = redisTemplate.delete(keys);
            deleted = count != null ? count.intValue() : 0;
            
            log.info("Deleted {} travel pattern entries for user {} (GDPR Article 17)", deleted, userId);
            
            // GDPR: Log successful user data deletion
            log.warn("GDPR: DELETION_COMPLETE Operation=deleteUserLocationData_SUCCESS, UserID={}, EntriesDeleted={}, DataRemoved=user.travel.history",
                    userId, deleted);
            
            // Audit the deletion
            auditDeletion("USER_TRAVEL", userId.toString(), deleted);
        }
        
        return deleted;
    }
    
    /**
     * Export all location data for a user.
     * Implements GDPR Article 20 - Right to data portability.
     * 
     * @param userId User ID to export data for
     * @return Map containing all user's location data
     */
    public Map<String, Object> exportUserLocationData(Long userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        
        // GDPR: Log data export request
        log.info("GDPR: Operation=exportUserLocationData, UserID={}, Purpose=gdpr_data_portability, LegalBasis=gdpr_article_20, DataAccessed=user.location.history",
                userId);
        
        log.info("Processing GDPR data export request for user: {}", userId);
        
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("userId", userId);
        exportData.put("exportDate", Instant.now().toString());
        exportData.put("dataController", "CheckItOut Platform");
        
        // Get all travel patterns
        String pattern = TRAVEL_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        
        List<Map<String, Object>> travelData = new ArrayList<>();
        
        // Defensive check - Redis keys() may return null in rare cases
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                // Extract date from key
                String dateStr = key.substring(key.lastIndexOf(':') + 1);
                
                // Get all travel events for this date
                List<String> events = redisTemplate.opsForList().range(key, 0, -1);
                
                if (events != null) {
                    for (String eventJson : events) {
                        try {
                            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
                            event.put("date", dateStr);
                            
                            // Anonymize IP addresses for export
                            if (event.containsKey("fromIp")) {
                                event.put("fromIp", maskIp((String) event.get("fromIp")));
                            }
                            if (event.containsKey("toIp")) {
                                event.put("toIp", maskIp((String) event.get("toIp")));
                            }
                            
                            travelData.add(event);
                        } catch (Exception e) {
                            log.error("Failed to parse travel event", e);
                        }
                    }
                }
            }
        }
        
        exportData.put("travelPatterns", travelData);
        exportData.put("totalRecords", travelData.size());
        
        // Audit the export
        auditExport("USER_LOCATION", userId.toString(), travelData.size());
        
        // GDPR: Log successful export
        log.info("GDPR: Operation=exportUserLocationData_SUCCESS, UserID={}, RecordsExported={}, DataProvided=anonymized.travel.patterns",
                userId, travelData.size());
        
        log.info("Exported {} travel records for user {} (GDPR Article 20)", 
                travelData.size(), userId);
        
        return exportData;
    }
    
    /**
     * Get data retention summary for a user.
     * Shows what data is stored and when it will be deleted.
     * 
     * @param userId User ID
     * @return Retention summary
     */
    public Map<String, Object> getDataRetentionSummary(Long userId) {
        // GDPR: Log retention summary request
        log.info("GDPR: Operation=getDataRetentionSummary, UserID={}, Purpose=gdpr_transparency, DataAccessed=retention.metadata",
                userId);
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("userId", userId);
        summary.put("timestamp", Instant.now().toString());
        
        // Count travel patterns
        String pattern = TRAVEL_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        int travelRecords = 0;
        
        // Defensive null check for Redis operation
        if (keys != null) {
            for (String key : keys) {
                Long size = redisTemplate.opsForList().size(key);
                if (size != null) {
                    travelRecords += size;
                }
            }
        }
        
        summary.put("travelPatternRecords", travelRecords);
        summary.put("retentionPeriod", "30 days");
        summary.put("automaticDeletion", true);
        summary.put("gdprCompliant", true);
        
        // Calculate oldest and newest data
        // Defensive null check
        if (keys != null && !keys.isEmpty()) {
            List<String> dates = keys.stream()
                .map(k -> k.substring(k.lastIndexOf(':') + 1))
                .sorted()
                .collect(Collectors.toList());
            
            if (!dates.isEmpty()) {
                summary.put("oldestData", dates.get(0));
                summary.put("newestData", dates.get(dates.size() - 1));
            }
        }
        
        return summary;
    }
    
    /**
     * Anonymize all data older than specified days.
     * Implements data minimization principle.
     * 
     * @param olderThanDays Days threshold
     * @return Number of records anonymized
     */
    public int anonymizeOldData(int olderThanDays) {
        // GDPR: Log anonymization operation
        log.info("GDPR: Operation=anonymizeOldData, OlderThanDays={}, Purpose=gdpr_data_minimization, LegalBasis=gdpr_article_5, DataToAnonymize=old.location.data",
                olderThanDays);
        
        log.info("Anonymizing location data older than {} days", olderThanDays);
        
        LocalDate cutoffDate = LocalDate.now().minusDays(olderThanDays);
        int anonymized = 0;
        
        // Find all travel patterns
        Set<String> keys = redisTemplate.keys(TRAVEL_PREFIX + "*");
        
        // Defensive null check - keys() may return null
        if (keys != null) {
            for (String key : keys) {
                // Extract date from key
                String dateStr = key.substring(key.lastIndexOf(':') + 1);
                
                try {
                    LocalDate keyDate = LocalDate.parse(dateStr);
                    
                    if (keyDate.isBefore(cutoffDate)) {
                        // Delete old data
                        redisTemplate.delete(key);
                        anonymized++;
                    }
                } catch (Exception e) {
                    log.debug("Could not parse date from key: {}", key);
                }
            }
        }
        
        if (anonymized > 0) {
            log.info("Anonymized {} old travel pattern records", anonymized);
            
            // GDPR: Log successful anonymization
            log.info("GDPR: Operation=anonymizeOldData_SUCCESS, RecordsAnonymized={}, OlderThanDays={}, DataMinimized=travel.patterns",
                    anonymized, olderThanDays);
            
            auditAnonymization("TRAVEL_PATTERNS", anonymized, olderThanDays);
        }
        
        return anonymized;
    }
    
    /**
     * Get GDPR compliance status.
     * 
     * @return Compliance status report
     */
    public Map<String, Object> getComplianceStatus() {
        // GDPR: Log compliance check
        log.info("GDPR: Operation=getComplianceStatus, Purpose=gdpr_compliance_monitoring, DataAccessed=compliance.metadata");
        
        Map<String, Object> status = new HashMap<>();
        
        status.put("gdprCompliant", true);
        status.put("dataMinimization", true);
        status.put("purposeLimitation", true);
        status.put("storageHasUserData", true);
        status.put("automaticExpiration", true);
        status.put("expirationDays", 7);  // For IP data
        status.put("travelDataRetention", 30);  // For travel patterns
        
        // Data categories
        Map<String, String> dataCategories = new HashMap<>();
        dataCategories.put("ipLocation", "7 days TTL");
        dataCategories.put("travelPatterns", "30 days TTL");
        dataCategories.put("impossibleTravel", "30 days TTL");
        status.put("dataCategories", dataCategories);
        
        // Rights implemented
        List<String> rights = Arrays.asList(
            "Right to erasure (Article 17)",
            "Right to data portability (Article 20)",
            "Right to be informed (Article 13)",
            "Data minimization (Article 5)",
            "Storage limitation (Article 5)"
        );
        status.put("implementedRights", rights);
        
        // Audit trail
        status.put("auditingEnabled", true);
        status.put("encryptionAtRest", false);  // Redis doesn't encrypt by default
        status.put("encryptionInTransit", true);  // If Redis TLS is enabled
        
        return status;
    }
    
    /**
     * Audit a deletion operation.
     */
    private void auditDeletion(String dataType, String identifier, int recordCount) {
        try {
            Map<String, Object> audit = new HashMap<>();
            audit.put("operation", "DELETE");
            audit.put("dataType", dataType);
            audit.put("identifier", maskSensitiveData(identifier));
            audit.put("recordCount", recordCount);
            audit.put("timestamp", Instant.now().toString());
            audit.put("gdprArticle", "17");
            
            String key = AUDIT_PREFIX + "deletion:" + System.currentTimeMillis();
            String json = objectMapper.writeValueAsString(audit);
            
            redisTemplate.opsForValue().set(key, json);
            redisTemplate.expire(key, 90, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Failed to audit deletion", e);
        }
    }
    
    /**
     * Audit a data export operation.
     */
    private void auditExport(String dataType, String identifier, int recordCount) {
        try {
            Map<String, Object> audit = new HashMap<>();
            audit.put("operation", "EXPORT");
            audit.put("dataType", dataType);
            audit.put("identifier", identifier);
            audit.put("recordCount", recordCount);
            audit.put("timestamp", Instant.now().toString());
            audit.put("gdprArticle", "20");
            
            String key = AUDIT_PREFIX + "export:" + System.currentTimeMillis();
            String json = objectMapper.writeValueAsString(audit);
            
            redisTemplate.opsForValue().set(key, json);
            redisTemplate.expire(key, 90, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Failed to audit export", e);
        }
    }
    
    /**
     * Audit an anonymization operation.
     */
    private void auditAnonymization(String dataType, int recordCount, int olderThanDays) {
        try {
            Map<String, Object> audit = new HashMap<>();
            audit.put("operation", "ANONYMIZE");
            audit.put("dataType", dataType);
            audit.put("recordCount", recordCount);
            audit.put("olderThanDays", olderThanDays);
            audit.put("timestamp", Instant.now().toString());
            audit.put("gdprPrinciple", "Data Minimization");
            
            String key = AUDIT_PREFIX + "anonymize:" + System.currentTimeMillis();
            String json = objectMapper.writeValueAsString(audit);
            
            redisTemplate.opsForValue().set(key, json);
            redisTemplate.expire(key, 90, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Failed to audit anonymization", e);
        }
    }
    
    /**
     * Mask IP address for privacy.
     */
    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        
        if (ip.contains(".")) {
            // IPv4: mask last octet
            int lastDot = ip.lastIndexOf('.');
            return lastDot > 0 ? ip.substring(0, lastDot) + ".xxx" : "masked";
        } else if (ip.contains(":")) {
            // IPv6: mask last 4 segments
            int lastColon = ip.lastIndexOf(':');
            return lastColon > 0 ? ip.substring(0, lastColon) + ":xxxx" : "masked";
        }
        return "masked";
    }
    
    /**
     * Mask sensitive data for audit logs.
     */
    private String maskSensitiveData(String data) {
        if (data == null || data.length() <= 4) {
            return "****";
        }
        
        // Show first 2 and last 2 characters only
        return data.substring(0, 2) + "***" + data.substring(data.length() - 2);
    }
}
