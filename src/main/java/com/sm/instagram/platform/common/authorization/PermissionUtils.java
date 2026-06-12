package com.sm.instagram.platform.common.authorization;

import com.google.firebase.auth.FirebaseAuthException;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionUtils {
    private final UserManagementService userManagementService;
    private final UserRepository userRepository;

    public boolean hasRole(Permission role) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            return authorities.stream()
                    .anyMatch(auth -> auth.getAuthority().equals(role.toString()));
        }
        return false;
    }


    public boolean isAdmin() {
        return hasRole(Permission.ADMIN);
    }

    public boolean isInfluencer() {
        return hasRole(Permission.INFLUENCER);
    }

    public boolean isCompany() {
        return hasRole(Permission.COMPANY);
    }

    public boolean isUserOwner(User user) {
        if (user == null) {
            return false;
        }
        String currentUserId = getUserId();
        return currentUserId != null && Objects.equals(user.getFirebaseUserId(), currentUserId);
    }

    public boolean isUserOwner(AppliedOpportunity ao) {
        if (ao == null || ao.getInfluencer() == null
                || SecurityContextHolder.getContext().getAuthentication() == null) {
            return false;
        }
        return SecurityContextHolder.getContext().getAuthentication().getName()
                .equals(ao.getInfluencer().getFirebaseUserId());
    }

    public boolean isUserOwner(UserSocialConnection usc) {
        if (usc == null || usc.getUser() == null
                || SecurityContextHolder.getContext().getAuthentication() == null) {
            return false;
        }
        return SecurityContextHolder.getContext().getAuthentication().getName()
                .equals(usc.getUser().getFirebaseUserId());
    }

    public boolean isUserOwner(PartnershipOpportunity po) {
        if (po == null || po.getCompany() == null
                || SecurityContextHolder.getContext().getAuthentication() == null) {
            return false;
        }
        return SecurityContextHolder.getContext().getAuthentication().getName()
                .equals(po.getCompany().getFirebaseUserId());
    }

    public boolean isUserOwner(String userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (userId == null || auth == null || auth.getName() == null) {
            return false;
        }
        return auth.getName()
                .equals(userId);
    }

    @Deprecated
    public boolean canEditOpportunity(AppliedOpportunity opportunity) {
        // This method is deprecated. Use canViewAppliedOpportunity or canEditAppliedOpportunity instead
        if (opportunity == null) {
            return false;
        }
        return isAdmin() || isUserOwner(opportunity);
    }

    public boolean canEditOpportunity(PartnershipOpportunity opportunity) {
        if (opportunity == null) {
            return false;
        }
        return isAdmin() || isUserOwner(opportunity);
    }

    public boolean canViewOpportunity(PartnershipOpportunity opportunity) {
        if (opportunity == null) {
            return false;
        }

        if (isAdmin()) {
            return true;
        }

        // Companies can view ALL opportunities - check both role and user type for reliability
        // Edit restrictions are handled elsewhere
        if (isCompany() || isCurrentUserTypeCompany()) {
            return true;
        }

        // Influencers can only view active opportunities
        if (isInfluencer() || isCurrentUserTypeInfluencer()) {
            return opportunity.isActive();
        }

        return false;
    }

    public boolean canEditOpportunity(String userId) {
        return isAdmin() || isUserOwner(userId);
    }

    /**
     * Returns the Firebase UID of the currently authenticated user.
     * Returns null if user is not authenticated or is anonymous.
     *
     * @return Firebase UID or null for unauthenticated/anonymous users
     */
    public String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Return null if no authentication or anonymous authentication
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        // Check for anonymous authentication token (Spring Security default for unauthenticated)
        if (auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return auth.getName();
    }

    public boolean isUserTypeInfluencer(User user) {
        return user.getUserType().equals(UserType.INFLUENCER);
    }

    public boolean isUserTypeCompany(User user) {
        return user.getUserType().equals(UserType.COMPANY);
    }

    /**
     * Checks if the current authenticated user has the specified user type.
     * This method retrieves the current user and checks their type directly,
     * regardless of their role/permission status.
     */
    public boolean isCurrentUserType(UserType userType) {
        try {
            String currentUserId = getUserId();
            if (currentUserId == null) {
                return false;
            }

            Optional<User> currentUser = userRepository.findByFirebaseUserId(currentUserId);
            return currentUser.isPresent() && currentUser.get().getUserType().equals(userType);
        } catch (Exception e) {
            log.error("Error checking current user type: {}", e.getMessage());
            return false;
        }
    }

    public boolean isCurrentUserTypeInfluencer() {
        return isCurrentUserType(UserType.INFLUENCER);
    }

    public boolean isCurrentUserTypeCompany() {
        return isCurrentUserType(UserType.COMPANY);
    }

    public void changeUserRole(User user) {
        changeUserRole(user.getFirebaseUserId(), user.getAccountStatus(), user.getUserType());
    }

    public void changeUserRole(String firebaseUuid, AccountStatus newStatus, UserType userType) {
        try {
            // GDPR logging for role changes
            log.info("GDPR: Operation=user_role_change, FirebaseUID={}, NewRole={}, Status={}, Purpose=access_control, LegalBasis=contract",
                firebaseUuid, userType, newStatus);
            
            if (newStatus.equals(AccountStatus.ACTIVE)) {
                if (userType.equals(UserType.INFLUENCER)) {
                    userManagementService.setUserClaims(firebaseUuid, Collections.singletonList(Permission.INFLUENCER));
                } else if (userType.equals(UserType.ADMIN)) {
                    userManagementService.setUserClaims(firebaseUuid, Collections.singletonList(Permission.ADMIN));
                } else if (userType.equals(UserType.COMPANY)) {
                    userManagementService.setUserClaims(firebaseUuid, Collections.singletonList(Permission.COMPANY));
                }
            } else
                userManagementService.setUserClaims(firebaseUuid, Collections.emptyList());

        } catch (FirebaseAuthException e) {
            // Handle case where user exists in DB but not in Firebase (e.g., test users)
            if (e.getMessage() != null && e.getMessage().contains("USER_NOT_FOUND")) {
                log.warn("GDPR: User {} exists in database but not in Firebase. " +
                           "This is expected for test users or users created directly in DB. " +
                           "Skipping Firebase role update.", firebaseUuid);
                // Don't throw exception - allow the operation to continue
                // The user's role in the database will still be updated
            } else {
            // For other Firebase errors, translate to user-facing error
            log.error("couldn't change role for user {}. Error: {}", firebaseUuid, e.getMessage());
            throw new com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException("error.permission.role_change_failed");
            }
        }
    }

    /**
     * Checks if the current user can view an applied opportunity.
     * - Admins can view all opportunities
     * - Influencers can view their own applications
     * - Companies can view applications to their partnership opportunities
     */
    public boolean canViewAppliedOpportunity(AppliedOpportunity opportunity) {
        if (opportunity == null) {
            return false;
        }
        
        // Admin can view everything
        if (isAdmin()) {
            return true;
        }
        
        // Influencer can view their own applications
        if (isInfluencer() && isUserOwner(opportunity)) {
            return true;
        }
        
        // Company can view applications to their opportunities
        if (isCompany() && opportunity.getPartnershipOpportunity() != null &&
            isUserOwner(opportunity.getPartnershipOpportunity())) {
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the current user can edit an applied opportunity.
     * This is more restrictive than view permissions.
     * - Admins can edit all opportunities
     * - Influencers can edit their own applications (with restrictions)
     * - Companies have limited edit rights (handled at operation level)
     */
    public boolean canEditAppliedOpportunity(AppliedOpportunity opportunity) {
        if (opportunity == null) {
            return false;
        }
        
        // Admin can edit everything
        if (isAdmin()) {
            return true;
        }
        
        // Only influencer owner can edit their own applications
        if (isInfluencer() && isUserOwner(opportunity)) {
            return true;
        }
        
        // Companies don't have general edit permissions
        // Their specific permissions are checked at operation level
        return false;
    }

    /**
     * Checks if the current user can update the status of an applied opportunity.
     * Different roles have different allowed status transitions.
     */
    public boolean canUpdateAppliedOpportunityStatus(AppliedOpportunity opportunity, 
                                                     com.sm.instagram.platform.appliedopportunities.OpportunityStatus currentStatus,
                                                     boolean accept) {
        if (opportunity == null) {
            return false;
        }
        
        // Admin can update any status
        if (isAdmin()) {
            return true;
        }
        
        // Influencer can respond to company decisions and submit content
        if (isInfluencer() && isUserOwner(opportunity)) {
            return isInfluencerAllowedStatusTransition(currentStatus);
        }
        
        // Company can handle most transitions except influencer responses
        if (isCompany() && isUserOwner(opportunity.getPartnershipOpportunity())) {
            return isCompanyAllowedStatusTransition(currentStatus);
        }
        
        return false;
    }

    /**
     * Checks if an influencer is allowed to transition from the current status
     */
    private boolean isInfluencerAllowedStatusTransition(com.sm.instagram.platform.appliedopportunities.OpportunityStatus currentStatus) {
        // Influencers can respond to company acceptance/rejection and resubmit content
        return currentStatus == com.sm.instagram.platform.appliedopportunities.OpportunityStatus.ACCEPTED_BY_COMPANY || 
               currentStatus == com.sm.instagram.platform.appliedopportunities.OpportunityStatus.ACCEPTED_BY_INFLUENCER ||
               currentStatus == com.sm.instagram.platform.appliedopportunities.OpportunityStatus.CONTENT_REJECTED;
    }

    /**
     * Checks if a company is allowed to transition from the current status
     */
    private boolean isCompanyAllowedStatusTransition(com.sm.instagram.platform.appliedopportunities.OpportunityStatus currentStatus) {
        // Companies cannot transition from their own decisions or influencer acceptance
        return currentStatus != com.sm.instagram.platform.appliedopportunities.OpportunityStatus.ACCEPTED_BY_COMPANY && 
               currentStatus != com.sm.instagram.platform.appliedopportunities.OpportunityStatus.REJECTED_BY_COMPANY &&
               currentStatus != com.sm.instagram.platform.appliedopportunities.OpportunityStatus.ACCEPTED_BY_INFLUENCER;
    }

    /**
     * Checks if the current user can rate in an applied opportunity
     * @param opportunity The applied opportunity
     * @param ratingType "influencer" for influencer rating company, "company" for company rating influencer
     */
    public boolean canRateInAppliedOpportunity(AppliedOpportunity opportunity, String ratingType) {
        if (opportunity == null || ratingType == null) {
            return false;
        }
        
        // Admin can rate anything
        if (isAdmin()) {
            return true;
        }
        
        // Influencer can rate the company
        if ("influencer".equals(ratingType) && isInfluencer() && isUserOwner(opportunity)) {
            return true;
        }
        
        // Company can rate the influencer
        if ("company".equals(ratingType) && isCompany() && 
            isUserOwner(opportunity.getPartnershipOpportunity())) {
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the current user owns the company associated with an applied opportunity
     */
    public boolean isCompanyOwnerOfAppliedOpportunity(AppliedOpportunity opportunity) {
        if (opportunity == null || opportunity.getPartnershipOpportunity() == null || 
            opportunity.getPartnershipOpportunity().getCompany() == null) {
            return false;
        }
        
        return isUserOwner(opportunity.getPartnershipOpportunity().getCompany().getFirebaseUserId());
    }
}