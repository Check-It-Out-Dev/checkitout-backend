package com.sm.instagram.platform.integration.service.supportticket;

import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.support.ticket.dtos.AdminTicketResponseDtoIn;
import com.sm.instagram.platform.support.ticket.dtos.SupportTicketDtoIn;
import com.sm.instagram.platform.support.ticket.dtos.TicketResponseDtoIn;
import com.sm.instagram.platform.support.ticket.models.SupportTicket;
import com.sm.instagram.platform.support.ticket.models.TicketCategory;
import com.sm.instagram.platform.support.ticket.models.TicketStatus;
import com.sm.instagram.platform.support.ticket.repositories.SupportTicketRepository;
import com.sm.instagram.platform.support.ticket.repositories.TicketResponseRepository;
import com.sm.instagram.platform.support.ticket.services.SupportTicketService;
import com.sm.instagram.platform.support.ticket.services.TicketReferenceService;
import com.sm.instagram.platform.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

/**
 * Base class for SupportTicketService integration tests.
 * Provides common fixtures and helper methods for testing support ticket functionality.
 */
public abstract class SupportTicketServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected SupportTicketService supportTicketService;

    @Autowired
    protected SupportTicketRepository supportTicketRepository;

    @Autowired
    protected TicketResponseRepository ticketResponseRepository;

    @Autowired
    protected TicketReferenceService ticketReferenceService;

    @MockBean
    protected EmailService emailService;

    @PersistenceContext
    protected EntityManager entityManager;

    protected static final String TEST_IP_ADDRESS = "127.0.0.1";

    /**
     * Flushes and clears the persistence context to ensure entities are persisted
     * and timestamps are populated before subsequent queries.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Creates a support ticket with default values.
     *
     * @param email The contact email
     * @param subject The ticket subject
     * @param description The ticket description
     * @return The created SupportTicket entity
     */
    protected SupportTicket createTicket(String email, String subject, String description) {
        return createTicket(email, subject, description, TicketCategory.GENERAL_INQUIRY, null);
    }

    /**
     * Creates a support ticket with specified category.
     *
     * @param email The contact email
     * @param subject The ticket subject
     * @param description The ticket description
     * @param category The ticket category
     * @return The created SupportTicket entity
     */
    protected SupportTicket createTicket(String email, String subject, String description, TicketCategory category) {
        return createTicket(email, subject, description, category, null);
    }

    /**
     * Creates a support ticket associated with a user.
     *
     * @param email The contact email
     * @param subject The ticket subject
     * @param description The ticket description
     * @param category The ticket category
     * @param user The user to associate with the ticket (can be null)
     * @return The created SupportTicket entity
     */
    protected SupportTicket createTicket(String email, String subject, String description, TicketCategory category, User user) {
        SupportTicket ticket = new SupportTicket();
        ticket.setContactEmail(email);
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setCategory(category);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setTicketReference(ticketReferenceService.generateTicketReference());
        ticket.setIpAddress(TEST_IP_ADDRESS);
        ticket.setUser(user);
        return supportTicketRepository.save(ticket);
    }

    /**
     * Creates a support ticket with specified status.
     *
     * @param email The contact email
     * @param subject The ticket subject
     * @param description The ticket description
     * @param status The ticket status
     * @return The created SupportTicket entity
     */
    protected SupportTicket createTicketWithStatus(String email, String subject, String description, TicketStatus status) {
        SupportTicket ticket = new SupportTicket();
        ticket.setContactEmail(email);
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setCategory(TicketCategory.GENERAL_INQUIRY);
        ticket.setStatus(status);
        ticket.setTicketReference(ticketReferenceService.generateTicketReference());
        ticket.setIpAddress(TEST_IP_ADDRESS);
        return supportTicketRepository.save(ticket);
    }

    /**
     * Creates a SupportTicketDtoIn for testing ticket creation via service.
     *
     * @param email The contact email
     * @param subject The ticket subject
     * @param description The ticket description
     * @return A populated SupportTicketDtoIn
     */
    protected SupportTicketDtoIn createTicketDtoIn(String email, String subject, String description) {
        SupportTicketDtoIn dto = new SupportTicketDtoIn();
        dto.setContactEmail(email);
        dto.setSubject(subject);
        dto.setDescription(description);
        dto.setCategory(TicketCategory.GENERAL_INQUIRY);
        return dto;
    }

    /**
     * Creates a SupportTicketDtoIn with a specific category.
     *
     * @param email The contact email
     * @param subject The ticket subject
     * @param description The ticket description
     * @param category The ticket category
     * @return A populated SupportTicketDtoIn
     */
    protected SupportTicketDtoIn createTicketDtoIn(String email, String subject, String description, TicketCategory category) {
        SupportTicketDtoIn dto = new SupportTicketDtoIn();
        dto.setContactEmail(email);
        dto.setSubject(subject);
        dto.setDescription(description);
        dto.setCategory(category);
        return dto;
    }

    /**
     * Creates a TicketResponseDtoIn for testing customer responses.
     *
     * @param content The response content
     * @return A populated TicketResponseDtoIn
     */
    protected TicketResponseDtoIn createResponseDtoIn(String content) {
        TicketResponseDtoIn dto = new TicketResponseDtoIn();
        dto.setContent(content);
        return dto;
    }

    /**
     * Creates an AdminTicketResponseDtoIn for testing admin responses.
     *
     * @param content The response content
     * @param adminName The admin's name
     * @return A populated AdminTicketResponseDtoIn
     */
    protected AdminTicketResponseDtoIn createAdminResponseDtoIn(String content, String adminName) {
        AdminTicketResponseDtoIn dto = new AdminTicketResponseDtoIn();
        dto.setContent(content);
        dto.setAdminName(adminName);
        dto.setSendEmail(false); // Disable email for tests
        return dto;
    }

    /**
     * Creates an AdminTicketResponseDtoIn with status change.
     *
     * @param content The response content
     * @param adminName The admin's name
     * @param newStatus The new status for the ticket
     * @return A populated AdminTicketResponseDtoIn
     */
    protected AdminTicketResponseDtoIn createAdminResponseDtoIn(String content, String adminName, TicketStatus newStatus) {
        AdminTicketResponseDtoIn dto = new AdminTicketResponseDtoIn();
        dto.setContent(content);
        dto.setAdminName(adminName);
        dto.setNewStatus(newStatus);
        dto.setSendEmail(false);
        return dto;
    }

    /**
     * Generates a unique test email.
     *
     * @return A unique email address
     */
    protected String generateTestEmail() {
        return "test." + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }
}
