package com.sm.instagram.platform.activecooperations;

import com.fasterxml.jackson.annotation.JsonView;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.function.Function;

/**
 * Read-side projection of an {@link AppliedOpportunity} as it appears in the
 * "active cooperations" panel. Fields are decorated with Jackson views so the
 * same object can drive several screens with different visibility rules.
 *
 * <p>Visibility groups:
 * <ul>
 *   <li>{@link Views.Basic} — identity + status (always emitted).</li>
 *   <li>{@link Views.Ratings} — influencer profile fields used to render
 *       rating tiles.</li>
 *   <li>{@link Views.Registration} — extra detail visible only on the
 *       registration / detail page.</li>
 *   <li>{@link Views.InProgress_CompanyView} / {@link Views.InProgress_InfluencerView}
 *       — role-specific fields on the in-progress screen.</li>
 * </ul>
 *
 * <p><b>Field order is part of the API.</b> {@code @AllArgsConstructor} is
 * exercised by tests using positional arguments, so the declared order below
 * must remain stable; introduce new fields at the end and prefer the no-arg
 * constructor + setters for any new call site.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CoopDto {

    private static final String INSTAGRAM_PLATFORM_NAME = "Instagram";

    @JsonView(Views.Basic.class)
    private Long id;

    @JsonView({Views.Ratings.class, Views.InProgress_CompanyView.class})
    private String influencerFirstName;

    @JsonView({Views.Ratings.class, Views.InProgress_CompanyView.class})
    private String influencerLastName;

    @JsonView(Views.InProgress_InfluencerView.class)
    private String companyName;

    @JsonView(Views.Ratings.class)
    private String influencerEmail;

    @JsonView(Views.Registration.class)
    private String influencerInstagramId;

    @JsonView(Views.Registration.class)
    private Integer followersAmount;

    @JsonView({Views.Ratings.class, Views.InProgress_CompanyView.class})
    private String influencerAvatarUrl;

    @JsonView(Views.InProgress_InfluencerView.class)
    private String companyAvatarUrl;

    @JsonView(Views.Basic.class)
    private Long appliedOpportunityId;

    @JsonView(Views.Basic.class)
    @Enumerated(EnumType.STRING)
    private OpportunityStatus appliedOpportunityStatus;

    @JsonView(Views.Basic.class)
    private String partnershipOpportunityTitle;

    @JsonView(Views.Registration.class)
    private String appliedOpportunityNote;

    @JsonView(Views.Basic.class)
    @Enumerated(EnumType.STRING)
    private RateStatus influencerRateStatus;

    @JsonView(Views.Basic.class)
    @Enumerated(EnumType.STRING)
    private RateStatus companyRateStatus;

    @JsonView(Views.Registration.class)
    private Long positiveRatesAmount;

    private Long negativeRatesAmount;

    /**
     * Build a {@link CoopDto} from an {@link AppliedOpportunity} aggregate.
     *
     * <p>The Instagram connection lookup is intentionally tolerant: an
     * influencer without a connected Instagram account simply gets nullable /
     * zero values rather than triggering an error, because the panel still
     * needs to render the row (admins can rescue the data by hand).
     *
     * <p>Aggregated rating counts are placeholders — they will be wired to a
     * dedicated query in a follow-up; until then the UI shows zeroes which is
     * what the existing endpoint already returns.
     */
    public static CoopDto mapToInfluencerCoopDto(AppliedOpportunity ao) {
        User influencer = ao.getInfluencer();
        PartnershipOpportunity partnership = ao.getPartnershipOpportunity();
        User company = partnership.getCompany();

        CoopDto dto = new CoopDto();
        dto.setId(ao.getId());
        dto.setInfluencerFirstName(influencer.getFirstName());
        dto.setInfluencerLastName(influencer.getLastName());
        dto.setCompanyName(company.getName());
        dto.setInfluencerEmail(influencer.getEmail());
        dto.setInfluencerInstagramId(
                instagramField(influencer, UserSocialConnection::getSocialUserId, null));
        dto.setFollowersAmount(
                instagramField(influencer, UserSocialConnection::getFollowersCount, 0));
        dto.setInfluencerAvatarUrl(
                instagramField(influencer, UserSocialConnection::getProfilePictureUrl, null));
        dto.setCompanyAvatarUrl(company.getProfilePicture());
        dto.setAppliedOpportunityId(ao.getId());
        dto.setAppliedOpportunityStatus(ao.getOpportunityStatus());
        dto.setPartnershipOpportunityTitle(partnership.getTitle());
        dto.setAppliedOpportunityNote(ao.getNote());
        dto.setInfluencerRateStatus(ao.getRateStatus());
        dto.setCompanyRateStatus(ao.getCompanyRateStatus());

        dto.setPositiveRatesAmount(0L);
        dto.setNegativeRatesAmount(0L);
        return dto;
    }

    private static <T> T instagramField(User influencer,
                                        Function<UserSocialConnection, T> extractor,
                                        T fallback) {
        return influencer.getSocialConnections().stream()
                .filter(conn -> INSTAGRAM_PLATFORM_NAME.equals(conn.getPlatform().getName()))
                .findFirst()
                .map(extractor)
                .orElse(fallback);
    }
}
