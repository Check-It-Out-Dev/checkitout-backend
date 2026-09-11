package com.sm.instagram.platform.registry;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.registry.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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

    /**
     * The company data this user has confirmed, or 204 when they have not confirmed any.
     *
     * <p>It used to answer {@code ResponseEntity.ok(data)} with {@code data} null, which is a 200
     * carrying no body and no {@code Content-Type} -- a client that reads the response as JSON gets
     * a parse error at character zero, which is what the fuzzer found. "200, may be null" is not a
     * thing HTTP can say; 204 is exactly the thing it can. Consumers see the same absence either
     * way, because an empty 200 body and a 204 both arrive as null.
     */
    @Operation(summary = "Get company data",
            description = "Returns the current user's confirmed company data, or 204 if none is confirmed yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company data returned"),
            // An empty @Content, or springdoc fills the 204 with the method's return type and the
            // document promises a body on the one status that is defined as having none.
            @ApiResponse(responseCode = "204", description = "This user has not confirmed company data yet",
                    content = @Content)
    })
    @GetMapping("/company-data")
    @RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<CompanyDataDtoOut> getCompanyData() {
        String firebaseUid = permissionUtils.getUserId();
        CompanyDataDtoOut data = registryLookupService.getCompanyDataForUser(firebaseUid);
        return data == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(data);
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
