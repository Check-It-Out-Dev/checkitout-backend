package com.sm.instagram.platform.address;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityRepository;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Service for managing Address entities.
 */
@Service
@Slf4j
public class AddressService extends BaseService<Address, Long, AddressDtoIn> {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final PartnershipOpportunityRepository partnershipOpportunityRepository;
    private final PermissionUtils permissionUtils;

    public AddressService(
            SpecificationBuilder<Address> specificationBuilder,
            AddressRepository addressRepository,
            ModelMapper modelMapper,
            RepositoryResolver repositoryResolver,
            UserRepository userRepository,
            PartnershipOpportunityRepository partnershipOpportunityRepository,
            PermissionUtils permissionUtils,
            ApplicationContext applicationContext) {
        super(applicationContext, specificationBuilder, addressRepository, modelMapper, repositoryResolver);
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.partnershipOpportunityRepository = partnershipOpportunityRepository;
        this.permissionUtils = permissionUtils;
    }

    /**
     * Validates if the current authenticated user is the owner of the address or has admin permissions.
     */
    private void validateOwnership(Address address) {
        if (permissionUtils.isAdmin()) {
            return;
        }

        String currentUserId = permissionUtils.getUserId();
        if (currentUserId == null) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    "anonymous",
                    "validateOwnership",
                    "Address#" + address.getId());
        }

        boolean isOwner = address.getUser() != null && address.getUser().getFirebaseUserId().equals(currentUserId);

        // Check if user owns any of the partnership opportunities associated with this address
        if (!isOwner && address.getPartnershipOpportunities() != null) {
            isOwner = address.getPartnershipOpportunities().stream()
                    .anyMatch(opportunity -> opportunity.getCompany() != null &&
                            opportunity.getCompany().getFirebaseUserId().equals(currentUserId));
        }

        if (!isOwner) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserId,
                    "validateOwnership",
                    "Address#" + address.getId());
        }
    }

    @Override
    public Address findById(Long id) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=findAddressById, FirebaseUID={}, AddressID={}, Purpose=address_retrieval",
                firebaseUid != null ? firebaseUid : "unknown", id);

        Address address = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Address"));
        validateOwnership(address);

        log.info("GDPR: DataAccessed=address.all_fields, FirebaseUID={}, AddressID={}, Purpose=service_processing",
                firebaseUid != null ? firebaseUid : "unknown", id);
        return address;
    }

    /**
     * Fix multiple primary addresses for a user.
     */
    @Transactional
    protected void fixUserPrimaryAddresses(User user, Address exceptAddress) {
        List<Address> primaryAddresses = addressRepository.findByUserAndIsPrimaryTrue(user);

        // If there are multiple primary addresses, keep only one
        if (primaryAddresses.size() > 1) {
            // Keep the address we want to be primary (if it's in the list)
            Address addressToKeepPrimary = exceptAddress != null && exceptAddress.isPrimary() ?
                    primaryAddresses.stream()
                            .filter(a -> a.getId().equals(exceptAddress.getId()))
                            .findFirst()
                            .orElse(null) : null;

            // If the except address isn't in the list or isn't primary, use the most recently updated
            if (addressToKeepPrimary == null) {
                // orElseThrow, not orElse(null): the enclosing branch is size() > 1, so max() is always
                // present and the null was unreachable -- but it was dereferenced two lines later all
                // the same, which is what javabugs:S2259 saw. Saying "cannot happen" out loud beats a
                // null the next reader has to prove impossible.
                //
                // nullsFirst is the defect the analyser did not flag: Comparator.comparing throws NPE
                // inside the comparator when an address has no lastUpdateTime, and a row written before
                // that column was populated has exactly that.
                addressToKeepPrimary = primaryAddresses.stream()
                        .max(Comparator.comparing(Address::getLastUpdateTime,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElseThrow(() -> new IllegalStateException(
                                "more than one primary address, yet no maximum: " + primaryAddresses.size()));
            }

            // Set all other addresses to non-primary
            for (Address address : primaryAddresses) {
                if (!address.getId().equals(addressToKeepPrimary.getId())) {
                    address.setPrimary(false);
                    addressRepository.save(address);
                }
            }
        }
    }

    /**
     * Fix multiple primary addresses for an opportunity.
     */
    @Transactional
    protected void fixOpportunityPrimaryAddresses(PartnershipOpportunity opportunity, Address exceptAddress) {
        List<Address> primaryAddresses = addressRepository.findByPartnershipOpportunityAndIsPrimaryTrue(opportunity);

        // If there are multiple primary addresses, keep only one
        if (primaryAddresses.size() > 1) {
            // Keep the address we want to be primary (if it's in the list)
            Address addressToKeepPrimary = exceptAddress != null && exceptAddress.isPrimary() ?
                    primaryAddresses.stream()
                            .filter(a -> a.getId().equals(exceptAddress.getId()))
                            .findFirst()
                            .orElse(null) : null;

            // If the except address isn't in the list or isn't primary, use the most recently updated
            if (addressToKeepPrimary == null) {
                // orElseThrow, not orElse(null): the enclosing branch is size() > 1, so max() is always
                // present and the null was unreachable -- but it was dereferenced two lines later all
                // the same, which is what javabugs:S2259 saw. Saying "cannot happen" out loud beats a
                // null the next reader has to prove impossible.
                //
                // nullsFirst is the defect the analyser did not flag: Comparator.comparing throws NPE
                // inside the comparator when an address has no lastUpdateTime, and a row written before
                // that column was populated has exactly that.
                addressToKeepPrimary = primaryAddresses.stream()
                        .max(Comparator.comparing(Address::getLastUpdateTime,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElseThrow(() -> new IllegalStateException(
                                "more than one primary address, yet no maximum: " + primaryAddresses.size()));
            }

            // Set all other addresses to non-primary
            for (Address address : primaryAddresses) {
                if (!address.getId().equals(addressToKeepPrimary.getId())) {
                    address.setPrimary(false);
                    addressRepository.save(address);
                }
            }
        }
    }

    /**
     * Manage primary address.
     */
    @Transactional
    private void managePrimaryAddress(Address address) {
        if (address.getUser() != null) {
            if (address.isPrimary()) {
                // Get all primary addresses for this user except the current one
                List<Address> primaryAddresses = addressRepository.findByUserAndIsPrimaryTrue(address.getUser());
                for (Address primaryAddress : primaryAddresses) {
                    if (!primaryAddress.getId().equals(address.getId())) {
                        primaryAddress.setPrimary(false);
                        addressRepository.save(primaryAddress);
                    }
                }
            } else {
                // Ensure at least one address is primary
                List<Address> userAddresses = addressRepository.findByUser(address.getUser());
                boolean anyPrimary = userAddresses.stream()
                        .anyMatch(addr -> !addr.getId().equals(address.getId()) && addr.isPrimary());

                if (!anyPrimary && userAddresses.size() > 1) {
                    // Make another address primary
                    userAddresses.stream()
                            .filter(addr -> !addr.getId().equals(address.getId()))
                            .max(Comparator.comparing(Address::getLastUpdateTime))
                            .ifPresent(addr -> {
                                addr.setPrimary(true);
                                addressRepository.save(addr);
                            });
                }
            }

            // Fix any remaining issues with multiple primary addresses
            fixUserPrimaryAddresses(address.getUser(), address);
        } else if (address.getPartnershipOpportunities() != null && !address.getPartnershipOpportunities().isEmpty()) {
            // For shared addresses, manage primary status per opportunity
            // Note: This is more complex now since the address can be shared
            // For now, we'll keep the address as primary if it's marked as such
            log.debug("Managing primary address for shared address with {} opportunities",
                    address.getPartnershipOpportunities().size());
        }
    }

    /**
     * Handle address type management.
     */
    @Transactional
    protected void manageAddressType(Address address) {
        if ("MAIN".equals(address.getAddressType())) {
            if (address.getUser() != null) {
                List<Address> existingMainAddresses = addressRepository.findByUserAndAddressType(
                        address.getUser(), "MAIN");
                existingMainAddresses.stream()
                        .filter(addr -> !addr.getId().equals(address.getId()))
                        .forEach(addr -> {
                            addr.setAddressType("SECONDARY");
                            addressRepository.save(addr);
                        });
            } else if (address.getPartnershipOpportunities() != null && !address.getPartnershipOpportunities().isEmpty()) {
                // For shared addresses, we need to be more careful about changing address types
                // Since this address might be shared across multiple opportunities
                log.debug("Managing address type for shared address with {} opportunities",
                        address.getPartnershipOpportunities().size());
                // For now, allow MAIN type for shared business locations
            }
        }
    }

    public List<Address> findAddressesByUserId(Long userId) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=findAddressesByUserId, FirebaseUID={}, UserID={}, Purpose=user_addresses_retrieval",
                firebaseUid != null ? firebaseUid : "unknown", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        if (!permissionUtils.isAdmin() && !user.getFirebaseUserId().equals(permissionUtils.getUserId())) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "findAddressesByUserId",
                    "User#" + userId);
        }

        List<Address> addresses = addressRepository.findByUser(user);
        log.info("GDPR: DataAccessed=user.addresses[], FirebaseUID={}, UserID={}, RecordCount={}, Purpose=list_retrieval",
                firebaseUid != null ? firebaseUid : "unknown", userId, addresses.size());
        return addresses;
    }

    public List<Address> findAddressesByOpportunityId(Long opportunityId) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=findAddressesByOpportunityId, FirebaseUID={}, OpportunityID={}, Purpose=opportunity_addresses_retrieval",
                firebaseUid != null ? firebaseUid : "unknown", opportunityId);

        PartnershipOpportunity opportunity = partnershipOpportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Partnership opportunity"));

        if (!permissionUtils.isAdmin() &&
                !opportunity.getCompany().getFirebaseUserId().equals(permissionUtils.getUserId())) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "findAddressesByOpportunityId",
                    "PartnershipOpportunity#" + opportunityId);
        }

        List<Address> addresses = addressRepository.findByPartnershipOpportunity(opportunity);
        log.info("GDPR: DataAccessed=opportunity.addresses[], FirebaseUID={}, OpportunityID={}, RecordCount={}, Purpose=list_retrieval",
                firebaseUid != null ? firebaseUid : "unknown", opportunityId, addresses.size());
        return addresses;
    }

    public Address findPrimaryAddressByUserId(Long userId) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=findPrimaryAddressByUserId, FirebaseUID={}, UserID={}, Purpose=primary_address_retrieval",
                firebaseUid != null ? firebaseUid : "unknown", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        if (!permissionUtils.isAdmin() && !user.getFirebaseUserId().equals(permissionUtils.getUserId())) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "findPrimaryAddressByUserId",
                    "User#" + userId);
        }

        // Fix any issues with multiple primary addresses
        fixUserPrimaryAddresses(user, null);

        // Get the primary address (should be only one now)
        List<Address> primaryAddresses = addressRepository.findByUserAndIsPrimaryTrue(user);
        if (primaryAddresses.isEmpty()) {
            throw new ResourceNotFoundException("error.business.item_not_found", "Primary address");
        }

        Address primaryAddress = primaryAddresses.getFirst();
        log.info("GDPR: DataAccessed=user.primary_address, FirebaseUID={}, UserID={}, AddressID={}, Purpose=primary_retrieved",
                firebaseUid != null ? firebaseUid : "unknown", userId, primaryAddress.getId());
        return primaryAddress;
    }

    public Address findPrimaryAddressByOpportunityId(Long opportunityId) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=findPrimaryAddressByOpportunityId, FirebaseUID={}, OpportunityID={}, Purpose=primary_address_retrieval",
                firebaseUid != null ? firebaseUid : "unknown", opportunityId);
        PartnershipOpportunity opportunity = partnershipOpportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Partnership opportunity"));

        if (!permissionUtils.isAdmin() &&
                !opportunity.getCompany().getFirebaseUserId().equals(permissionUtils.getUserId())) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "findPrimaryAddressByOpportunityId",
                    "PartnershipOpportunity#" + opportunityId);
        }

        // Fix any issues with multiple primary addresses
        fixOpportunityPrimaryAddresses(opportunity, null);

        // Get the primary address (should be only one now)
        List<Address> primaryAddresses = addressRepository.findByPartnershipOpportunityAndIsPrimaryTrue(opportunity);
        if (primaryAddresses.isEmpty()) {
            throw new ResourceNotFoundException("error.business.item_not_found", "Primary address");
        }

        Address primaryAddress = primaryAddresses.getFirst();
        log.info("GDPR: DataAccessed=opportunity.primary_address, FirebaseUID={}, OpportunityID={}, AddressID={}, Purpose=primary_retrieved",
                firebaseUid != null ? firebaseUid : "unknown", opportunityId, primaryAddress.getId());
        return primaryAddress;
    }

    @Transactional
    public Address setAsPrimary(Long addressId) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=setAddressAsPrimary, FirebaseUID={}, AddressID={}, Purpose=primary_designation",
                firebaseUid != null ? firebaseUid : "unknown", addressId);

        Address address = findById(addressId);
        // findById already includes ownership validation

        address.setPrimary(true);
        managePrimaryAddress(address);

        Address updated = addressRepository.save(updateEntityUpdater(address));
        log.info("GDPR: DataModified=address.isPrimary, FirebaseUID={}, AddressID={}, Purpose=primary_status_changed",
                firebaseUid != null ? firebaseUid : "unknown", addressId);
        return updated;
    }

    @Override
    @Transactional
    public Address update(Long id, AddressDtoIn dtoIn) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=updateAddress, FirebaseUID={}, AddressID={}, Purpose=address_modification",
                firebaseUid != null ? firebaseUid : "unknown", id);

        Address existingAddress = findById(id);
        // findById already includes ownership validation

        boolean wasPrimary = existingAddress.isPrimary();
        String oldAddressType = existingAddress.getAddressType();

        // Apply updates from DTO
        existingAddress.setStreet(dtoIn.getStreet());
        existingAddress.setCity(dtoIn.getCity());
        existingAddress.setPostalCode(dtoIn.getPostalCode());
        existingAddress.setCountry(dtoIn.getCountry());
        existingAddress.setState(dtoIn.getState());
        existingAddress.setAdditionalInfo(dtoIn.getAdditionalInfo());
        existingAddress.setAddressType(dtoIn.getAddressType());
        existingAddress.setPrimary(dtoIn.isPrimary());

        // Handle address type changes
        if (!dtoIn.getAddressType().equals(oldAddressType)) {
            manageAddressType(existingAddress);
        }

        // Handle primary flag changes
        if (wasPrimary != dtoIn.isPrimary()) {
            managePrimaryAddress(existingAddress);
        }

        Address updated = addressRepository.save(updateEntityUpdater(existingAddress));
        log.info("GDPR: DataModified=address.all_fields, FirebaseUID={}, AddressID={}, Purpose=user_update_complete",
                firebaseUid != null ? firebaseUid : "unknown", id);
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        String firebaseUid = permissionUtils.getUserId();
        log.warn("GDPR: DELETION Operation=deleteAddress, FirebaseUID={}, AddressID={}, Purpose=user_requested",
                firebaseUid != null ? firebaseUid : "unknown", id);

        Address address = findById(id);
        // findById already includes ownership validation

        boolean wasPrimary = address.isPrimary();

        // Delete the address first
        repository.deleteById(id);

        log.info("GDPR: DELETION_COMPLETE AddressID={}, FirebaseUID={}, Permanent=true",
                id, firebaseUid != null ? firebaseUid : "unknown");

        // If this was a primary address, set a new primary
        if (wasPrimary) {
            if (address.getUser() != null) {
                List<Address> userAddresses = addressRepository.findByUser(address.getUser());
                if (!userAddresses.isEmpty()) {
                    // Check if any address is already primary
                    boolean anyPrimary = userAddresses.stream().anyMatch(Address::isPrimary);
                    if (!anyPrimary) {
                        // Make the most recently updated address primary
                        userAddresses.stream()
                                .max(Comparator.comparing(Address::getLastUpdateTime))
                                .ifPresent(addr -> {
                                    addr.setPrimary(true);
                                    addressRepository.save(addr);
                                });
                    }
                }
            } else if (address.getPartnershipOpportunity() != null) {
                List<Address> opportunityAddresses = addressRepository.findByPartnershipOpportunity(
                        address.getPartnershipOpportunity());
                if (!opportunityAddresses.isEmpty()) {
                    // Check if any address is already primary
                    boolean anyPrimary = opportunityAddresses.stream().anyMatch(Address::isPrimary);
                    if (!anyPrimary) {
                        // Make the most recently updated address primary
                        opportunityAddresses.stream()
                                .max(Comparator.comparing(Address::getLastUpdateTime))
                                .ifPresent(addr -> {
                                    addr.setPrimary(true);
                                    addressRepository.save(addr);
                                });
                    }
                }
            }
        }
    }

    public List<Address> findAddressesByUserIdAndType(Long userId, String addressType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // Validate that the current user is either the owner or an admin
        if (!permissionUtils.isAdmin() && !user.getFirebaseUserId().equals(permissionUtils.getUserId())) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "findAddressesByUserIdAndType",
                    "User#" + userId);
        }

        return addressRepository.findByUserAndAddressType(user, addressType);
    }

    public List<Address> findAddressesByOpportunityIdAndType(Long opportunityId, String addressType) {
        PartnershipOpportunity opportunity = partnershipOpportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Partnership opportunity"));

        // Validate that the current user is either the owner or an admin
        if (!permissionUtils.isAdmin() &&
                !opportunity.getCompany().getFirebaseUserId().equals(permissionUtils.getUserId())) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "findAddressesByOpportunityIdAndType",
                    "PartnershipOpportunity#" + opportunityId);
        }

        return addressRepository.findByPartnershipOpportunityAndAddressType(opportunity, addressType);
    }

    @Transactional
    public Address createAddressForUser(Long userId, AddressDtoIn addressDtoIn) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=createAddressForUser, FirebaseUID={}, UserID={}, Purpose=address_creation",
                firebaseUid != null ? firebaseUid : "unknown", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // Validate that the current user is either the owner or an admin
        if (!permissionUtils.isAdmin() && !user.getFirebaseUserId().equals(permissionUtils.getUserId())) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "createAddressForUser",
                    "User#" + userId);
        }

        // Create address
        Address address = new Address();
        address.setStreet(addressDtoIn.getStreet());
        address.setCity(addressDtoIn.getCity());
        address.setPostalCode(addressDtoIn.getPostalCode());
        address.setCountry(addressDtoIn.getCountry());
        address.setState(addressDtoIn.getState());
        address.setAdditionalInfo(addressDtoIn.getAdditionalInfo());
        address.setAddressType(addressDtoIn.getAddressType());
        address.setPrimary(addressDtoIn.isPrimary());
        address.setUser(user);
        address.setPartnershipOpportunity(null);

        // Handle address type management
        manageAddressType(address);

        // If no primary preference is specified but this is the first address, make it primary
        List<Address> existingAddresses = addressRepository.findByUser(user);
        if (existingAddresses.isEmpty()) {
            address.setPrimary(true);
        } else if (address.isPrimary()) {
            // If this address is explicitly set as primary, handle it
            managePrimaryAddress(address);
        }

        Address saved = addressRepository.save(updateEntityUpdater(address));
        log.info("GDPR: DataCreated=address.all_fields, FirebaseUID={}, UserID={}, AddressID={}, Purpose=user_address_created",
                firebaseUid != null ? firebaseUid : "unknown", userId, saved.getId());
        return saved;
    }

    @Transactional
    public Address createAddressForOpportunity(Long opportunityId, AddressDtoIn addressDtoIn) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=createAddressForOpportunity, FirebaseUID={}, OpportunityID={}, Purpose=address_creation",
                firebaseUid != null ? firebaseUid : "unknown", opportunityId);
        PartnershipOpportunity opportunity = partnershipOpportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Partnership opportunity"));

        // Validate that the current user is either the owner or an admin
        if (!permissionUtils.isAdmin() &&
                !opportunity.getCompany().getFirebaseUserId().equals(permissionUtils.getUserId())) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "createAddressForOpportunity",
                    "PartnershipOpportunity#" + opportunityId);
        }

        // Create address
        Address address = new Address();
        address.setStreet(addressDtoIn.getStreet());
        address.setCity(addressDtoIn.getCity());
        address.setPostalCode(addressDtoIn.getPostalCode());
        address.setCountry(addressDtoIn.getCountry());
        address.setState(addressDtoIn.getState());
        address.setAdditionalInfo(addressDtoIn.getAdditionalInfo());
        address.setAddressType(addressDtoIn.getAddressType());
        address.setPrimary(addressDtoIn.isPrimary());
        address.setUser(null);
        address.setPartnershipOpportunity(opportunity);

        // Handle address type management
        manageAddressType(address);

        // If no primary preference is specified but this is the first address, make it primary
        List<Address> existingAddresses = addressRepository.findByPartnershipOpportunity(opportunity);
        if (existingAddresses.isEmpty()) {
            address.setPrimary(true);
        } else if (address.isPrimary()) {
            // If this address is explicitly set as primary, handle it
            managePrimaryAddress(address);
        }

        Address saved = addressRepository.save(updateEntityUpdater(address));
        log.info("GDPR: DataCreated=address.all_fields, FirebaseUID={}, OpportunityID={}, AddressID={}, Purpose=opportunity_address_created",
                firebaseUid != null ? firebaseUid : "unknown", opportunityId, saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public Address save(Address address) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=saveAddress, FirebaseUID={}, Purpose=address_persistence",
                firebaseUid != null ? firebaseUid : "unknown");

        // Validate ownership based on associated entities
        if (address.getUser() != null) {
            if (!permissionUtils.isAdmin() && !address.getUser().getFirebaseUserId().equals(permissionUtils.getUserId())) {
                throw new InsufficientPermissionsException(
                        "error.auth.insufficient_permissions",
                        permissionUtils.getUserId(),
                        "saveAddress",
                        "Address");
            }
        } else if (address.getPartnershipOpportunities() != null && !address.getPartnershipOpportunities().isEmpty()) {
            // Check if user has permission for any of the associated opportunities
            boolean hasPermission = permissionUtils.isAdmin() ||
                    address.getPartnershipOpportunities().stream().anyMatch(opportunity ->
                            opportunity.getCompany() != null &&
                                    opportunity.getCompany().getFirebaseUserId().equals(permissionUtils.getUserId()));

            if (!hasPermission) {
                throw new InsufficientPermissionsException(
                        "error.auth.insufficient_permissions",
                        permissionUtils.getUserId(),
                        "saveAddress",
                        "Address");
            }
        } else {
            // If neither user nor opportunity is set, only admins can create addresses
            if (!permissionUtils.isAdmin()) {
                throw new InsufficientPermissionsException(
                        "error.auth.insufficient_permissions",
                        permissionUtils.getUserId(),
                        "saveAddress",
                        "Address");
            }
        }

        // Handle address type management
        manageAddressType(address);

        // Check if this is a new address (id is null) or an existing one
        if (address.getId() == null) {
            // For new addresses, if it's the first one for the user/opportunity, make it primary
            if (address.getUser() != null) {
                List<Address> existingAddresses = addressRepository.findByUser(address.getUser());
                if (existingAddresses.isEmpty()) {
                    address.setPrimary(true);
                }
            } else if (address.getPartnershipOpportunities() != null && !address.getPartnershipOpportunities().isEmpty()) {
                // For shared addresses, default to primary
                address.setPrimary(true);
            }
        }

        // Handle primary flag management
        if (address.isPrimary()) {
            managePrimaryAddress(address);
        }

        Address saved = super.save(address);
        log.info("GDPR: DataCreated=address.all_fields, FirebaseUID={}, AddressID={}, Purpose=address_saved",
                firebaseUid != null ? firebaseUid : "unknown", saved.getId());
        return saved;
    }

    /**
     * Resolves an address for a new opportunity with duplicate prevention.
     * - If source is a user address: check for existing copy, create if needed
     * - If source is already an opportunity address: reuse it directly
     *
     * @param addressId The ID of the address to resolve
     * @return An address instance for the opportunity (existing copy or new copy)
     */
    @Transactional
    public Address resolveAddressForNewOpportunity(Long addressId) {
        Address sourceAddress = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Address"));

        // Validate that the user has permission to use this address
        validateOwnership(sourceAddress);

        // Check if source is already an opportunity address (copied or business location)
        if (sourceAddress.getUser() == null) {
            // This is already an opportunity address, reuse it directly
            log.info("Reusing existing opportunity address {}", addressId);
            return sourceAddress;
        }

        // Source is a user address, check if we already copied it
        Address existingCopy = addressRepository.findBySourceAddressIdAndIsCopiedTrue(addressId);

        if (existingCopy != null) {
            log.info("Found existing copied address {} for source user address {}", existingCopy.getId(), addressId);
            return existingCopy;
        }

        // No existing copy found, create a new one from the user address
        log.info("Creating new copy of user address {} for opportunity", addressId);

        Address newAddress = new Address();
        newAddress.setStreet(sourceAddress.getStreet());
        newAddress.setCity(sourceAddress.getCity());
        newAddress.setPostalCode(sourceAddress.getPostalCode());
        newAddress.setCountry(sourceAddress.getCountry());
        newAddress.setState(sourceAddress.getState());
        newAddress.setAdditionalInfo(sourceAddress.getAdditionalInfo());
        newAddress.setAddressType("MAIN");
        newAddress.setPrimary(true);
        newAddress.setUser(null);
        newAddress.setCopied(true);
        newAddress.setSourceAddressId(addressId);

        // Don't save here - let the caller save after setting up associations
        return newAddress;
    }

    /**
     * Simple search for addresses accessible to the current user.
     * Used by the public API search endpoint.
     *
     * @param street     Street to search for (optional, partial match)
     * @param city       City to search for (optional, partial match)
     * @param postalCode Postal code to search for (optional, partial match)
     * @param country    Country to search for (optional, partial match)
     * @return List of addresses that the user can access
     */
    public List<Address> searchReusableAddressesFlexible(String street, String city, String postalCode, String country) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=searchAddresses, FirebaseUID={}, SearchCriteria=[street={},city={},postalCode={},country={}], Purpose=address_search",
                firebaseUid != null ? firebaseUid : "unknown", street, city, postalCode, country);

        log.debug("Searching addresses with street='{}', city='{}', postalCode='{}', country='{}'",
                street, city, postalCode, country);

        // For now, return all addresses the user has access to
        // A more sophisticated search could be implemented later if needed
        List<Address> allAddresses = addressRepository.findAll();

        return allAddresses.stream()
                .filter(addr -> {
                    try {
                        if (addr.getUser() != null) {
                            return permissionUtils.isAdmin() ||
                                    addr.getUser().getFirebaseUserId().equals(permissionUtils.getUserId());
                        } else if (addr.getPartnershipOpportunities() != null && !addr.getPartnershipOpportunities().isEmpty()) {
                            return permissionUtils.isAdmin() ||
                                    addr.getPartnershipOpportunities().stream().anyMatch(opportunity ->
                                            opportunity.getCompany() != null &&
                                                    opportunity.getCompany().getFirebaseUserId().equals(permissionUtils.getUserId()));
                        }
                        return false;
                    } catch (Exception e) {
                        log.warn("Error checking permissions for address {}: {}", addr.getId(), e.getMessage());
                        return false;
                    }
                })
                .filter(addr -> matchesSearchCriteria(addr, street, city, postalCode, country))
                .toList();
    }

    /**
     * Helper method to check if an address matches search criteria.
     */
    private boolean matchesSearchCriteria(Address address, String street, String city, String postalCode, String country) {
        if (street != null && !street.trim().isEmpty()) {
            if (address.getStreet() == null || !address.getStreet().toLowerCase().contains(street.toLowerCase().trim())) {
                return false;
            }
        }

        if (city != null && !city.trim().isEmpty()) {
            if (address.getCity() == null || !address.getCity().toLowerCase().contains(city.toLowerCase().trim())) {
                return false;
            }
        }

        if (postalCode != null && !postalCode.trim().isEmpty()) {
            if (address.getPostalCode() == null || !address.getPostalCode().toLowerCase().contains(postalCode.toLowerCase().trim())) {
                return false;
            }
        }

        if (country != null && !country.trim().isEmpty()) {
            if (address.getCountry() == null || !address.getCountry().toLowerCase().contains(country.toLowerCase().trim())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Retrieves a paginated list of addresses as DTOs with all conversions done within transaction.
     * This prevents LazyInitializationException by ensuring all DTO mappings happen inside @Transactional.
     *
     * @param pageable Pagination parameters
     * @param filters  Filter parameters
     * @return Page of AddressDtoOut with all lazy relationships properly loaded
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> Page<DTOOUT> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters) {
        // Get entities with proper pagination and filtering
        Page<Address> page = getDataPagedAndFiltered(pageable, filters);

        // Convert to DTOs within transaction boundary to avoid LazyInitializationException
        @SuppressWarnings("unchecked")
        Page<DTOOUT> dtoPage = (Page<DTOOUT>) page.map(address -> {
            AddressDtoOut dto = new AddressDtoOut();

            // Map basic fields
            dto.setId(address.getId());
            dto.setStreet(address.getStreet());
            dto.setCity(address.getCity());
            dto.setPostalCode(address.getPostalCode());
            dto.setCountry(address.getCountry());
            dto.setState(address.getState());
            dto.setAdditionalInfo(address.getAdditionalInfo());
            dto.setAddressType(address.getAddressType());
            dto.setPrimary(address.isPrimary());
            dto.setCreatedTime(address.getCreatedTime());
            dto.setLastUpdateTime(address.getLastUpdateTime());
            dto.setUpdaterId(address.getUpdaterId());
            dto.setCopied(address.isCopied());
            dto.setSourceAddressId(address.getSourceAddressId());

            // Map user association safely
            if (address.getUser() != null) {
                dto.setUserId(address.getUser().getId());
            }

            // Map partnership opportunity safely - note it's a single opportunity now
            if (address.getPartnershipOpportunity() != null) {
                dto.setPartnershipOpportunityId(address.getPartnershipOpportunity().getId());
            }

            return dto;
        });

        return dtoPage;
    }

    /**
     * Converts an Address entity to AddressDtoOut within a transaction.
     * This method ensures all lazy collections are properly handled.
     *
     * @param entity the Address entity to convert
     * @return AddressDtoOut with all data properly mapped
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> DTOOUT toDto(Address entity) {
        if (entity == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        DTOOUT result = (DTOOUT) modelMapper.map(entity, AddressDtoOut.class);
        return result;
    }

    /**
     * Creates a new Address from DTO and returns as DTO.
     * All conversions happen within transaction boundary.
     *
     * @param dto The input DTO
     * @return The created entity as DTO
     */
    @Override
    @Transactional
    public <DTOOUT> DTOOUT createFromDtoAsDto(AddressDtoIn dto) {
        Address entity = modelMapper.map(dto, Address.class);
        Address saved = save(entity);
        return toDto(saved);
    }

    /**
     * Converts a list of Address entities to AddressDtoOut list within a transaction.
     * This method ensures all lazy collections are properly handled for each entity.
     *
     * @param entities the list of Address entities to convert
     * @return List of AddressDtoOut with all data properly mapped
     */
    @Transactional(readOnly = true)
    public List<AddressDtoOut> toDtoList(List<Address> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        List<AddressDtoOut> result = new ArrayList<>();
        for (Address entity : entities) {
            AddressDtoOut dto = this.<AddressDtoOut>toDto(entity);
            result.add(dto);
        }
        return result;
    }

    /**
     * Finds addresses by user ID and returns them as DTOs.
     * All conversions happen within the transaction boundary.
     *
     * @param userId the user ID
     * @return List of AddressDtoOut
     */
    @Transactional(readOnly = true)
    public List<AddressDtoOut> findAddressesByUserIdAsDto(Long userId) {
        List<Address> addresses = findAddressesByUserId(userId);
        return toDtoList(addresses);
    }

    /**
     * Finds addresses by opportunity ID and returns them as DTOs.
     * All conversions happen within the transaction boundary.
     *
     * @param opportunityId the opportunity ID
     * @return List of AddressDtoOut
     */
    @Transactional(readOnly = true)
    public List<AddressDtoOut> findAddressesByOpportunityIdAsDto(Long opportunityId) {
        List<Address> addresses = findAddressesByOpportunityId(opportunityId);
        return toDtoList(addresses);
    }

    /**
     * Searches for reusable addresses and returns them as DTOs.
     * All conversions happen within the transaction boundary.
     *
     * @param street     Street to search for (optional)
     * @param city       City to search for (optional)
     * @param postalCode Postal code to search for (optional)
     * @param country    Country to search for (optional)
     * @return List of AddressDtoOut
     */
    @Transactional(readOnly = true)
    public List<AddressDtoOut> searchReusableAddressesFlexibleAsDto(String street, String city, String postalCode, String country) {
        List<Address> addresses = searchReusableAddressesFlexible(street, city, postalCode, country);
        return toDtoList(addresses);
    }

    /**
     * Finds primary address by user ID and returns it as DTO.
     *
     * @param userId the user ID
     * @return AddressDtoOut
     */
    @Transactional(readOnly = true)
    public AddressDtoOut findPrimaryAddressByUserIdAsDto(Long userId) {
        Address address = findPrimaryAddressByUserId(userId);
        return toDto(address);
    }

    /**
     * Finds primary address by opportunity ID and returns it as DTO.
     *
     * @param opportunityId the opportunity ID
     * @return AddressDtoOut
     */
    @Transactional(readOnly = true)
    public AddressDtoOut findPrimaryAddressByOpportunityIdAsDto(Long opportunityId) {
        Address address = findPrimaryAddressByOpportunityId(opportunityId);
        return toDto(address);
    }

    /**
     * Sets an address as primary and returns it as DTO.
     *
     * @param addressId the address ID
     * @return AddressDtoOut
     */
    @Transactional
    public AddressDtoOut setAsPrimaryAsDto(Long addressId) {
        Address address = setAsPrimary(addressId);
        return toDto(address);
    }

    /**
     * Creates address for user and returns it as DTO.
     *
     * @param userId       the user ID
     * @param addressDtoIn the address input DTO
     * @return AddressDtoOut
     */
    @Transactional
    public AddressDtoOut createAddressForUserAsDto(Long userId, AddressDtoIn addressDtoIn) {
        Address address = createAddressForUser(userId, addressDtoIn);
        return toDto(address);
    }

    /**
     * Creates address for opportunity and returns it as DTO.
     *
     * @param opportunityId the opportunity ID
     * @param addressDtoIn  the address input DTO
     * @return AddressDtoOut
     */
    @Transactional
    public AddressDtoOut createAddressForOpportunityAsDto(Long opportunityId, AddressDtoIn addressDtoIn) {
        Address address = createAddressForOpportunity(opportunityId, addressDtoIn);
        return toDto(address);
    }

    /**
     * Finds addresses by user ID and type, returns them as DTOs.
     *
     * @param userId      the user ID
     * @param addressType the address type
     * @return List of AddressDtoOut
     */
    @Transactional(readOnly = true)
    public List<AddressDtoOut> findAddressesByUserIdAndTypeAsDto(Long userId, String addressType) {
        List<Address> addresses = findAddressesByUserIdAndType(userId, addressType);
        return toDtoList(addresses);
    }

    /**
     * Finds addresses by opportunity ID and type, returns them as DTOs.
     *
     * @param opportunityId the opportunity ID
     * @param addressType   the address type
     * @return List of AddressDtoOut
     */
    @Transactional(readOnly = true)
    public List<AddressDtoOut> findAddressesByOpportunityIdAndTypeAsDto(Long opportunityId, String addressType) {
        List<Address> addresses = findAddressesByOpportunityIdAndType(opportunityId, addressType);
        return toDtoList(addresses);
    }
}