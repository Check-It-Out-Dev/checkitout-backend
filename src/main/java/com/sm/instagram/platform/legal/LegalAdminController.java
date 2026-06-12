package com.sm.instagram.platform.legal;

import com.sm.instagram.platform.legal.dto.ConsentRecordAdminDtoOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/legal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class LegalAdminController {

    private final ConsentRecordRepository consentRecordRepository;

    /**
     * Get all consent records for a user (linked + originally-anonymous).
     * Used to verify anonymous cookie consent linking after registration.
     */
    @GetMapping("/consent-records/{userId}")
    public ResponseEntity<List<ConsentRecordAdminDtoOut>> getConsentRecords(@PathVariable Long userId) {
        List<ConsentRecord> records = consentRecordRepository.findByUserIdOrderByTimestampDesc(userId);
        List<ConsentRecordAdminDtoOut> dtos = records.stream()
                .map(this::toAdminDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get recent orphaned anonymous records (user_id IS NULL).
     * Useful for diagnostics — if many exist, the linking flow may be broken.
     */
    @GetMapping("/orphaned-anonymous")
    public ResponseEntity<List<ConsentRecordAdminDtoOut>> getOrphanedAnonymous(
            @RequestParam(defaultValue = "24") int hoursBack) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        List<ConsentRecord> records = consentRecordRepository.findRecentAnonymousRecords(since);
        List<ConsentRecordAdminDtoOut> dtos = records.stream()
                .map(this::toAdminDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private ConsentRecordAdminDtoOut toAdminDto(ConsentRecord record) {
        return ConsentRecordAdminDtoOut.builder()
                .id(record.getId())
                .userId(record.getUser() != null ? record.getUser().getId() : null)
                .documentType(record.getDocument().getType().name())
                .documentVersion(record.getDocument().getVersion())
                .timestamp(record.getTimestamp())
                .source(record.getSource().name())
                .isTrusted(record.getIsTrusted())
                .ipAddress(record.getIpAddress() != null ? record.getIpAddress().getHostAddress() : null)
                .userAgent(record.getUserAgent())
                .wasAnonymous(record.getSource() == ConsentSource.COOKIE_BANNER)
                .build();
    }
}
