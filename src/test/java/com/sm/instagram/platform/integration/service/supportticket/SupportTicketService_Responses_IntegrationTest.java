package com.sm.instagram.platform.integration.service.supportticket;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.support.ticket.dtos.AdminTicketResponseDtoIn;
import com.sm.instagram.platform.support.ticket.dtos.TicketResponseDtoIn;
import com.sm.instagram.platform.support.ticket.dtos.TicketResponseDtoOut;
import com.sm.instagram.platform.support.ticket.models.SupportTicket;
import com.sm.instagram.platform.support.ticket.models.TicketResponse;
import com.sm.instagram.platform.support.ticket.models.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for SupportTicketService response operations.
 */
@DisplayName("SupportTicketService Response Operations")
class SupportTicketService_Responses_IntegrationTest extends SupportTicketServiceIntegrationTestBase {

    @Nested
    @DisplayName("addCustomerResponse()")
    class AddCustomerResponse {

        @Test
        @DisplayName("adds customer response to ticket successfully")
        void addsCustomerResponseToTicketSuccessfully() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            TicketResponseDtoIn responseDto = createResponseDtoIn("Customer's response content");

            TicketResponse result = supportTicketService.addCustomerResponse(
                    ticket.getTicketReference(), email, responseDto);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEqualTo("Customer's response content");
            assertThat(result.isFromAdmin()).isFalse();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for wrong email")
        void throwsResourceNotFoundForWrongEmail() {
            String correctEmail = generateTestEmail();
            String wrongEmail = generateTestEmail();
            SupportTicket ticket = createTicket(correctEmail, "Subject", "Description");
            TicketResponseDtoIn responseDto = createResponseDtoIn("Response content");

            assertThatThrownBy(() -> supportTicketService.addCustomerResponse(
                    ticket.getTicketReference(), wrongEmail, responseDto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for invalid reference")
        void throwsResourceNotFoundForInvalidReference() {
            String email = generateTestEmail();
            TicketResponseDtoIn responseDto = createResponseDtoIn("Response content");

            assertThatThrownBy(() -> supportTicketService.addCustomerResponse(
                    "INVALID-REF", email, responseDto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("changes status from WAITING_FOR_CUSTOMER to IN_PROGRESS")
        void changesStatusFromWaitingForCustomerToInProgress() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.WAITING_FOR_CUSTOMER);
            TicketResponseDtoIn responseDto = createResponseDtoIn("Response content");

            supportTicketService.addCustomerResponse(ticket.getTicketReference(), email, responseDto);

            SupportTicket updated = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("does not change status when ticket is OPEN")
        void doesNotChangeStatusWhenTicketIsOpen() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.OPEN);
            TicketResponseDtoIn responseDto = createResponseDtoIn("Response content");

            supportTicketService.addCustomerResponse(ticket.getTicketReference(), email, responseDto);

            SupportTicket updated = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TicketStatus.OPEN);
        }

        @Test
        @DisplayName("updates ticket lastUpdateTime")
        void updatesTicketLastUpdateTime() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            // Flush and clear to ensure @CreationTimestamp is populated
            flushAndClear();
            SupportTicket freshTicket = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            TicketResponseDtoIn responseDto = createResponseDtoIn("Response content");

            supportTicketService.addCustomerResponse(freshTicket.getTicketReference(), email, responseDto);

            SupportTicket updated = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            assertThat(updated.getLastUpdateTime()).isNotNull();
            assertThat(updated.getLastUpdateTime()).isAfterOrEqualTo(freshTicket.getCreatedTime().minusSeconds(1));
        }
    }

    @Nested
    @DisplayName("addCustomerResponseAndReturnDto()")
    class AddCustomerResponseAndReturnDto {

        @Test
        @DisplayName("returns response DTO with content and ticket association")
        void returnsResponseDtoWithContentAndTicketAssociation() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            TicketResponseDtoIn responseDto = createResponseDtoIn("Customer response");

            TicketResponseDtoOut result = supportTicketService.addCustomerResponseAndReturnDto(
                    ticket.getTicketReference(), email, responseDto);

            assertThat(result.getContent()).isEqualTo("Customer response");
            assertThat(result.isFromAdmin()).isFalse();
            assertThat(result.getTicketId()).isEqualTo(ticket.getId());
        }

        @Test
        @DisplayName("persists response in database")
        void persistsResponseInDatabase() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            TicketResponseDtoIn responseDto = createResponseDtoIn("Customer response");

            supportTicketService.addCustomerResponseAndReturnDto(
                    ticket.getTicketReference(), email, responseDto);

            // Verify response was persisted via repository
            var responses = ticketResponseRepository.findByTicketOrderByCreatedTimeAsc(ticket);
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getContent()).isEqualTo("Customer response");
            assertThat(responses.get(0).getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("addAdminResponse()")
    class AddAdminResponse {

        @BeforeEach
        void setUpAdminAuth() {
            authenticateAs(testAdmin);
        }

        @Test
        @DisplayName("adds admin response to ticket successfully")
        void addsAdminResponseToTicketSuccessfully() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            AdminTicketResponseDtoIn responseDto = createAdminResponseDtoIn("Admin response", "Admin User");

            TicketResponseDtoOut result = supportTicketService.addAdminResponse(ticket.getId(), responseDto);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEqualTo("Admin response");
            assertThat(result.isFromAdmin()).isTrue();
            assertThat(result.getAdminName()).isEqualTo("Admin User");
        }

        @Test
        @DisplayName("throws InsufficientPermissionsException when non-admin tries to respond")
        void throwsInsufficientPermissionsWhenNonAdminTriesToRespond() {
            authenticateAs(testInfluencer);
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            AdminTicketResponseDtoIn responseDto = createAdminResponseDtoIn("Response", "Admin");

            assertThatThrownBy(() -> supportTicketService.addAdminResponse(ticket.getId(), responseDto))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("sets ticket status to WAITING_FOR_CUSTOMER by default")
        void setsTicketStatusToWaitingForCustomerByDefault() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.OPEN);
            AdminTicketResponseDtoIn responseDto = createAdminResponseDtoIn("Admin response", "Admin");

            supportTicketService.addAdminResponse(ticket.getId(), responseDto);

            SupportTicket updated = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TicketStatus.WAITING_FOR_CUSTOMER);
        }

        @Test
        @DisplayName("can set custom status with response")
        void canSetCustomStatusWithResponse() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.OPEN);
            AdminTicketResponseDtoIn responseDto = createAdminResponseDtoIn("Issue resolved", "Admin", TicketStatus.RESOLVED);

            supportTicketService.addAdminResponse(ticket.getId(), responseDto);

            SupportTicket updated = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        }

        @Test
        @DisplayName("sets resolvedTime when transitioning to RESOLVED")
        void setsResolvedTimeWhenTransitioningToResolved() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicketWithStatus(email, "Subject", "Description", TicketStatus.OPEN);
            AdminTicketResponseDtoIn responseDto = createAdminResponseDtoIn("Resolved", "Admin", TicketStatus.RESOLVED);

            supportTicketService.addAdminResponse(ticket.getId(), responseDto);

            SupportTicket updated = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            assertThat(updated.getResolvedTime()).isNotNull();
        }

        @Test
        @DisplayName("sends email notification when sendEmail is true")
        void sendsEmailNotificationWhenSendEmailIsTrue() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            AdminTicketResponseDtoIn responseDto = new AdminTicketResponseDtoIn();
            responseDto.setContent("Admin response");
            responseDto.setAdminName("Admin User");
            responseDto.setSendEmail(true);

            supportTicketService.addAdminResponse(ticket.getId(), responseDto);

            verify(emailService).sendAdminResponseNotification(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("does not send email when sendEmail is false")
        void doesNotSendEmailWhenSendEmailIsFalse() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            AdminTicketResponseDtoIn responseDto = createAdminResponseDtoIn("Admin response", "Admin");
            responseDto.setSendEmail(false);

            supportTicketService.addAdminResponse(ticket.getId(), responseDto);

            verify(emailService, never()).sendAdminResponseNotification(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent ticket")
        void throwsResourceNotFoundForNonExistentTicket() {
            AdminTicketResponseDtoIn responseDto = createAdminResponseDtoIn("Response", "Admin");

            assertThatThrownBy(() -> supportTicketService.addAdminResponse(99999L, responseDto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("updates ticket lastUpdateTime on admin response")
        void updatesTicketLastUpdateTimeOnAdminResponse() {
            String email = generateTestEmail();
            SupportTicket ticket = createTicket(email, "Subject", "Description");
            // Flush and clear to ensure @CreationTimestamp is populated
            flushAndClear();
            SupportTicket freshTicket = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            AdminTicketResponseDtoIn responseDto = createAdminResponseDtoIn("Response", "Admin");

            supportTicketService.addAdminResponse(freshTicket.getId(), responseDto);

            SupportTicket updated = supportTicketRepository.findById(ticket.getId()).orElseThrow();
            assertThat(updated.getLastUpdateTime()).isNotNull();
            assertThat(updated.getLastUpdateTime()).isAfterOrEqualTo(freshTicket.getCreatedTime().minusSeconds(1));
        }
    }
}
