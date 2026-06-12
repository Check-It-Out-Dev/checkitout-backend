package com.sm.instagram.platform.address;

import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * REST controller for managing addresses.
 */
@Slf4j
@RestController
@RequestMapping("/address")
@Tag(name = "Address API", description = "Endpoints for managing address data")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class AddressController extends BaseController<Address, Long, AddressDtoIn, AddressDtoOut> {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        super(Address.class);
        this.addressService = addressService;
    }

    @Override
    protected BaseService<Address, Long, AddressDtoIn> getService() {
        return addressService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new address", description = "Creates a new address based on the provided data")
    @ApiResponse(responseCode = "200", description = "Address created successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "403", description = "User not authorized to create this address")
    public ResponseEntity<AddressDtoOut> create(@Valid @RequestBody AddressDtoIn dto) {
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=createAddress, FirebaseUID={}, Purpose=address_creation", firebaseUid);
        log.info("GDPR: DataAccessed=address.street,address.city,address.postalCode,address.country, FirebaseUID={}, Purpose=address_storage", firebaseUid);

        try {
            return super.create(dto);
        } catch (Exception e) {
            log.error("GDPR: Operation=createAddress_failed, FirebaseUID={}, Error={}", firebaseUid, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    @Operation(summary = "Get address by ID", description = "Returns an address by its ID if the user has permission")
    @ApiResponse(responseCode = "200", description = "Address found successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "403", description = "User not authorized to view this address")
    @ApiResponse(responseCode = "404", description = "Address not found")
    public ResponseEntity<AddressDtoOut> getById(@PathVariable Long id) {
        validateId(id, "id");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getAddressById, FirebaseUID={}, AddressID={}, Purpose=address_retrieval", firebaseUid, id);

        try {
            ResponseEntity<AddressDtoOut> response = super.getById(id);
            log.info("GDPR: DataAccessed=address.all_fields, FirebaseUID={}, AddressID={}, Purpose=display", firebaseUid, id);
            return response;
        } catch (Exception e) {
            log.error("GDPR: Operation=getAddressById_failed, FirebaseUID={}, AddressID={}, Error={}", firebaseUid, id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    @Operation(summary = "Update an address", description = "Updates an existing address if the user has permission")
    @ApiResponse(responseCode = "200", description = "Address updated successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "403", description = "User not authorized to update this address")
    @ApiResponse(responseCode = "404", description = "Address not found")
    public ResponseEntity<AddressDtoOut> update(@PathVariable Long id, @Valid @RequestBody AddressDtoIn dto) {
        validateId(id, "id");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=updateAddress, FirebaseUID={}, AddressID={}, Purpose=address_modification", firebaseUid, id);
        log.info("GDPR: DataModified=address.all_fields, FirebaseUID={}, AddressID={}, Purpose=user_update", firebaseUid, id);

        try {
            return super.update(id, dto);
        } catch (Exception e) {
            log.error("GDPR: Operation=updateAddress_failed, FirebaseUID={}, AddressID={}, Error={}", firebaseUid, id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{ids}")
    @Operation(summary = "Delete addresses", description = "Deletes one or more addresses if the user has permission")
    @ApiResponse(responseCode = "204", description = "Addresses deleted successfully")
    @ApiResponse(responseCode = "403", description = "User not authorized to delete one or more addresses")
    public ResponseEntity<Void> delete(@PathVariable List<Long> ids) {
        validateIdList(ids, "ids");
        String firebaseUid = getAuthenticatedUserUid();

        log.warn("GDPR: DELETION Operation=deleteAddresses, FirebaseUID={}, AddressIDs={}, Purpose=user_requested", firebaseUid, ids);

        try {
            ResponseEntity<Void> response = super.delete(ids);
            log.info("GDPR: DELETION_COMPLETE AddressIDs={}, FirebaseUID={}, Permanent=true", ids, firebaseUid);
            return response;
        } catch (Exception e) {
            log.error("GDPR: DELETION_FAILED Operation=deleteAddresses, FirebaseUID={}, AddressIDs={}, Error={}", firebaseUid, ids, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get all available address types.
     *
     * @return list of address types
     * @throws ResourceNotFoundException if authentication context is missing
     */
    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all address types", description = "Returns a list of all available address types")
    @ApiResponse(responseCode = "200", description = "Address types returned successfully")
    public ResponseEntity<List<String>> getAddressTypes() {
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getAddressTypes, FirebaseUID={}, Purpose=metadata_retrieval", firebaseUid);
        log.info("Retrieving address type enum values");

        try {
            // Return the predefined address types as strings
            List<String> addressTypes = Arrays.asList("MAIN", "SECONDARY", "BILLING", "SHIPPING", "TEMPORARY");

            log.debug("Retrieved {} address types: {}", addressTypes.size(), addressTypes);
            log.info("GDPR: DataAccessed=address_types_metadata, FirebaseUID={}, Purpose=enum_display", firebaseUid);
            return ResponseEntity.ok(addressTypes);
        } catch (Exception e) {
            log.error("GDPR: Operation=getAddressTypes_failed, FirebaseUID={}, Error={}", firebaseUid, e.getMessage());
            log.error("Failed to retrieve address types - {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get all addresses for a specific user.
     *
     * @param userId the user ID
     * @return list of addresses
     * @throws ValidationTranslatableException if userId is invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get addresses by user ID", description = "Returns all addresses for a specific user")
    @ApiResponse(responseCode = "200", description = "Addresses returned successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to view these addresses")
    public ResponseEntity<List<AddressDtoOut>> getAddressesByUserId(
            @Parameter(description = "User ID", required = true) @PathVariable Long userId) {
        validateId(userId, "userId");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getAddressesByUserId, FirebaseUID={}, UserID={}, Purpose=user_addresses_retrieval", firebaseUid, userId);
        log.info("Retrieving addresses for user ID: {}", userId);
        long startTime = System.currentTimeMillis();

        try {
            List<AddressDtoOut> dtoList = addressService.findAddressesByUserIdAsDto(userId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully retrieved {} addresses for user ID: {} in {}ms",
                    dtoList.size(), userId, duration);
            log.info("GDPR: DataAccessed=user.addresses[], FirebaseUID={}, UserID={}, RecordCount={}, Purpose=list_display",
                    firebaseUid, userId, dtoList.size());

            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("GDPR: Operation=getAddressesByUserId_failed, FirebaseUID={}, UserID={}, Error={}",
                    firebaseUid, userId, e.getMessage());
            log.error("Failed to retrieve addresses for user ID: {} after {}ms - {}",
                    userId, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Get all addresses for a specific partnership opportunity.
     *
     * @param opportunityId the partnership opportunity ID
     * @return list of addresses
     * @throws ValidationTranslatableException if opportunityId is invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @GetMapping("/opportunity/{opportunityId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get addresses by opportunity ID",
            description = "Returns all addresses for a specific partnership opportunity")
    @ApiResponse(responseCode = "200", description = "Addresses returned successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "Partnership opportunity not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to view these addresses")
    public ResponseEntity<List<AddressDtoOut>> getAddressesByOpportunityId(
            @Parameter(description = "Partnership opportunity ID", required = true)
            @PathVariable Long opportunityId) {
        validateId(opportunityId, "opportunityId");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getAddressesByOpportunityId, FirebaseUID={}, OpportunityID={}, Purpose=opportunity_addresses_retrieval",
                firebaseUid, opportunityId);

        try {
            List<AddressDtoOut> dtoList = addressService.findAddressesByOpportunityIdAsDto(opportunityId);
            log.info("GDPR: DataAccessed=opportunity.addresses[], FirebaseUID={}, OpportunityID={}, RecordCount={}, Purpose=list_display",
                    firebaseUid, opportunityId, dtoList.size());
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            log.error("GDPR: Operation=getAddressesByOpportunityId_failed, FirebaseUID={}, OpportunityID={}, Error={}",
                    firebaseUid, opportunityId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Search for reusable addresses based on address components.
     * This endpoint helps users find existing addresses they can reuse for new opportunities.
     * All parameters are optional and support partial matching.
     *
     * @param street     Street name to search for (optional, partial match)
     * @param city       City name to search for (optional, partial match)
     * @param postalCode Postal code to search for (optional, partial match)
     * @param country    Country to search for (optional, partial match)
     * @return list of matching addresses that can be reused
     * @throws ResourceNotFoundException if authentication context is missing
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search for reusable addresses",
            description = "Finds existing addresses that match the given criteria and can be reused for new opportunities. " +
                    "All parameters are optional and support partial matching (case-insensitive).")
    @ApiResponse(responseCode = "200", description = "Addresses found successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    public ResponseEntity<List<AddressDtoOut>> searchReusableAddresses(
            @Parameter(description = "Street name (optional, partial match)", required = false)
            @RequestParam(required = false) String street,
            @Parameter(description = "City name (optional, partial match)", required = false)
            @RequestParam(required = false) String city,
            @Parameter(description = "Postal code (optional, partial match)", required = false)
            @RequestParam(required = false) String postalCode,
            @Parameter(description = "Country name (optional, partial match)", required = false)
            @RequestParam(required = false) String country) {

        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=searchAddresses, FirebaseUID={}, SearchCriteria=[street={},city={},postalCode={},country={}], Purpose=address_search",
                firebaseUid, street, city, postalCode, country);
        log.info("Searching for reusable addresses with street: '{}', city: '{}', postalCode: '{}', country: '{}'",
                street, city, postalCode, country);

        List<AddressDtoOut> dtoList = addressService.searchReusableAddressesFlexibleAsDto(street, city, postalCode, country);

        log.info("Found {} reusable addresses", dtoList.size());
        log.info("GDPR: DataAccessed=addresses.search_results, FirebaseUID={}, ResultCount={}, Purpose=address_reuse",
                firebaseUid, dtoList.size());
        return ResponseEntity.ok(dtoList);
    }

    /**
     * Get primary address for a specific user.
     *
     * @param userId the user ID
     * @return the primary address
     * @throws ValidationTranslatableException if userId is invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @GetMapping("/user/{userId}/primary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get primary address by user ID",
            description = "Returns the primary address for a specific user")
    @ApiResponse(responseCode = "200", description = "Primary address returned successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "User or primary address not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to view this address")
    public ResponseEntity<AddressDtoOut> getPrimaryAddressByUserId(
            @Parameter(description = "User ID", required = true) @PathVariable Long userId) {
        validateId(userId, "userId");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getPrimaryAddressByUserId, FirebaseUID={}, UserID={}, Purpose=primary_address_retrieval",
                firebaseUid, userId);

        try {
            AddressDtoOut dto = addressService.findPrimaryAddressByUserIdAsDto(userId);
            log.info("GDPR: DataAccessed=user.primary_address, FirebaseUID={}, UserID={}, Purpose=display",
                    firebaseUid, userId);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("GDPR: Operation=getPrimaryAddressByUserId_failed, FirebaseUID={}, UserID={}, Error={}",
                    firebaseUid, userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get primary address for a specific partnership opportunity.
     *
     * @param opportunityId the partnership opportunity ID
     * @return the primary address
     * @throws ValidationTranslatableException if opportunityId is invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @GetMapping("/opportunity/{opportunityId}/primary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get primary address by opportunity ID",
            description = "Returns the primary address for a specific partnership opportunity")
    @ApiResponse(responseCode = "200", description = "Primary address returned successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "Partnership opportunity or primary address not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to view this address")
    public ResponseEntity<AddressDtoOut> getPrimaryAddressByOpportunityId(
            @Parameter(description = "Partnership opportunity ID", required = true)
            @PathVariable Long opportunityId) {
        validateId(opportunityId, "opportunityId");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getPrimaryAddressByOpportunityId, FirebaseUID={}, OpportunityID={}, Purpose=primary_address_retrieval",
                firebaseUid, opportunityId);

        try {
            AddressDtoOut dto = addressService.findPrimaryAddressByOpportunityIdAsDto(opportunityId);
            log.info("GDPR: DataAccessed=opportunity.primary_address, FirebaseUID={}, OpportunityID={}, Purpose=display",
                    firebaseUid, opportunityId);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("GDPR: Operation=getPrimaryAddressByOpportunityId_failed, FirebaseUID={}, OpportunityID={}, Error={}",
                    firebaseUid, opportunityId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Set an address as primary.
     *
     * @param id the address ID
     * @return the updated address
     * @throws ValidationTranslatableException if id is invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @PostMapping("/{id}/primary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set address as primary",
            description = "Sets the specified address as primary, unsetting any existing primary address")
    @ApiResponse(responseCode = "200", description = "Address set as primary successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "Address not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to modify this address")
    public ResponseEntity<AddressDtoOut> setAsPrimary(
            @Parameter(description = "Address ID", required = true) @PathVariable Long id) {
        validateId(id, "id");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=setAddressAsPrimary, FirebaseUID={}, AddressID={}, Purpose=primary_designation", firebaseUid, id);

        try {
            AddressDtoOut dto = addressService.setAsPrimaryAsDto(id);
            log.info("GDPR: DataModified=address.isPrimary, FirebaseUID={}, AddressID={}, Purpose=user_preference", firebaseUid, id);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("GDPR: Operation=setAddressAsPrimary_failed, FirebaseUID={}, AddressID={}, Error={}", firebaseUid, id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Create a new address for a user.
     *
     * @param userId the user ID
     * @param dto    the address data
     * @return the created address
     * @throws ValidationTranslatableException if userId is invalid or DTO is invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @PostMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create address for user",
            description = "Creates a new address associated with the specified user")
    @ApiResponse(responseCode = "201", description = "Address created successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to create an address for this user")
    public ResponseEntity<AddressDtoOut> createAddressForUser(
            @Parameter(description = "User ID", required = true) @PathVariable Long userId,
            @Valid @RequestBody AddressDtoIn dto) {
        validateId(userId, "userId");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=createAddressForUser, FirebaseUID={}, UserID={}, Purpose=address_creation",
                firebaseUid, userId);
        log.info("GDPR: DataCreated=address.all_fields, FirebaseUID={}, UserID={}, Purpose=user_address_association",
                firebaseUid, userId);

        try {
            // Set userId in the DTO to pass validation
            dto.setUserId(userId);
            AddressDtoOut addressDto = addressService.createAddressForUserAsDto(userId, dto);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(addressDto);
        } catch (Exception e) {
            log.error("GDPR: Operation=createAddressForUser_failed, FirebaseUID={}, UserID={}, Error={}",
                    firebaseUid, userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Create a new address for a partnership opportunity.
     *
     * @param opportunityId the partnership opportunity ID
     * @param dto           the address data
     * @return the created address
     * @throws ValidationTranslatableException if opportunityId is invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @PostMapping("/opportunity/{opportunityId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create address for opportunity",
            description = "Creates a new address associated with the specified partnership opportunity")
    @ApiResponse(responseCode = "201", description = "Address created successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "Partnership opportunity not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to create an address for this opportunity")
    public ResponseEntity<AddressDtoOut> createAddressForOpportunity(
            @Parameter(description = "Partnership opportunity ID", required = true)
            @PathVariable Long opportunityId,
            @RequestBody AddressDtoIn dto) {
        validateId(opportunityId, "opportunityId");
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=createAddressForOpportunity, FirebaseUID={}, OpportunityID={}, Purpose=address_creation",
                firebaseUid, opportunityId);
        log.info("GDPR: DataCreated=address.all_fields, FirebaseUID={}, OpportunityID={}, Purpose=opportunity_address_association",
                firebaseUid, opportunityId);

        try {
            // Set opportunityId in the DTO to pass validation
            dto.setPartnershipOpportunityId(opportunityId);
            AddressDtoOut addressDto = addressService.createAddressForOpportunityAsDto(opportunityId, dto);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(addressDto);
        } catch (Exception e) {
            log.error("GDPR: Operation=createAddressForOpportunity_failed, FirebaseUID={}, OpportunityID={}, Error={}",
                    firebaseUid, opportunityId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get addresses by type for a specific user.
     *
     * @param userId the user ID
     * @param type   the address type
     * @return list of addresses
     * @throws ValidationTranslatableException if parameters are invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @GetMapping("/user/{userId}/type/{type}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user addresses by type",
            description = "Returns all addresses of a specific type for a user")
    @ApiResponse(responseCode = "200", description = "Addresses returned successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to view these addresses")
    public ResponseEntity<List<AddressDtoOut>> getAddressesByUserIdAndType(
            @Parameter(description = "User ID", required = true) @PathVariable Long userId,
            @Parameter(description = "Address type", required = true) @PathVariable String type) {
        validateId(userId, "userId");
        validateAddressType(type);
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getAddressesByUserIdAndType, FirebaseUID={}, UserID={}, Type={}, Purpose=filtered_address_retrieval",
                firebaseUid, userId, type);

        try {
            List<AddressDtoOut> dtoList = addressService.findAddressesByUserIdAndTypeAsDto(userId, type);
            log.info("GDPR: DataAccessed=user.addresses[type={}], FirebaseUID={}, UserID={}, RecordCount={}, Purpose=filtered_display",
                    type, firebaseUid, userId, dtoList.size());
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            log.error("GDPR: Operation=getAddressesByUserIdAndType_failed, FirebaseUID={}, UserID={}, Type={}, Error={}",
                    firebaseUid, userId, type, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get addresses by type for a specific partnership opportunity.
     *
     * @param opportunityId the partnership opportunity ID
     * @param type          the address type
     * @return list of addresses
     * @throws ValidationTranslatableException if parameters are invalid
     * @throws ResourceNotFoundException       if authentication context is missing
     */
    @GetMapping("/opportunity/{opportunityId}/type/{type}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get opportunity addresses by type",
            description = "Returns all addresses of a specific type for a partnership opportunity")
    @ApiResponse(responseCode = "200", description = "Addresses returned successfully",
            content = @Content(schema = @Schema(implementation = AddressDtoOut.class)))
    @ApiResponse(responseCode = "404", description = "Partnership opportunity not found")
    @ApiResponse(responseCode = "403", description = "User not authorized to view these addresses")
    public ResponseEntity<List<AddressDtoOut>> getAddressesByOpportunityIdAndType(
            @Parameter(description = "Partnership opportunity ID", required = true)
            @PathVariable Long opportunityId,
            @Parameter(description = "Address type", required = true) @PathVariable String type) {
        validateId(opportunityId, "opportunityId");
        validateAddressType(type);
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getAddressesByOpportunityIdAndType, FirebaseUID={}, OpportunityID={}, Type={}, Purpose=filtered_address_retrieval",
                firebaseUid, opportunityId, type);

        try {
            List<AddressDtoOut> dtoList = addressService.findAddressesByOpportunityIdAndTypeAsDto(opportunityId, type);
            log.info("GDPR: DataAccessed=opportunity.addresses[type={}], FirebaseUID={}, OpportunityID={}, RecordCount={}, Purpose=filtered_display",
                    type, firebaseUid, opportunityId, dtoList.size());
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            log.error("GDPR: Operation=getAddressesByOpportunityIdAndType_failed, FirebaseUID={}, OpportunityID={}, Type={}, Error={}",
                    firebaseUid, opportunityId, type, e.getMessage(), e);
            throw e;
        }
    }

    // ===== HELPER METHODS =====

    /**
     * Gets the authenticated user UID from the security context.
     *
     * @return The Firebase UID of the authenticated user
     * @throws ResourceNotFoundException if authentication context is missing
     */
    private String getAuthenticatedUserUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new ResourceNotFoundException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }

    /**
     * Validates an ID parameter.
     *
     * @param id            The ID to validate
     * @param parameterName The name of the parameter for error reporting
     * @throws ValidationTranslatableException if the ID is invalid
     */
    private void validateId(Long id, String parameterName) {
        if (id == null || id <= 0) {
            throw new ValidationTranslatableException("error.validation.invalid_id", parameterName);
        }
    }

    /**
     * Validates a list of IDs.
     *
     * @param ids           The list of IDs to validate
     * @param parameterName The name of the parameter for error reporting
     * @throws ValidationTranslatableException if the ID list is invalid
     */
    private void validateIdList(List<Long> ids, String parameterName) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationTranslatableException("error.validation.empty_list", parameterName);
        }
        if (ids.size() > 100) {
            throw new ValidationTranslatableException("error.validation.list_too_large", "100");
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new ValidationTranslatableException("error.validation.invalid_id", "ID in " + parameterName + " list");
            }
        }
    }

    /**
     * Validates an address type.
     *
     * @param type The address type to validate
     * @throws ValidationTranslatableException if the type is invalid
     */
    private void validateAddressType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "type");
        }

        List<String> validTypes = Arrays.asList("MAIN", "SECONDARY", "BILLING", "SHIPPING", "TEMPORARY");
        if (!validTypes.contains(type.toUpperCase())) {
            throw new ValidationTranslatableException("error.validation.invalid_parameter",
                    "type must be one of: " + String.join(", ", validTypes));
        }
    }
}
