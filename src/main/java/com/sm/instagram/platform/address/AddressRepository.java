package com.sm.instagram.platform.address;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Address entities.
 */
@Repository
public interface AddressRepository extends BaseRepository<Address, Long> {

    /**
     * Find all addresses associated with a specific user.
     *
     * @param user the user
     * @return a list of addresses
     */
    List<Address> findByUser(User user);

    /**
     * Find all addresses associated with a specific partnership opportunity.
     * Since we now have Many-to-One relationship (PartnershipOpportunity -> Address),
     * we need to query differently.
     *
     * @param partnershipOpportunity the partnership opportunity
     * @return a list of addresses (typically just one due to Many-to-One)
     */
    @Query("SELECT po.address FROM PartnershipOpportunity po WHERE po = :opportunity AND po.address IS NOT NULL")
    List<Address> findByPartnershipOpportunity(@Param("opportunity") PartnershipOpportunity partnershipOpportunity);

    /**
     * Find primary addresses for a specific user.
     *
     * @param user the user
     * @return a list of primary addresses
     */
    List<Address> findByUserAndIsPrimaryTrue(User user);

    /**
     * Find primary addresses for a specific partnership opportunity.
     * Since we now have Many-to-One relationship, we query the opportunity's address directly.
     *
     * @param partnershipOpportunity the partnership opportunity
     * @return a list of primary addresses (typically just one)
     */
    @Query("SELECT po.address FROM PartnershipOpportunity po WHERE po = :opportunity AND po.address IS NOT NULL AND po.address.isPrimary = true")
    List<Address> findByPartnershipOpportunityAndIsPrimaryTrue(@Param("opportunity") PartnershipOpportunity partnershipOpportunity);

    /**
     * Find all addresses of a specific type for a user.
     *
     * @param user        the user
     * @param addressType the address type
     * @return a list of addresses
     */
    List<Address> findByUserAndAddressType(User user, String addressType);

    /**
     * Find all addresses of a specific type for a partnership opportunity.
     * Since we now have Many-to-One relationship, we query the opportunity's address directly.
     *
     * @param partnershipOpportunity the partnership opportunity
     * @param addressType            the address type
     * @return a list of addresses (typically just one)
     */
    @Query("SELECT po.address FROM PartnershipOpportunity po WHERE po = :opportunity AND po.address IS NOT NULL AND po.address.addressType = :addressType")
    List<Address> findByPartnershipOpportunityAndAddressType(@Param("opportunity") PartnershipOpportunity partnershipOpportunity, @Param("addressType") String addressType);

    /**
     * Find existing copied address by source address ID.
     * This is used to prevent duplicate copies of the same source address.
     *
     * @param sourceAddressId the ID of the source address that was copied
     * @return the existing copied address if found, null otherwise
     */
    Address findBySourceAddressIdAndIsCopiedTrue(Long sourceAddressId);

}