package com.sm.instagram.platform.integration.service.supportticket;

import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.support.ticket.dtos.SupportTicketDtoOut;
import com.sm.instagram.platform.support.ticket.models.SupportTicket;
import com.sm.instagram.platform.support.ticket.models.TicketCategory;
import com.sm.instagram.platform.support.ticket.models.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for SupportTicketService search and status operations.
 */
@DisplayName("SupportTicketService Search and Status Operations")
class SupportTicketService_Search_IntegrationTest extends SupportTicketServiceIntegrationTestBase {

    @Nested
    @DisplayName("getTicketByReferenceAndEmail()")
    class GetTicketByReferenceAndEmail {

        @Test
        @DisplayName("returns ticket when reference and email match")
        void returnsTicketWhenReferenceAndEmailMatch() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");

            SupportTicketDtoOut result = supportTicketService.getTicketByReferenceAndEmail(
                    ticket.getTicketReference(), email);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(ticket.getId());
            assertThat(result.getContactEmail()).isEqualTo(email);
            assertThat(result.getSubject()).isEqualTo("Subject");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for wrong email")
        void throwsResourceNotFoundForWrongEmail() {
            String correctEmail = generateTestEmail();
            String wrongEmail = generateTestEmail();
            SupportTicket ticket = createTicket(correctEmail, "Subject", "Description");

            assertThatThrownBy(() -> supportTicketService.getTicketByReferenceAndEmail(
                    ticket.getTicketReference(), wrongEmail))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for invalid reference")
        void throwsResourceNotFoundForInvalidReference() {
            String email = generateTestEmail();

            assertThatThrownBy(() -> supportTicketService.getTicketByReferenceAndEmail(
                    "INVALID-REF", email))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("includes responses in returned DTO")
        void includesResponsesInReturnedDto() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            // Add a customer response
            supportTicketService.addCustomerResponse(
                    ticket.getTicketReference(), email, createResponseDtoIn("Customer message"));

            SupportTicketDtoOut result = supportTicketService.getTicketByReferenceAndEmail(
                    ticket.getTicketReference(), email);

            assertThat(result.getResponses()).hasSize(1);
            assertThat(result.getResponses().get(0).getContent()).isEqualTo("Customer message");
        }
    }

    @Nested
    @DisplayName("findTickets()")
    class FindTickets {

        @BeforeEach
        void setUpAdminAuth() {
            authenticateAs(testAdmin);
        }

        @Test
        @DisplayName("returns all tickets without filters")
        void returnsAllTicketsWithoutFilters() {
            String email1 = generateTestEmail();
            String email2 = generateTestEmail();
            createTicket(email1, "Subject 1", "Description 1");
            createTicket(email2, "Subject 2", "Description 2");

            Page<SupportTicketDtoOut> result = supportTicketService.findTickets(
                    null, null, null, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("filters by status")
        void filtersByStatus() {
            String email1 = generateTestEmail();
            String email2 = generateTestEmail();
            createTicketWithStatus(email1, "Open Ticket", "Description", TicketStatus.OPEN);
            createTicketWithStatus(email2, "In Progress Ticket", "Description", TicketStatus.IN_PROGRESS);

            Page<SupportTicketDtoOut> result = supportTicketService.findTickets(
                    TicketStatus.OPEN, null, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).allSatisfy(dto ->
                    assertThat(dto.getStatus()).isEqualTo(TicketStatus.OPEN));
        }

        @Test
        @DisplayName("filters by category")
        void filtersByCategory() {
            String email1 = generateTestEmail();
            String email2 = generateTestEmail();
            createTicket(email1, "Technical Issue", "Description", TicketCategory.TECHNICAL_PROBLEM);
            createTicket(email2, "Billing Issue", "Description", TicketCategory.BILLING_PAYMENT);

            Page<SupportTicketDtoOut> result = supportTicketService.findTickets(
                    null, TicketCategory.TECHNICAL_PROBLEM, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).allSatisfy(dto ->
                    assertThat(dto.getCategory()).isEqualTo(TicketCategory.TECHNICAL_PROBLEM));
        }

        @Test
        @DisplayName("searches by text in subject")
        void searchesByTextInSubject() {
            String email1 = generateTestEmail();
            String email2 = generateTestEmail();
            createTicket(email1, "Login Problem", "Cannot login to the system");
            createTicket(email2, "Payment Issue", "Payment failed");

            Page<SupportTicketDtoOut> result = supportTicketService.findTickets(
                    null, null, "Login", PageRequest.of(0, 10));

            assertThat(result.getContent()).allSatisfy(dto ->
                    assertThat(dto.getSubject().toLowerCase()).contains("login"));
        }

        @Test
        @DisplayName("searches by text in description")
        void searchesByTextInDescription() {
            String email1 = generateTestEmail();
            String email2 = generateTestEmail();
            createTicket(email1, "Issue A", "Contains searchable keyword in description");
            createTicket(email2, "Issue B", "Different content here");

            Page<SupportTicketDtoOut> result = supportTicketService.findTickets(
                    null, null, "searchable", PageRequest.of(0, 10));

            assertThat(result.getContent()).allSatisfy(dto ->
                    assertThat(dto.getDescription().toLowerCase()).contains("searchable"));
        }

        @Test
        @DisplayName("supports pagination")
        void supportsPagination() {
            // Create 5 tickets
            for (int i = 0; i < 5; i++) {
                createTicket(generateTestEmail(), "Ticket " + i, "Description");
            }

            Page<SupportTicketDtoOut> page1 = supportTicketService.findTickets(
                    null, null, null, PageRequest.of(0, 2));
            Page<SupportTicketDtoOut> page2 = supportTicketService.findTickets(
                    null, null, null, PageRequest.of(1, 2));

            assertThat(page1.getContent()).hasSize(2);
            assertThat(page2.getContent()).hasSize(2);
            assertThat(page1.getTotalPages()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("returns empty page when no matches")
        void returnsEmptyPageWhenNoMatches() {
            Page<SupportTicketDtoOut> result = supportTicketService.findTickets(
                    null, null, "nonexistent-query-xyz123", PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("search is case-insensitive")
        void searchIsCaseInsensitive() {
            String email = generateTestEmail();
            createTicket(email, "PASSWORD Reset Issue", "Need to reset PASSWORD");

            Page<SupportTicketDtoOut> upperResult = supportTicketService.findTickets(
                    null, null, "PASSWORD", PageRequest.of(0, 10));
            Page<SupportTicketDtoOut> lowerResult = supportTicketService.findTickets(
                    null, null, "password", PageRequest.of(0, 10));

            assertThat(upperResult.getTotalElements()).isEqualTo(lowerResult.getTotalElements());
        }
    }

    @Nested
    @DisplayName("updateTicketStatus()")
    class UpdateTicketStatus {

        @BeforeEach
        void setUpAdminAuth() {
            authenticateAs(testAdmin);
        }

        @Test
        @DisplayName("updates ticket status successfully")
        void updatesTicketStatusSuccessfully() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.OPEN);

            SupportTicketDtoOut result = supportTicketService.updateTicketStatus(ticket.getId(), TicketStatus.IN_PROGRESS);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("throws InsufficientPermissionsException when non-admin updates status")
        void throwsInsufficientPermissionsWhenNonAdminUpdatesStatus() {
            authenticateAs(testInfluencer);
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.OPEN);

            assertThatThrownBy(() -> supportTicketService.updateTicketStatus(ticket.getId(), TicketStatus.IN_PROGRESS))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("sets resolvedTime when transitioning to RESOLVED")
        void setsResolvedTimeWhenTransitioningToResolved() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.IN_PROGRESS);

            SupportTicketDtoOut result = supportTicketService.updateTicketStatus(ticket.getId(), TicketStatus.RESOLVED);

            assertThat(result.getResolvedTime()).isNotNull();
            assertThat(result.isResolved()).isTrue();
        }

        @Test
        @DisplayName("sets resolvedTime when transitioning to CLOSED")
        void setsResolvedTimeWhenTransitioningToClosed() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.IN_PROGRESS);

            SupportTicketDtoOut result = supportTicketService.updateTicketStatus(ticket.getId(), TicketStatus.CLOSED);

            assertThat(result.getResolvedTime()).isNotNull();
            assertThat(result.isResolved()).isTrue();
        }

        @Test
        @DisplayName("throws BusinessRuleTranslatableException for invalid status transition")
        void throwsBusinessRuleExceptionForInvalidStatusTransition() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.CLOSED);

            // CLOSED tickets cannot transition to any status
            assertThatThrownBy(() -> supportTicketService.updateTicketStatus(ticket.getId(), TicketStatus.OPEN))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent ticket")
        void throwsResourceNotFoundForNonExistentTicket() {
            assertThatThrownBy(() -> supportTicketService.updateTicketStatus(99999L, TicketStatus.IN_PROGRESS))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("allows valid status transitions")
        void allowsValidStatusTransitions() {
            // OPEN -> IN_PROGRESS
            String email1 = generateTestEmail();
            SupportTicket ticket1 = createTicketWithStatus(email1, "Ticket 1", "Description", TicketStatus.OPEN);
            SupportTicketDtoOut result1 = supportTicketService.updateTicketStatus(ticket1.getId(), TicketStatus.IN_PROGRESS);
            assertThat(result1.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

            // IN_PROGRESS -> WAITING_FOR_CUSTOMER
            String email2 = generateTestEmail();
            SupportTicket ticket2 = createTicketWithStatus(email2, "Ticket 2", "Description", TicketStatus.IN_PROGRESS);
            SupportTicketDtoOut result2 = supportTicketService.updateTicketStatus(ticket2.getId(), TicketStatus.WAITING_FOR_CUSTOMER);
            assertThat(result2.getStatus()).isEqualTo(TicketStatus.WAITING_FOR_CUSTOMER);

            // WAITING_FOR_CUSTOMER -> RESOLVED
            String email3 = generateTestEmail();
            SupportTicket ticket3 = createTicketWithStatus(email3, "Ticket 3", "Description", TicketStatus.WAITING_FOR_CUSTOMER);
            SupportTicketDtoOut result3 = supportTicketService.updateTicketStatus(ticket3.getId(), TicketStatus.RESOLVED);
            assertThat(result3.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        }
    }

    @Nested
    @DisplayName("getTicketById()")
    class GetTicketById {

        @Test
        @DisplayName("admin can access any ticket")
        void adminCanAccessAnyTicket() {
            authenticateAs(testAdmin);
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");

            SupportTicketDtoOut result = supportTicketService.getTicketById(ticket.getId(), true);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(ticket.getId());
        }

        @Test
        @DisplayName("non-admin cannot access ticket with adminOnly flag")
        void nonAdminCannotAccessTicketWithAdminOnlyFlag() {
            authenticateAs(testInfluencer);
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");

            assertThatThrownBy(() -> supportTicketService.getTicketById(ticket.getId(), true))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("user can access their own ticket")
        void userCanAccessTheirOwnTicket() {
            authenticateAs(testInfluencer);
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description", TicketCategory.GENERAL_INQUIRY, testInfluencer);

            SupportTicketDtoOut result = supportTicketService.getTicketById(ticket.getId(), false);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(ticket.getId());
        }

        @Test
        @DisplayName("user cannot access other user's ticket")
        void userCannotAccessOtherUsersTicket() {
            authenticateAs(testInfluencer);
            String email = generateTestEmail();
            // Create ticket associated with testCompany user
            SupportTicket ticket = createTicket(email, "Subject", "Description", TicketCategory.GENERAL_INQUIRY, testCompany);

            assertThatThrownBy(() -> supportTicketService.getTicketById(ticket.getId(), false))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }
}
