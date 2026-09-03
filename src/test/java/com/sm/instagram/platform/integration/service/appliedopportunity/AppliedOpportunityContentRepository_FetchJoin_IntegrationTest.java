package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityContent;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityContentRepository;
import com.sm.instagram.platform.appliedopportunities.ContentApprovalStatus;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.contenttype.ContentTypeRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the review-page 500 caught by the live Chrome click-through
 * 2026-06-12: GET /applied-opportunity/content/applied-opportunity/{id} threw
 * LazyInitializationException (ContentType proxy, no session) because the
 * controller maps the DTO outside any transaction and the derived query did
 * not fetch-join contentType. Same defect class as findByApprovalStatus
 * (fixed 2026-06-10).
 *
 * Hibernate.isInitialized() is the session-independent oracle: a fetch-joined
 * relation is a real entity even while the test transaction is still open, a
 * lazy proxy is not — so this test fails on the un-joined query without
 * having to reproduce the out-of-session mapping itself.
 */
class AppliedOpportunityContentRepository_FetchJoin_IntegrationTest
        extends AppliedOpportunityServiceIntegrationTestBase {

    @Autowired
    private AppliedOpportunityContentRepository contentRepository;

    @Autowired
    private ContentTypeRepository contentTypeRepository;

    @Autowired
    private EntityManager entityManager;

    private AppliedOpportunityContent savedContentFor(AppliedOpportunity application) {
        ContentType contentType = contentTypeRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    ContentType ct = new ContentType();
                    ct.setName("FetchJoin IT type");
                    return contentTypeRepository.save(ct);
                });
        AppliedOpportunityContent content = new AppliedOpportunityContent();
        content.setAppliedOpportunity(application);
        content.setContentType(contentType);
        content.setContentCount(1);
        content.setSubmissionDate(LocalDateTime.now());
        content.setApprovalStatus(ContentApprovalStatus.PENDING);
        return contentRepository.save(content);
    }

    @Test
    @DisplayName("findByAppliedOpportunityId fetch-joins contentType (review-page 500 regression)")
    void findByAppliedOpportunityId_initializesContentType() {
        AppliedOpportunity application = createAppliedOpportunityAtStatus(
                testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        savedContentFor(application);
        entityManager.flush();
        entityManager.clear(); // force fresh load — no first-level-cache warm proxies

        List<AppliedOpportunityContent> result =
                contentRepository.findByAppliedOpportunityId(application.getId());

        assertThat(result).isNotEmpty();
        assertThat(Hibernate.isInitialized(result.get(0).getContentType()))
                .as("contentType must be fetch-joined — the DTO mapper reads its name outside any session")
                .isTrue();
        assertThat(result.get(0).getContentType().getName()).isNotBlank();
    }

    @Test
    @DisplayName("findByAppliedOpportunityIdAndApprovalStatus fetch-joins contentType too")
    void findByAppliedOpportunityIdAndStatus_initializesContentType() {
        AppliedOpportunity application = createAppliedOpportunityAtStatus(
                testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        savedContentFor(application);
        entityManager.flush();
        entityManager.clear();

        List<AppliedOpportunityContent> result =
                contentRepository.findByAppliedOpportunityIdAndApprovalStatus(
                        application.getId(), ContentApprovalStatus.PENDING);

        assertThat(result).isNotEmpty();
        assertThat(Hibernate.isInitialized(result.get(0).getContentType())).isTrue();
    }

    @Test
    @DisplayName("findByIdWithRelationships fetch-joins contentType (GET /content/{id} 500 regression)")
    void findByIdWithRelationships_initializesContentType() {
        AppliedOpportunity application = createAppliedOpportunityAtStatus(
                testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        AppliedOpportunityContent saved = savedContentFor(application);
        entityManager.flush();
        entityManager.clear();

        Optional<AppliedOpportunityContent> result =
                contentRepository.findByIdWithRelationships(saved.getId());

        assertThat(result).isPresent();
        assertThat(Hibernate.isInitialized(result.get().getContentType()))
                .as("contentType must be fetch-joined — getContentById maps the DTO in the controller, outside any session")
                .isTrue();
        assertThat(Hibernate.isInitialized(result.get().getAppliedOpportunity()))
                .as("the permission chain must be fetch-joined too")
                .isTrue();
        assertThat(result.get().getContentType().getName()).isNotBlank();
    }

    @Test
    @DisplayName("paged findAll(Specification, Pageable) fetch-joins contentType via @EntityGraph (paged endpoint 500 regression)")
    void pagedFindAll_initializesContentType() {
        AppliedOpportunity application = createAppliedOpportunityAtStatus(
                testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        savedContentFor(application);
        entityManager.flush();
        entityManager.clear();

        Specification<AppliedOpportunityContent> spec = (root, query, cb) ->
                cb.equal(root.get("appliedOpportunity").get("id"), application.getId());
        Page<AppliedOpportunityContent> page =
                contentRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getContent()).isNotEmpty();
        assertThat(Hibernate.isInitialized(page.getContent().get(0).getContentType()))
                .as("@EntityGraph must initialize contentType — the paged endpoint maps DTOs in the controller, outside any session")
                .isTrue();
    }
}
