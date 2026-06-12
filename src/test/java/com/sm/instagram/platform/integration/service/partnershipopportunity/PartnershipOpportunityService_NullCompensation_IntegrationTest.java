package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityDtoOut;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Regression tests for the influencer "browse campaigns" 400 (BUG-22, 2026-05-31).
 *
 * <p><b>Root cause.</b> {@code PartnershipOpportunity.compensationAmountMin/Max} were declared as
 * primitive {@code int}, while the DB columns ({@code compensation_amount_min/max INTEGER},
 * Liquibase {@code 002-tables.sql}) are nullable. A legacy row with {@code compensation_amount_max
 * IS NULL} crashed Hibernate hydration with
 * {@code "Null value was assigned to a property [...] of primitive type"} ->
 * {@code JpaSystemException} -> HTTP 400, breaking
 * {@code GET /api/partnership-opportunity/paged} for <i>every</i> caller (role-independent;
 * the bug was reported by an influencer but reproduced as a company too).
 *
 * <p><b>Fix.</b> Box the two fields to {@code Integer} so nullable rows hydrate, and coalesce
 * {@code null -> 0} at the DTO boundaries so the {@code int}-typed DtoOut contract is preserved.
 *
 * <p>Both halves are required: boxing alone fixes the <i>load</i> crash but then mapping a null
 * into the primitive-{@code int} DtoOut setter throws a <i>secondary</i> NPE — covered by
 * {@link #pagedBrowseToleratesNullCompensationMax()}.
 */
@DisplayName("PartnershipOpportunityService - Null compensation (BUG-22 regression)")
class PartnershipOpportunityService_NullCompensation_IntegrationTest
        extends PartnershipOpportunityServiceIntegrationTestBase {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Forces a legacy-shaped row: the column is nullable, but the entity could not represent it
     * while the field was a primitive. The native UPDATE bypasses the entity setter.
     */
    private Long saveOpportunityThenNullOutMaxCompensation() {
        PartnershipOpportunity saved = saveOpportunity(createTestOpportunity(testCompany));
        Long id = saved.getId();
        entityManager.createNativeQuery(
                        "UPDATE partnership_opportunity SET compensation_amount_max = NULL WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    @Test
    @DisplayName("Hydrating a row whose compensation_amount_max is NULL does not crash Hibernate")
    void hydratesOpportunityWithNullCompensationMax() {
        authenticateAs(testCompany);
        Long id = saveOpportunityThenNullOutMaxCompensation();

        Optional<PartnershipOpportunity> reloaded = partnershipOpportunityRepository.findById(id);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCompensationAmountMax()).isNull();
    }

    @Test
    @DisplayName("Influencer paged browse returns the NULL-compensation row instead of throwing 400")
    void pagedBrowseToleratesNullCompensationMax() {
        authenticateAs(testCompany);
        Long id = saveOpportunityThenNullOutMaxCompensation();

        // Reproduce the reported actor: an influencer browsing active campaigns.
        authenticateAs(testInfluencer);

        assertThatNoException().isThrownBy(() -> {
            // Mirror the exact query the FE/user sends (?page=0&size=12&sort=createdTime,desc).
            // The newest row is the one we just created, so it lands on page 0 deterministically
            // (unsorted pagination is non-deterministic against the seeded demo opportunities).
            Page<PartnershipOpportunityDtoOut> page = partnershipOpportunityService
                    .getDataPagedAndFilteredAsDtos(
                            PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "createdTime")),
                            new HashMap<>(), Locale.ENGLISH);

            PartnershipOpportunityDtoOut row = page.getContent().stream()
                    .filter(dto -> id.equals(dto.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "NULL-compensation opportunity " + id + " missing from influencer browse"));

            // null max compensation coalesced to 0 at the DTO boundary (int contract preserved)
            assertThat(row.getCompensationAmountMax()).isZero();
        });
    }
}
