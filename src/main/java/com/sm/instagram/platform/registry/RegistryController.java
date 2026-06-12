package com.sm.instagram.platform.registry;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.registry.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for company registry operations.
 * All endpoints under /api/** are auto-authenticated via security config.
 */
@Slf4j
@RestController
@RequestMapping("/registry")
@RequiredArgsConstructor
@Tag(name = "Registry", description = "Company registry operations — NIP lookup, data confirmation, and management")
public class RegistryController {

    private final RegistryLookupService registryLookupService;
    private final PermissionUtils permissionUtils;

    @Operation(summary = "Look up company by NIP", description = "Queries GUS BIR1, Biała Lista, and CEIDG to auto-populate company data for user review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company data found"),
            @ApiResponse(responseCode = "400", description = "Invalid NIP format"),
            @ApiResponse(responseCode = "404", description = "NIP not found in GUS registry"),
            @ApiResponse(responseCode = "409", description = "NIP already registered, company inactive, or invalid user type"),
            @ApiResponse(responseCode = "503", description = "Registry API unavailable")
    })
    @PostMapping("/lookup")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<NipLookupResponse> lookupByNip(@RequestBody @Valid NipLookupRequest request) {
        String firebaseUid = permissionUtils.getUserId();
        NipLookupResponse response = registryLookupService.lookupByNip(request.getNip(), firebaseUid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Confirm company data", description = "Persists company data after user review and may auto-activate the account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company data confirmed"),
            @ApiResponse(responseCode = "400", description = "Invalid NIP"),
            @ApiResponse(responseCode = "409", description = "NIP already registered or user already has company data")
    })
    @PostMapping("/confirm")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<CompanyDataConfirmResponse> confirmCompanyData(
            @RequestBody @Valid CompanyDataConfirmRequest request) {

        String firebaseUid = permissionUtils.getUserId();
        CompanyDataConfirmResponse response = registryLookupService.confirmCompanyData(
                firebaseUid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get company data", description = "Returns the current user's confirmed company data, or null if not yet confirmed.")
    @ApiResponse(responseCode = "200", description = "Company data returned (may be null)")
    @GetMapping("/company-data")
    @RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<CompanyDataDtoOut> getCompanyData() {
        String firebaseUid = permissionUtils.getUserId();
        CompanyDataDtoOut data = registryLookupService.getCompanyDataForUser(firebaseUid);
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "Reset company data", description = "Deletes the company data so user can enter a different NIP. Reverts account to IN_VALIDATION.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company data reset successfully"),
            @ApiResponse(responseCode = "409", description = "No company data to reset or invalid user type")
    })
    @DeleteMapping("/company-data")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<Void> resetCompanyData() {
        String firebaseUid = permissionUtils.getUserId();
        registryLookupService.resetCompanyData(firebaseUid);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Refresh company data", description = "Re-fetches company data from all registries and updates the stored record.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company data refreshed"),
            @ApiResponse(responseCode = "409", description = "No existing company data to refresh or invalid user type")
    })
    @PostMapping("/refresh")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<NipLookupResponse> refreshCompanyData() {
        String firebaseUid = permissionUtils.getUserId();
        NipLookupResponse response = registryLookupService.refreshCompanyData(firebaseUid);
        return ResponseEntity.ok(response);
    }
}
