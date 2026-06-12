package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.support.ticket.SupportTicketController;
import com.sm.instagram.platform.support.ticket.dtos.*;
import com.sm.instagram.platform.support.ticket.models.*;
import com.sm.instagram.platform.support.ticket.services.SupportTicketService;
import com.sm.instagram.platform.support.ticket.services.TicketReferenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for SupportTicketController and related classes.
 * Uses pure Mockito without Spring context.
 * 
 * Tests cover:
 * - All controller endpoints
 * - SupportTicket entity
 * - All DTOs (SupportTicketDtoIn, SupportTicketDtoOut, TicketResponseDtoIn, etc.)
 * - TicketStatus and TicketCategory enums
 * - TicketReferenceService
 * - Validation logic
 * - Error handling
 * - Edge cases
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SupportTicketController Full Unit Tests")
class SupportTicketControllerFullUnitTest {

    @Mock
    private SupportTicketService ticketService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SupportTicketController controller;

    // Test data
    private SupportTicketDtoIn testTicketDtoIn;
    private SupportTicketDtoOut testTicketDtoOut;
    private SupportTicket testTicket;
    private TicketResponseDtoIn testResponseDtoIn;
    private TicketResponseDtoOut testResponseDtoOut;
    private AdminTicketResponseDtoIn testAdminResponseDtoIn;
    private TicketAttachmentDtoIn testAttachmentDtoIn;
    private TicketAttachmentDtoOut testAttachmentDtoOut;
    private ResponseAttachmentDtoIn testResponseAttachmentDtoIn;
    private ResponseAttachmentDtoOut testResponseAttachmentDtoOut;

    private static final String TEST_FIREBASE_UID = "test-firebase-uid";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_REFERENCE = "CIO-20241201-1234";
    private static final String TEST_IP = "192.168.1.1";
    private static final Long TEST_TICKET_ID = 1L;
    private static final Long TEST_RESPONSE_ID = 10L;

    @BeforeEach
    void setUp() {
        setupSecurityContext();
        setupTestData();
    }

    private void setupSecurityContext() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(TEST_FIREBASE_UID);
        when(authentication.getName()).thenReturn(TEST_FIREBASE_UID);
        SecurityContextHolder.setContext(securityContext);
    }

    private void setupSecurityContextWithAdmin() {
        Collection<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ADMIN")
        );
        when(authentication.getAuthorities()).thenAnswer(inv -> authorities);
    }

    private void setupTestData() {
        // Setup test SupportTicketDtoIn
        testTicketDtoIn = new SupportTicketDtoIn();
        testTicketDtoIn.setContactEmail(TEST_EMAIL);
        testTicketDtoIn.setSubject("Test Subject");
        testTicketDtoIn.setDescription("Test Description");
        testTicketDtoIn.setCategory(TicketCategory.TECHNICAL_PROBLEM);

        // Setup test SupportTicket entity
        testTicket = new SupportTicket();
        testTicket.setId(TEST_TICKET_ID);
        testTicket.setContactEmail(TEST_EMAIL);
        testTicket.setSubject("Test Subject");
        testTicket.setDescription("Test Description");
        testTicket.setCategory(TicketCategory.TECHNICAL_PROBLEM);
        testTicket.setStatus(TicketStatus.OPEN);
        testTicket.setTicketReference(TEST_REFERENCE);
        testTicket.setIpAddress(TEST_IP);
        testTicket.setCreatedTime(LocalDateTime.now());
        testTicket.setLastUpdateTime(LocalDateTime.now());

        // Setup test SupportTicketDtoOut
        testTicketDtoOut = new SupportTicketDtoOut();
        testTicketDtoOut.setId(TEST_TICKET_ID);
        testTicketDtoOut.setContactEmail(TEST_EMAIL);
        testTicketDtoOut.setSubject("Test Subject");
        testTicketDtoOut.setDescription("Test Description");
        testTicketDtoOut.setCategory(TicketCategory.TECHNICAL_PROBLEM);
        testTicketDtoOut.setCategoryDisplay("Technical Problem");
        testTicketDtoOut.setStatus(TicketStatus.OPEN);
        testTicketDtoOut.setStatusDisplay("Open");
        testTicketDtoOut.setTicketReference(TEST_REFERENCE);
        testTicketDtoOut.setCreatedTime(LocalDateTime.now());
        testTicketDtoOut.setLastUpdateTime(LocalDateTime.now());
        testTicketDtoOut.setResponses(new ArrayList<>());
        testTicketDtoOut.setAttachments(new ArrayList<>());

        // Setup test TicketResponseDtoIn
        testResponseDtoIn = new TicketResponseDtoIn();
        testResponseDtoIn.setContent("Test response content");

        // Setup test TicketResponseDtoOut
        testResponseDtoOut = new TicketResponseDtoOut();
        testResponseDtoOut.setId(TEST_RESPONSE_ID);
        testResponseDtoOut.setTicketId(TEST_TICKET_ID);
        testResponseDtoOut.setContent("Test response content");
        testResponseDtoOut.setFromAdmin(false);
        testResponseDtoOut.setCreatedTime(LocalDateTime.now());
        testResponseDtoOut.setAttachments(new ArrayList<>());

        // Setup test AdminTicketResponseDtoIn
        testAdminResponseDtoIn = new AdminTicketResponseDtoIn();
        testAdminResponseDtoIn.setContent("Admin response content");
        testAdminResponseDtoIn.setAdminName("Admin User");
        testAdminResponseDtoIn.setNewStatus(TicketStatus.IN_PROGRESS);
        testAdminResponseDtoIn.setSendEmail(true);

        // Setup test TicketAttachmentDtoIn
        testAttachmentDtoIn = new TicketAttachmentDtoIn();
        testAttachmentDtoIn.setFileName("test-file.pdf");
        testAttachmentDtoIn.setContentType("application/pdf");
        testAttachmentDtoIn.setFileUrl("https://storage.example.com/files/test-file.pdf");
        testAttachmentDtoIn.setFileSize(1024L);

        // Setup test TicketAttachmentDtoOut
        testAttachmentDtoOut = new TicketAttachmentDtoOut();
        testAttachmentDtoOut.setId(1L);
        testAttachmentDtoOut.setTicketId(TEST_TICKET_ID);
        testAttachmentDtoOut.setFileName("test-file.pdf");
        testAttachmentDtoOut.setContentType("application/pdf");
        testAttachmentDtoOut.setFileSize(1024L);
        testAttachmentDtoOut.setUploadTime(LocalDateTime.now());
        testAttachmentDtoOut.setDownloadUrl("https://storage.example.com/files/test-file.pdf");

        // Setup test ResponseAttachmentDtoIn
        testResponseAttachmentDtoIn = new ResponseAttachmentDtoIn();
        testResponseAttachmentDtoIn.setFileName("response-file.png");
        testResponseAttachmentDtoIn.setContentType("image/png");
        testResponseAttachmentDtoIn.setFileUrl("https://storage.example.com/files/response-file.png");
        testResponseAttachmentDtoIn.setFileSize(2048L);

        // Setup test ResponseAttachmentDtoOut
        testResponseAttachmentDtoOut = new ResponseAttachmentDtoOut();
        testResponseAttachmentDtoOut.setId(2L);
        testResponseAttachmentDtoOut.setResponseId(TEST_RESPONSE_ID);
        testResponseAttachmentDtoOut.setFileName("response-file.png");
        testResponseAttachmentDtoOut.setContentType("image/png");
        testResponseAttachmentDtoOut.setFileSize(2048L);
        testResponseAttachmentDtoOut.setUploadTime(LocalDateTime.now());
        testResponseAttachmentDtoOut.setDownloadUrl("https://storage.example.com/files/response-file.png");
    }

    // ==================== CreateTicket Endpoint Tests ====================

    @Nested
    @DisplayName("POST /support/ticket - createTicket")
    class CreateTicketTests {

        @Test
        @DisplayName("should create ticket successfully")
        void shouldCreateTicketSuccessfully() {
            when(httpServletRequest.getRemoteAddr()).thenReturn(TEST_IP);
            when(ticketService.createTicket(any(SupportTicketDtoIn.class), eq(TEST_IP))).thenReturn(testTicket);
            when(ticketService.convertToDto(testTicket)).thenReturn(testTicketDtoOut);

            ResponseEntity<SupportTicketDtoOut> response = controller.createTicket(testTicketDtoIn, httpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(TEST_TICKET_ID);
            assertThat(response.getBody().getTicketReference()).isEqualTo(TEST_REFERENCE);
            verify(ticketService).createTicket(testTicketDtoIn, TEST_IP);
            verify(ticketService).convertToDto(testTicket);
        }

        @Test
        @DisplayName("should extract IP address from request")
        void shouldExtractIpAddressFromRequest() {
            String expectedIp = "10.0.0.1";
            when(httpServletRequest.getRemoteAddr()).thenReturn(expectedIp);
            when(ticketService.createTicket(any(SupportTicketDtoIn.class), eq(expectedIp))).thenReturn(testTicket);
            when(ticketService.convertToDto(testTicket)).thenReturn(testTicketDtoOut);

            controller.createTicket(testTicketDtoIn, httpServletRequest);

            verify(ticketService).createTicket(testTicketDtoIn, expectedIp);
        }

        @Test
        @DisplayName("should handle ticket with all categories")
        void shouldHandleTicketWithAllCategories() {
            when(httpServletRequest.getRemoteAddr()).thenReturn(TEST_IP);
            when(ticketService.createTicket(any(SupportTicketDtoIn.class), anyString())).thenReturn(testTicket);
            when(ticketService.convertToDto(testTicket)).thenReturn(testTicketDtoOut);

            for (TicketCategory category : TicketCategory.values()) {
                testTicketDtoIn.setCategory(category);
                ResponseEntity<SupportTicketDtoOut> response = controller.createTicket(testTicketDtoIn, httpServletRequest);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Test
        @DisplayName("should pass technical description to service")
        void shouldPassTechnicalDescriptionToService() {
            testTicketDtoIn.setTechnicalDescription("Error stack trace here...");
            when(httpServletRequest.getRemoteAddr()).thenReturn(TEST_IP);
            when(ticketService.createTicket(any(SupportTicketDtoIn.class), anyString())).thenReturn(testTicket);
            when(ticketService.convertToDto(testTicket)).thenReturn(testTicketDtoOut);

            controller.createTicket(testTicketDtoIn, httpServletRequest);

            verify(ticketService).createTicket(argThat(dto -> 
                "Error stack trace here...".equals(dto.getTechnicalDescription())
            ), anyString());
        }
    }

    // ==================== AddTicketAttachments Endpoint Tests ====================

    @Nested
    @DisplayName("POST /support/ticket/{ticketId}/attachments - addTicketAttachments")
    class AddTicketAttachmentsTests {

        @Test
        @DisplayName("should add attachments to ticket successfully")
        void shouldAddAttachmentsSuccessfully() {
            List<TicketAttachmentDtoIn> attachments = Collections.singletonList(testAttachmentDtoIn);
            List<TicketAttachmentDtoOut> expectedAttachments = Collections.singletonList(testAttachmentDtoOut);
            
            when(ticketService.addTicketAttachments(eq(TEST_TICKET_ID), eq(attachments))).thenReturn(expectedAttachments);

            ResponseEntity<List<TicketAttachmentDtoOut>> response = controller.addTicketAttachments(TEST_TICKET_ID, attachments);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getFileName()).isEqualTo("test-file.pdf");
            verify(ticketService).addTicketAttachments(TEST_TICKET_ID, attachments);
        }

        @Test
        @DisplayName("should handle multiple attachments")
        void shouldHandleMultipleAttachments() {
            TicketAttachmentDtoIn attachment2 = new TicketAttachmentDtoIn();
            attachment2.setFileName("file2.jpg");
            attachment2.setContentType("image/jpeg");
            attachment2.setFileUrl("https://storage.example.com/file2.jpg");
            attachment2.setFileSize(512L);

            List<TicketAttachmentDtoIn> attachments = Arrays.asList(testAttachmentDtoIn, attachment2);
            
            TicketAttachmentDtoOut out2 = new TicketAttachmentDtoOut();
            out2.setId(2L);
            out2.setFileName("file2.jpg");
            List<TicketAttachmentDtoOut> expectedAttachments = Arrays.asList(testAttachmentDtoOut, out2);
            
            when(ticketService.addTicketAttachments(eq(TEST_TICKET_ID), eq(attachments))).thenReturn(expectedAttachments);

            ResponseEntity<List<TicketAttachmentDtoOut>> response = controller.addTicketAttachments(TEST_TICKET_ID, attachments);

            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("should handle empty attachments list")
        void shouldHandleEmptyAttachmentsList() {
            List<TicketAttachmentDtoIn> attachments = Collections.emptyList();
            when(ticketService.addTicketAttachments(eq(TEST_TICKET_ID), eq(attachments))).thenReturn(Collections.emptyList());

            ResponseEntity<List<TicketAttachmentDtoOut>> response = controller.addTicketAttachments(TEST_TICKET_ID, attachments);

            assertThat(response.getBody()).isEmpty();
        }
    }

    // ==================== GetTicketByReference Endpoint Tests ====================

    @Nested
    @DisplayName("GET /support/ticket/status - getTicketByReference")
    class GetTicketByReferenceTests {

        @Test
        @DisplayName("should get ticket by reference and email")
        void shouldGetTicketByReferenceAndEmail() {
            when(ticketService.getTicketByReferenceAndEmail(TEST_REFERENCE, TEST_EMAIL)).thenReturn(testTicketDtoOut);

            ResponseEntity<SupportTicketDtoOut> response = controller.getTicketByReference(TEST_REFERENCE, TEST_EMAIL);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTicketReference()).isEqualTo(TEST_REFERENCE);
            verify(ticketService).getTicketByReferenceAndEmail(TEST_REFERENCE, TEST_EMAIL);
        }

        @Test
        @DisplayName("should throw exception when ticket not found")
        void shouldThrowExceptionWhenTicketNotFound() {
            when(ticketService.getTicketByReferenceAndEmail(anyString(), anyString()))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Ticket"));

            assertThatThrownBy(() -> controller.getTicketByReference("INVALID-REF", TEST_EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should return ticket with responses")
        void shouldReturnTicketWithResponses() {
            testTicketDtoOut.setResponses(Collections.singletonList(testResponseDtoOut));
            when(ticketService.getTicketByReferenceAndEmail(TEST_REFERENCE, TEST_EMAIL)).thenReturn(testTicketDtoOut);

            ResponseEntity<SupportTicketDtoOut> response = controller.getTicketByReference(TEST_REFERENCE, TEST_EMAIL);

            assertThat(response.getBody().getResponses()).hasSize(1);
        }

        @Test
        @DisplayName("should return ticket with attachments")
        void shouldReturnTicketWithAttachments() {
            testTicketDtoOut.setAttachments(Collections.singletonList(testAttachmentDtoOut));
            when(ticketService.getTicketByReferenceAndEmail(TEST_REFERENCE, TEST_EMAIL)).thenReturn(testTicketDtoOut);

            ResponseEntity<SupportTicketDtoOut> response = controller.getTicketByReference(TEST_REFERENCE, TEST_EMAIL);

            assertThat(response.getBody().getAttachments()).hasSize(1);
        }
    }

    // ==================== AddCustomerResponse Endpoint Tests ====================

    @Nested
    @DisplayName("POST /support/ticket/response - addCustomerResponse")
    class AddCustomerResponseTests {

        @Test
        @DisplayName("should add customer response successfully")
        void shouldAddCustomerResponseSuccessfully() {
            when(ticketService.addCustomerResponseAndReturnDto(TEST_REFERENCE, TEST_EMAIL, testResponseDtoIn))
                    .thenReturn(testResponseDtoOut);

            ResponseEntity<TicketResponseDtoOut> response = controller.addCustomerResponse(
                    TEST_REFERENCE, TEST_EMAIL, testResponseDtoIn);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).isEqualTo("Test response content");
            assertThat(response.getBody().isFromAdmin()).isFalse();
            verify(ticketService).addCustomerResponseAndReturnDto(TEST_REFERENCE, TEST_EMAIL, testResponseDtoIn);
        }

        @Test
        @DisplayName("should throw exception when ticket not found for response")
        void shouldThrowExceptionWhenTicketNotFoundForResponse() {
            when(ticketService.addCustomerResponseAndReturnDto(anyString(), anyString(), any()))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Ticket"));

            assertThatThrownBy(() -> controller.addCustomerResponse("INVALID-REF", TEST_EMAIL, testResponseDtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== AddResponseAttachments Endpoint Tests ====================

    @Nested
    @DisplayName("POST /support/ticket/response/{responseId}/attachments - addResponseAttachments")
    class AddResponseAttachmentsTests {

        @Test
        @DisplayName("should add response attachments successfully")
        void shouldAddResponseAttachmentsSuccessfully() {
            List<ResponseAttachmentDtoIn> attachments = Collections.singletonList(testResponseAttachmentDtoIn);
            List<ResponseAttachmentDtoOut> expectedAttachments = Collections.singletonList(testResponseAttachmentDtoOut);
            
            when(ticketService.addResponseAttachments(eq(TEST_RESPONSE_ID), eq(attachments))).thenReturn(expectedAttachments);

            ResponseEntity<List<ResponseAttachmentDtoOut>> response = controller.addResponseAttachments(TEST_RESPONSE_ID, attachments);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getFileName()).isEqualTo("response-file.png");
            verify(ticketService).addResponseAttachments(TEST_RESPONSE_ID, attachments);
        }

        @Test
        @DisplayName("should handle multiple response attachments")
        void shouldHandleMultipleResponseAttachments() {
            ResponseAttachmentDtoIn attachment2 = new ResponseAttachmentDtoIn();
            attachment2.setFileName("screenshot.png");
            attachment2.setContentType("image/png");
            attachment2.setFileUrl("https://storage.example.com/screenshot.png");
            attachment2.setFileSize(4096L);

            List<ResponseAttachmentDtoIn> attachments = Arrays.asList(testResponseAttachmentDtoIn, attachment2);
            
            ResponseAttachmentDtoOut out2 = new ResponseAttachmentDtoOut();
            out2.setId(3L);
            out2.setFileName("screenshot.png");
            List<ResponseAttachmentDtoOut> expectedAttachments = Arrays.asList(testResponseAttachmentDtoOut, out2);
            
            when(ticketService.addResponseAttachments(eq(TEST_RESPONSE_ID), eq(attachments))).thenReturn(expectedAttachments);

            ResponseEntity<List<ResponseAttachmentDtoOut>> response = controller.addResponseAttachments(TEST_RESPONSE_ID, attachments);

            assertThat(response.getBody()).hasSize(2);
        }
    }

    // ==================== GetTicketById Endpoint Tests ====================

    @Nested
    @DisplayName("GET /support/ticket/{id} - getTicketById")
    class GetTicketByIdTests {

        @Test
        @DisplayName("should get ticket by ID for authenticated user")
        void shouldGetTicketByIdForAuthenticatedUser() {
            when(ticketService.getTicketById(TEST_TICKET_ID, false)).thenReturn(testTicketDtoOut);

            ResponseEntity<SupportTicketDtoOut> response = controller.getTicketById(TEST_TICKET_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(TEST_TICKET_ID);
            verify(ticketService).getTicketById(TEST_TICKET_ID, false);
        }

        @Test
        @DisplayName("should throw exception when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            when(securityContext.getAuthentication()).thenReturn(null);

            assertThatThrownBy(() -> controller.getTicketById(TEST_TICKET_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception when principal is null")
        void shouldThrowExceptionWhenPrincipalIsNull() {
            when(authentication.getPrincipal()).thenReturn(null);

            assertThatThrownBy(() -> controller.getTicketById(TEST_TICKET_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception when ticket not found")
        void shouldThrowExceptionWhenTicketNotFoundById() {
            when(ticketService.getTicketById(anyLong(), anyBoolean()))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Ticket"));

            assertThatThrownBy(() -> controller.getTicketById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== GetTickets (Admin) Endpoint Tests ====================

    @Nested
    @DisplayName("GET /support/ticket - getTickets (Admin)")
    class GetTicketsTests {

        @Test
        @DisplayName("should get tickets page for admin")
        void shouldGetTicketsPageForAdmin() {
            setupSecurityContextWithAdmin();
            Pageable pageable = PageRequest.of(0, 20);
            Page<SupportTicketDtoOut> expectedPage = new PageImpl<>(
                    Collections.singletonList(testTicketDtoOut), pageable, 1);
            
            when(ticketService.findTickets(any(), any(), any(), any())).thenReturn(expectedPage);

            ResponseEntity<Page<SupportTicketDtoOut>> response = controller.getTickets(
                    null, null, null, pageable);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTotalElements()).isEqualTo(1);
            verify(ticketService).findTickets(null, null, null, pageable);
        }

        @Test
        @DisplayName("should filter tickets by status")
        void shouldFilterTicketsByStatus() {
            setupSecurityContextWithAdmin();
            Pageable pageable = PageRequest.of(0, 20);
            Page<SupportTicketDtoOut> expectedPage = new PageImpl<>(Collections.emptyList());
            
            when(ticketService.findTickets(eq(TicketStatus.OPEN), any(), any(), any())).thenReturn(expectedPage);

            controller.getTickets(TicketStatus.OPEN, null, null, pageable);

            verify(ticketService).findTickets(eq(TicketStatus.OPEN), isNull(), isNull(), eq(pageable));
        }

        @Test
        @DisplayName("should filter tickets by category")
        void shouldFilterTicketsByCategory() {
            setupSecurityContextWithAdmin();
            Pageable pageable = PageRequest.of(0, 20);
            Page<SupportTicketDtoOut> expectedPage = new PageImpl<>(Collections.emptyList());
            
            when(ticketService.findTickets(any(), eq(TicketCategory.BILLING_PAYMENT), any(), any())).thenReturn(expectedPage);

            controller.getTickets(null, TicketCategory.BILLING_PAYMENT, null, pageable);

            verify(ticketService).findTickets(isNull(), eq(TicketCategory.BILLING_PAYMENT), isNull(), eq(pageable));
        }

        @Test
        @DisplayName("should filter tickets by search query")
        void shouldFilterTicketsBySearchQuery() {
            setupSecurityContextWithAdmin();
            Pageable pageable = PageRequest.of(0, 20);
            Page<SupportTicketDtoOut> expectedPage = new PageImpl<>(Collections.emptyList());
            String searchQuery = "login issue";
            
            when(ticketService.findTickets(any(), any(), eq(searchQuery), any())).thenReturn(expectedPage);

            controller.getTickets(null, null, searchQuery, pageable);

            verify(ticketService).findTickets(isNull(), isNull(), eq(searchQuery), eq(pageable));
        }

        @Test
        @DisplayName("should filter tickets by all criteria")
        void shouldFilterTicketsByAllCriteria() {
            setupSecurityContextWithAdmin();
            Pageable pageable = PageRequest.of(0, 10);
            Page<SupportTicketDtoOut> expectedPage = new PageImpl<>(Collections.emptyList());
            
            when(ticketService.findTickets(
                    eq(TicketStatus.IN_PROGRESS), 
                    eq(TicketCategory.ACCOUNT_ISSUE), 
                    eq("password"), 
                    any())).thenReturn(expectedPage);

            controller.getTickets(TicketStatus.IN_PROGRESS, TicketCategory.ACCOUNT_ISSUE, "password", pageable);

            verify(ticketService).findTickets(
                    eq(TicketStatus.IN_PROGRESS), 
                    eq(TicketCategory.ACCOUNT_ISSUE), 
                    eq("password"), 
                    eq(pageable));
        }

        @Test
        @DisplayName("should throw exception when authentication is null for admin endpoint")
        void shouldThrowExceptionWhenAuthenticationIsNullForAdminEndpoint() {
            when(securityContext.getAuthentication()).thenReturn(null);
            Pageable pageable = PageRequest.of(0, 20);

            assertThatThrownBy(() -> controller.getTickets(null, null, null, pageable))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw exception when principal is null for admin endpoint")
        void shouldThrowExceptionWhenPrincipalIsNullForAdminEndpoint() {
            when(authentication.getPrincipal()).thenReturn(null);
            Pageable pageable = PageRequest.of(0, 20);

            assertThatThrownBy(() -> controller.getTickets(null, null, null, pageable))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    // ==================== AddAdminResponse Endpoint Tests ====================

    @Nested
    @DisplayName("POST /support/ticket/{ticketId}/admin-response - addAdminResponse")
    class AddAdminResponseTests {

        @Test
        @DisplayName("should add admin response successfully")
        void shouldAddAdminResponseSuccessfully() {
            setupSecurityContextWithAdmin();
            TicketResponseDtoOut adminResponseOut = new TicketResponseDtoOut();
            adminResponseOut.setId(20L);
            adminResponseOut.setContent("Admin response content");
            adminResponseOut.setFromAdmin(true);
            adminResponseOut.setAdminName("Admin User");
            
            when(ticketService.addAdminResponse(eq(TEST_TICKET_ID), any(AdminTicketResponseDtoIn.class)))
                    .thenReturn(adminResponseOut);

            ResponseEntity<TicketResponseDtoOut> response = controller.addAdminResponse(
                    TEST_TICKET_ID, testAdminResponseDtoIn);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isFromAdmin()).isTrue();
            assertThat(response.getBody().getAdminName()).isEqualTo("Admin User");
            verify(ticketService).addAdminResponse(TEST_TICKET_ID, testAdminResponseDtoIn);
        }

        @Test
        @DisplayName("should throw exception when authentication is null for admin response")
        void shouldThrowExceptionWhenAuthenticationIsNullForAdminResponse() {
            when(securityContext.getAuthentication()).thenReturn(null);

            assertThatThrownBy(() -> controller.addAdminResponse(TEST_TICKET_ID, testAdminResponseDtoIn))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw exception when principal is null for admin response")
        void shouldThrowExceptionWhenPrincipalIsNullForAdminResponse() {
            when(authentication.getPrincipal()).thenReturn(null);

            assertThatThrownBy(() -> controller.addAdminResponse(TEST_TICKET_ID, testAdminResponseDtoIn))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should handle admin response with status change")
        void shouldHandleAdminResponseWithStatusChange() {
            setupSecurityContextWithAdmin();
            testAdminResponseDtoIn.setNewStatus(TicketStatus.RESOLVED);
            
            TicketResponseDtoOut adminResponseOut = new TicketResponseDtoOut();
            adminResponseOut.setId(20L);
            adminResponseOut.setFromAdmin(true);
            
            when(ticketService.addAdminResponse(eq(TEST_TICKET_ID), any(AdminTicketResponseDtoIn.class)))
                    .thenReturn(adminResponseOut);

            controller.addAdminResponse(TEST_TICKET_ID, testAdminResponseDtoIn);

            verify(ticketService).addAdminResponse(eq(TEST_TICKET_ID), argThat(dto -> 
                dto.getNewStatus() == TicketStatus.RESOLVED
            ));
        }

        @Test
        @DisplayName("should handle admin response without email notification")
        void shouldHandleAdminResponseWithoutEmailNotification() {
            setupSecurityContextWithAdmin();
            testAdminResponseDtoIn.setSendEmail(false);
            
            TicketResponseDtoOut adminResponseOut = new TicketResponseDtoOut();
            adminResponseOut.setId(20L);
            adminResponseOut.setFromAdmin(true);
            
            when(ticketService.addAdminResponse(eq(TEST_TICKET_ID), any(AdminTicketResponseDtoIn.class)))
                    .thenReturn(adminResponseOut);

            controller.addAdminResponse(TEST_TICKET_ID, testAdminResponseDtoIn);

            verify(ticketService).addAdminResponse(eq(TEST_TICKET_ID), argThat(dto -> 
                !dto.isSendEmail()
            ));
        }
    }

    // ==================== UpdateTicketStatus Endpoint Tests ====================

    @Nested
    @DisplayName("PATCH /support/ticket/{ticketId}/status - updateTicketStatus")
    class UpdateTicketStatusTests {

        @Test
        @DisplayName("should update ticket status successfully")
        void shouldUpdateTicketStatusSuccessfully() {
            setupSecurityContextWithAdmin();
            SupportTicketDtoOut updatedTicket = new SupportTicketDtoOut();
            updatedTicket.setId(TEST_TICKET_ID);
            updatedTicket.setStatus(TicketStatus.IN_PROGRESS);
            updatedTicket.setStatusDisplay("In Progress");
            
            when(ticketService.updateTicketStatus(TEST_TICKET_ID, TicketStatus.IN_PROGRESS))
                    .thenReturn(updatedTicket);

            ResponseEntity<SupportTicketDtoOut> response = controller.updateTicketStatus(
                    TEST_TICKET_ID, TicketStatus.IN_PROGRESS);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            verify(ticketService).updateTicketStatus(TEST_TICKET_ID, TicketStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("should throw exception when authentication is null for status update")
        void shouldThrowExceptionWhenAuthenticationIsNullForStatusUpdate() {
            when(securityContext.getAuthentication()).thenReturn(null);

            assertThatThrownBy(() -> controller.updateTicketStatus(TEST_TICKET_ID, TicketStatus.CLOSED))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw exception when principal is null for status update")
        void shouldThrowExceptionWhenPrincipalIsNullForStatusUpdate() {
            when(authentication.getPrincipal()).thenReturn(null);

            assertThatThrownBy(() -> controller.updateTicketStatus(TEST_TICKET_ID, TicketStatus.CLOSED))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @ParameterizedTest
        @EnumSource(TicketStatus.class)
        @DisplayName("should handle all status values")
        void shouldHandleAllStatusValues(TicketStatus status) {
            setupSecurityContextWithAdmin();
            SupportTicketDtoOut updatedTicket = new SupportTicketDtoOut();
            updatedTicket.setId(TEST_TICKET_ID);
            updatedTicket.setStatus(status);
            
            when(ticketService.updateTicketStatus(TEST_TICKET_ID, status)).thenReturn(updatedTicket);

            ResponseEntity<SupportTicketDtoOut> response = controller.updateTicketStatus(TEST_TICKET_ID, status);

            assertThat(response.getBody().getStatus()).isEqualTo(status);
        }
    }

    // ==================== TicketStatus Enum Tests ====================

    @Nested
    @DisplayName("TicketStatus Enum Tests")
    class TicketStatusEnumTests {

        @Test
        @DisplayName("should have correct display names")
        void shouldHaveCorrectDisplayNames() {
            assertThat(TicketStatus.OPEN.getDisplayName()).isEqualTo("Open");
            assertThat(TicketStatus.IN_PROGRESS.getDisplayName()).isEqualTo("In Progress");
            assertThat(TicketStatus.WAITING_FOR_CUSTOMER.getDisplayName()).isEqualTo("Awaiting Your Reply");
            assertThat(TicketStatus.RESOLVED.getDisplayName()).isEqualTo("Resolved");
            assertThat(TicketStatus.CLOSED.getDisplayName()).isEqualTo("Closed");
        }

        @Test
        @DisplayName("OPEN can transition to IN_PROGRESS")
        void openCanTransitionToInProgress() {
            assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.IN_PROGRESS)).isTrue();
        }

        @Test
        @DisplayName("OPEN can transition to RESOLVED")
        void openCanTransitionToResolved() {
            assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.RESOLVED)).isTrue();
        }

        @Test
        @DisplayName("OPEN can transition to CLOSED")
        void openCanTransitionToClosed() {
            assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.CLOSED)).isTrue();
        }

        @Test
        @DisplayName("OPEN cannot transition to WAITING_FOR_CUSTOMER")
        void openCannotTransitionToWaitingForCustomer() {
            assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.WAITING_FOR_CUSTOMER)).isFalse();
        }

        @Test
        @DisplayName("IN_PROGRESS can transition to WAITING_FOR_CUSTOMER")
        void inProgressCanTransitionToWaitingForCustomer() {
            assertThat(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.WAITING_FOR_CUSTOMER)).isTrue();
        }

        @Test
        @DisplayName("IN_PROGRESS can transition to RESOLVED")
        void inProgressCanTransitionToResolved() {
            assertThat(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.RESOLVED)).isTrue();
        }

        @Test
        @DisplayName("IN_PROGRESS can transition to CLOSED")
        void inProgressCanTransitionToClosed() {
            assertThat(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.CLOSED)).isTrue();
        }

        @Test
        @DisplayName("WAITING_FOR_CUSTOMER can transition to IN_PROGRESS")
        void waitingForCustomerCanTransitionToInProgress() {
            assertThat(TicketStatus.WAITING_FOR_CUSTOMER.canTransitionTo(TicketStatus.IN_PROGRESS)).isTrue();
        }

        @Test
        @DisplayName("WAITING_FOR_CUSTOMER can transition to RESOLVED")
        void waitingForCustomerCanTransitionToResolved() {
            assertThat(TicketStatus.WAITING_FOR_CUSTOMER.canTransitionTo(TicketStatus.RESOLVED)).isTrue();
        }

        @Test
        @DisplayName("WAITING_FOR_CUSTOMER can transition to CLOSED")
        void waitingForCustomerCanTransitionToClosed() {
            assertThat(TicketStatus.WAITING_FOR_CUSTOMER.canTransitionTo(TicketStatus.CLOSED)).isTrue();
        }

        @Test
        @DisplayName("RESOLVED can transition to IN_PROGRESS")
        void resolvedCanTransitionToInProgress() {
            assertThat(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.IN_PROGRESS)).isTrue();
        }

        @Test
        @DisplayName("RESOLVED can transition to CLOSED")
        void resolvedCanTransitionToClosed() {
            assertThat(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.CLOSED)).isTrue();
        }

        @Test
        @DisplayName("RESOLVED can transition to WAITING_FOR_CUSTOMER")
        void resolvedCanTransitionToWaitingForCustomer() {
            assertThat(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.WAITING_FOR_CUSTOMER)).isTrue();
        }

        @Test
        @DisplayName("CLOSED cannot transition to any status")
        void closedCannotTransitionToAnyStatus() {
            for (TicketStatus status : TicketStatus.values()) {
                assertThat(TicketStatus.CLOSED.canTransitionTo(status)).isFalse();
            }
        }
    }

    // ==================== TicketCategory Enum Tests ====================

    @Nested
    @DisplayName("TicketCategory Enum Tests")
    class TicketCategoryEnumTests {

        @Test
        @DisplayName("should have correct display names")
        void shouldHaveCorrectDisplayNames() {
            assertThat(TicketCategory.ACCOUNT_ISSUE.getDisplayName()).isEqualTo("Account Issue");
            assertThat(TicketCategory.BILLING_PAYMENT.getDisplayName()).isEqualTo("Billing & Payment");
            assertThat(TicketCategory.TECHNICAL_PROBLEM.getDisplayName()).isEqualTo("Technical Problem");
            assertThat(TicketCategory.FEATURE_REQUEST.getDisplayName()).isEqualTo("Feature Request");
            assertThat(TicketCategory.PARTNERSHIP_ISSUE.getDisplayName()).isEqualTo("Partnership Issue");
            assertThat(TicketCategory.CONTENT_MODERATION.getDisplayName()).isEqualTo("Content Moderation");
            assertThat(TicketCategory.GENERAL_INQUIRY.getDisplayName()).isEqualTo("General Inquiry");
            assertThat(TicketCategory.EARLY_ACCESS_INTEREST.getDisplayName()).isEqualTo("Early Access Interest");
            assertThat(TicketCategory.OTHER.getDisplayName()).isEqualTo("Other");
        }

        @Test
        @DisplayName("should have all expected values")
        void shouldHaveAllExpectedValues() {
            assertThat(TicketCategory.values()).hasSize(9);
        }

        @ParameterizedTest
        @EnumSource(TicketCategory.class)
        @DisplayName("each category should have a non-empty display name")
        void eachCategoryShouldHaveNonEmptyDisplayName(TicketCategory category) {
            assertThat(category.getDisplayName()).isNotBlank();
        }
    }

    // ==================== SupportTicket Entity Tests ====================

    @Nested
    @DisplayName("SupportTicket Entity Tests")
    class SupportTicketEntityTests {

        @Test
        @DisplayName("should create ticket with default status OPEN")
        void shouldCreateTicketWithDefaultStatusOpen() {
            SupportTicket ticket = new SupportTicket();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        }

        @Test
        @DisplayName("should set and get all ticket properties")
        void shouldSetAndGetAllTicketProperties() {
            LocalDateTime now = LocalDateTime.now();
            SupportTicket ticket = new SupportTicket();
            
            ticket.setId(1L);
            ticket.setContactEmail("user@example.com");
            ticket.setSubject("Test Subject");
            ticket.setDescription("Test Description");
            ticket.setTechnicalDescription("Technical Details");
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setCategory(TicketCategory.TECHNICAL_PROBLEM);
            ticket.setIpAddress("192.168.1.1");
            ticket.setTicketReference("CIO-20241201-0001");
            ticket.setAdminAssignee("admin@example.com");
            ticket.setCreatedTime(now);
            ticket.setLastUpdateTime(now);
            ticket.setResolvedTime(now);
            ticket.setUpdaterId("updater123");

            assertThat(ticket.getId()).isEqualTo(1L);
            assertThat(ticket.getContactEmail()).isEqualTo("user@example.com");
            assertThat(ticket.getSubject()).isEqualTo("Test Subject");
            assertThat(ticket.getDescription()).isEqualTo("Test Description");
            assertThat(ticket.getTechnicalDescription()).isEqualTo("Technical Details");
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(ticket.getCategory()).isEqualTo(TicketCategory.TECHNICAL_PROBLEM);
            assertThat(ticket.getIpAddress()).isEqualTo("192.168.1.1");
            assertThat(ticket.getTicketReference()).isEqualTo("CIO-20241201-0001");
            assertThat(ticket.getAdminAssignee()).isEqualTo("admin@example.com");
            assertThat(ticket.getCreatedTime()).isEqualTo(now);
            assertThat(ticket.getLastUpdateTime()).isEqualTo(now);
            assertThat(ticket.getResolvedTime()).isEqualTo(now);
            assertThat(ticket.getUpdaterId()).isEqualTo("updater123");
        }

        @Test
        @DisplayName("should identify resolved ticket correctly")
        void shouldIdentifyResolvedTicketCorrectly() {
            SupportTicket ticket = new SupportTicket();
            
            ticket.setStatus(TicketStatus.OPEN);
            assertThat(ticket.isResolved()).isFalse();
            
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            assertThat(ticket.isResolved()).isFalse();
            
            ticket.setStatus(TicketStatus.WAITING_FOR_CUSTOMER);
            assertThat(ticket.isResolved()).isFalse();
            
            ticket.setStatus(TicketStatus.RESOLVED);
            assertThat(ticket.isResolved()).isTrue();
            
            ticket.setStatus(TicketStatus.CLOSED);
            assertThat(ticket.isResolved()).isTrue();
        }

        @Test
        @DisplayName("should add response to ticket")
        void shouldAddResponseToTicket() {
            SupportTicket ticket = new SupportTicket();
            TicketResponse response = new TicketResponse();
            response.setContent("Test response");

            ticket.addResponse(response);

            assertThat(ticket.getResponses()).hasSize(1);
            assertThat(ticket.getResponses().get(0).getContent()).isEqualTo("Test response");
            assertThat(response.getTicket()).isEqualTo(ticket);
        }

        @Test
        @DisplayName("should add attachment to ticket")
        void shouldAddAttachmentToTicket() {
            SupportTicket ticket = new SupportTicket();
            TicketAttachment attachment = new TicketAttachment();
            attachment.setFileName("test.pdf");

            ticket.addAttachment(attachment);

            assertThat(ticket.getAttachments()).hasSize(1);
            assertThat(ticket.getAttachments().get(0).getFileName()).isEqualTo("test.pdf");
            assertThat(attachment.getTicket()).isEqualTo(ticket);
        }

        @Test
        @DisplayName("should add multiple responses")
        void shouldAddMultipleResponses() {
            SupportTicket ticket = new SupportTicket();
            
            TicketResponse response1 = new TicketResponse();
            response1.setContent("Response 1");
            ticket.addResponse(response1);
            
            TicketResponse response2 = new TicketResponse();
            response2.setContent("Response 2");
            ticket.addResponse(response2);

            assertThat(ticket.getResponses()).hasSize(2);
        }

        @Test
        @DisplayName("should add multiple attachments")
        void shouldAddMultipleAttachments() {
            SupportTicket ticket = new SupportTicket();
            
            TicketAttachment attachment1 = new TicketAttachment();
            attachment1.setFileName("file1.pdf");
            ticket.addAttachment(attachment1);
            
            TicketAttachment attachment2 = new TicketAttachment();
            attachment2.setFileName("file2.pdf");
            ticket.addAttachment(attachment2);

            assertThat(ticket.getAttachments()).hasSize(2);
        }
    }

    // ==================== TicketResponse Entity Tests ====================

    @Nested
    @DisplayName("TicketResponse Entity Tests")
    class TicketResponseEntityTests {

        @Test
        @DisplayName("should set and get all response properties")
        void shouldSetAndGetAllResponseProperties() {
            LocalDateTime now = LocalDateTime.now();
            TicketResponse response = new TicketResponse();
            
            response.setId(1L);
            response.setContent("Response content");
            response.setFromAdmin(true);
            response.setAdminName("Admin User");
            response.setEmailSent(true);
            response.setCreatedTime(now);
            response.setTicket(testTicket);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getContent()).isEqualTo("Response content");
            assertThat(response.isFromAdmin()).isTrue();
            assertThat(response.getAdminName()).isEqualTo("Admin User");
            assertThat(response.isEmailSent()).isTrue();
            assertThat(response.getCreatedTime()).isEqualTo(now);
            assertThat(response.getTicket()).isEqualTo(testTicket);
        }

        @Test
        @DisplayName("should add attachment to response")
        void shouldAddAttachmentToResponse() {
            TicketResponse response = new TicketResponse();
            ResponseAttachment attachment = new ResponseAttachment();
            attachment.setFileName("response-attachment.pdf");

            response.addAttachment(attachment);

            assertThat(response.getAttachments()).hasSize(1);
            assertThat(response.getAttachments().get(0).getFileName()).isEqualTo("response-attachment.pdf");
            assertThat(attachment.getResponse()).isEqualTo(response);
        }

        @Test
        @DisplayName("should handle customer response (not from admin)")
        void shouldHandleCustomerResponse() {
            TicketResponse response = new TicketResponse();
            response.setFromAdmin(false);
            response.setContent("Customer reply");

            assertThat(response.isFromAdmin()).isFalse();
            assertThat(response.getAdminName()).isNull();
        }
    }

    // ==================== TicketAttachment Entity Tests ====================

    @Nested
    @DisplayName("TicketAttachment Entity Tests")
    class TicketAttachmentEntityTests {

        @Test
        @DisplayName("should set and get all attachment properties")
        void shouldSetAndGetAllAttachmentProperties() {
            LocalDateTime now = LocalDateTime.now();
            TicketAttachment attachment = new TicketAttachment();
            
            attachment.setId(1L);
            attachment.setFileName("test-file.pdf");
            attachment.setContentType("application/pdf");
            attachment.setFileUrl("https://storage.example.com/test-file.pdf");
            attachment.setFileSize(2048L);
            attachment.setUploadTime(now);
            attachment.setTicket(testTicket);

            assertThat(attachment.getId()).isEqualTo(1L);
            assertThat(attachment.getFileName()).isEqualTo("test-file.pdf");
            assertThat(attachment.getContentType()).isEqualTo("application/pdf");
            assertThat(attachment.getFileUrl()).isEqualTo("https://storage.example.com/test-file.pdf");
            assertThat(attachment.getFileSize()).isEqualTo(2048L);
            assertThat(attachment.getUploadTime()).isEqualTo(now);
            assertThat(attachment.getTicket()).isEqualTo(testTicket);
        }
    }

    // ==================== ResponseAttachment Entity Tests ====================

    @Nested
    @DisplayName("ResponseAttachment Entity Tests")
    class ResponseAttachmentEntityTests {

        @Test
        @DisplayName("should set and get all response attachment properties")
        void shouldSetAndGetAllResponseAttachmentProperties() {
            LocalDateTime now = LocalDateTime.now();
            TicketResponse response = new TicketResponse();
            response.setId(10L);
            
            ResponseAttachment attachment = new ResponseAttachment();
            
            attachment.setId(1L);
            attachment.setFileName("response-file.png");
            attachment.setContentType("image/png");
            attachment.setFileUrl("https://storage.example.com/response-file.png");
            attachment.setFileSize(4096L);
            attachment.setUploadTime(now);
            attachment.setResponse(response);

            assertThat(attachment.getId()).isEqualTo(1L);
            assertThat(attachment.getFileName()).isEqualTo("response-file.png");
            assertThat(attachment.getContentType()).isEqualTo("image/png");
            assertThat(attachment.getFileUrl()).isEqualTo("https://storage.example.com/response-file.png");
            assertThat(attachment.getFileSize()).isEqualTo(4096L);
            assertThat(attachment.getUploadTime()).isEqualTo(now);
            assertThat(attachment.getResponse()).isEqualTo(response);
        }
    }

    // ==================== DTO Tests ====================

    @Nested
    @DisplayName("SupportTicketDtoIn Tests")
    class SupportTicketDtoInTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            SupportTicketDtoIn dto = new SupportTicketDtoIn();
            
            dto.setContactEmail("test@example.com");
            dto.setSubject("Test Subject");
            dto.setDescription("Test Description");
            dto.setCategory(TicketCategory.ACCOUNT_ISSUE);
            dto.setTechnicalDescription("Technical info");

            assertThat(dto.getContactEmail()).isEqualTo("test@example.com");
            assertThat(dto.getSubject()).isEqualTo("Test Subject");
            assertThat(dto.getDescription()).isEqualTo("Test Description");
            assertThat(dto.getCategory()).isEqualTo(TicketCategory.ACCOUNT_ISSUE);
            assertThat(dto.getTechnicalDescription()).isEqualTo("Technical info");
        }

        @Test
        @DisplayName("should create with all args constructor")
        void shouldCreateWithAllArgsConstructor() {
            SupportTicketDtoIn dto = new SupportTicketDtoIn(
                    "email@test.com",
                    "Subject",
                    "Description",
                    TicketCategory.BILLING_PAYMENT,
                    "Tech details"
            );

            assertThat(dto.getContactEmail()).isEqualTo("email@test.com");
            assertThat(dto.getSubject()).isEqualTo("Subject");
            assertThat(dto.getDescription()).isEqualTo("Description");
            assertThat(dto.getCategory()).isEqualTo(TicketCategory.BILLING_PAYMENT);
            assertThat(dto.getTechnicalDescription()).isEqualTo("Tech details");
        }
    }

    @Nested
    @DisplayName("SupportTicketDtoOut Tests")
    class SupportTicketDtoOutTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            LocalDateTime now = LocalDateTime.now();
            SupportTicketDtoOut dto = new SupportTicketDtoOut();
            
            dto.setId(1L);
            dto.setContactEmail("test@example.com");
            dto.setSubject("Test Subject");
            dto.setDescription("Test Description");
            dto.setStatus(TicketStatus.OPEN);
            dto.setStatusDisplay("Open");
            dto.setCategory(TicketCategory.TECHNICAL_PROBLEM);
            dto.setCategoryDisplay("Technical Problem");
            dto.setTicketReference("CIO-20241201-0001");
            dto.setAdminAssignee("admin@example.com");
            dto.setCreatedTime(now);
            dto.setLastUpdateTime(now);
            dto.setResolvedTime(now);
            dto.setResponses(new ArrayList<>());
            dto.setAttachments(new ArrayList<>());
            dto.setResolved(true);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getContactEmail()).isEqualTo("test@example.com");
            assertThat(dto.getSubject()).isEqualTo("Test Subject");
            assertThat(dto.getDescription()).isEqualTo("Test Description");
            assertThat(dto.getStatus()).isEqualTo(TicketStatus.OPEN);
            assertThat(dto.getStatusDisplay()).isEqualTo("Open");
            assertThat(dto.getCategory()).isEqualTo(TicketCategory.TECHNICAL_PROBLEM);
            assertThat(dto.getCategoryDisplay()).isEqualTo("Technical Problem");
            assertThat(dto.getTicketReference()).isEqualTo("CIO-20241201-0001");
            assertThat(dto.getAdminAssignee()).isEqualTo("admin@example.com");
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
            assertThat(dto.getResolvedTime()).isEqualTo(now);
            assertThat(dto.getResponses()).isEmpty();
            assertThat(dto.getAttachments()).isEmpty();
            assertThat(dto.isResolved()).isTrue();
        }

        @Test
        @DisplayName("should have empty response list by default")
        void shouldHaveEmptyResponseListByDefault() {
            SupportTicketDtoOut dto = new SupportTicketDtoOut();
            assertThat(dto.getResponses()).isEmpty();
        }

        @Test
        @DisplayName("should have empty attachment list by default")
        void shouldHaveEmptyAttachmentListByDefault() {
            SupportTicketDtoOut dto = new SupportTicketDtoOut();
            assertThat(dto.getAttachments()).isEmpty();
        }
    }

    @Nested
    @DisplayName("TicketResponseDtoIn Tests")
    class TicketResponseDtoInTests {

        @Test
        @DisplayName("should set and get content")
        void shouldSetAndGetContent() {
            TicketResponseDtoIn dto = new TicketResponseDtoIn();
            dto.setContent("Test content");
            assertThat(dto.getContent()).isEqualTo("Test content");
        }

        @Test
        @DisplayName("should create with all args constructor")
        void shouldCreateWithAllArgsConstructor() {
            TicketResponseDtoIn dto = new TicketResponseDtoIn("Response content");
            assertThat(dto.getContent()).isEqualTo("Response content");
        }
    }

    @Nested
    @DisplayName("TicketResponseDtoOut Tests")
    class TicketResponseDtoOutTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            LocalDateTime now = LocalDateTime.now();
            TicketResponseDtoOut dto = new TicketResponseDtoOut();
            
            dto.setId(1L);
            dto.setTicketId(10L);
            dto.setContent("Response content");
            dto.setFromAdmin(true);
            dto.setAdminName("Admin User");
            dto.setCreatedTime(now);
            dto.setAttachments(new ArrayList<>());

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getTicketId()).isEqualTo(10L);
            assertThat(dto.getContent()).isEqualTo("Response content");
            assertThat(dto.isFromAdmin()).isTrue();
            assertThat(dto.getAdminName()).isEqualTo("Admin User");
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getAttachments()).isEmpty();
        }

        @Test
        @DisplayName("should have empty attachment list by default")
        void shouldHaveEmptyAttachmentListByDefault() {
            TicketResponseDtoOut dto = new TicketResponseDtoOut();
            assertThat(dto.getAttachments()).isEmpty();
        }
    }

    @Nested
    @DisplayName("AdminTicketResponseDtoIn Tests")
    class AdminTicketResponseDtoInTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            AdminTicketResponseDtoIn dto = new AdminTicketResponseDtoIn();
            
            dto.setContent("Admin response");
            dto.setNewStatus(TicketStatus.RESOLVED);
            dto.setAdminName("Admin Name");
            dto.setSendEmail(false);

            assertThat(dto.getContent()).isEqualTo("Admin response");
            assertThat(dto.getNewStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(dto.getAdminName()).isEqualTo("Admin Name");
            assertThat(dto.isSendEmail()).isFalse();
        }

        @Test
        @DisplayName("should default sendEmail to true")
        void shouldDefaultSendEmailToTrue() {
            AdminTicketResponseDtoIn dto = new AdminTicketResponseDtoIn();
            assertThat(dto.isSendEmail()).isTrue();
        }

        @Test
        @DisplayName("should extend TicketResponseDtoIn")
        void shouldExtendTicketResponseDtoIn() {
            AdminTicketResponseDtoIn dto = new AdminTicketResponseDtoIn();
            assertThat(dto).isInstanceOf(TicketResponseDtoIn.class);
        }
    }

    @Nested
    @DisplayName("TicketAttachmentDtoIn Tests")
    class TicketAttachmentDtoInTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            TicketAttachmentDtoIn dto = new TicketAttachmentDtoIn();
            
            dto.setFileName("document.pdf");
            dto.setContentType("application/pdf");
            dto.setFileUrl("https://storage.example.com/document.pdf");
            dto.setFileSize(5000L);

            assertThat(dto.getFileName()).isEqualTo("document.pdf");
            assertThat(dto.getContentType()).isEqualTo("application/pdf");
            assertThat(dto.getFileUrl()).isEqualTo("https://storage.example.com/document.pdf");
            assertThat(dto.getFileSize()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("should create with all args constructor")
        void shouldCreateWithAllArgsConstructor() {
            TicketAttachmentDtoIn dto = new TicketAttachmentDtoIn(
                    "file.jpg",
                    "image/jpeg",
                    "https://storage.example.com/file.jpg",
                    1024L
            );

            assertThat(dto.getFileName()).isEqualTo("file.jpg");
            assertThat(dto.getContentType()).isEqualTo("image/jpeg");
            assertThat(dto.getFileUrl()).isEqualTo("https://storage.example.com/file.jpg");
            assertThat(dto.getFileSize()).isEqualTo(1024L);
        }
    }

    @Nested
    @DisplayName("TicketAttachmentDtoOut Tests")
    class TicketAttachmentDtoOutTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            LocalDateTime now = LocalDateTime.now();
            TicketAttachmentDtoOut dto = new TicketAttachmentDtoOut();
            
            dto.setId(1L);
            dto.setTicketId(10L);
            dto.setFileName("document.pdf");
            dto.setContentType("application/pdf");
            dto.setFileSize(5000L);
            dto.setUploadTime(now);
            dto.setDownloadUrl("https://storage.example.com/document.pdf");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getTicketId()).isEqualTo(10L);
            assertThat(dto.getFileName()).isEqualTo("document.pdf");
            assertThat(dto.getContentType()).isEqualTo("application/pdf");
            assertThat(dto.getFileSize()).isEqualTo(5000L);
            assertThat(dto.getUploadTime()).isEqualTo(now);
            assertThat(dto.getDownloadUrl()).isEqualTo("https://storage.example.com/document.pdf");
        }
    }

    @Nested
    @DisplayName("ResponseAttachmentDtoIn Tests")
    class ResponseAttachmentDtoInTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            ResponseAttachmentDtoIn dto = new ResponseAttachmentDtoIn();
            
            dto.setFileName("screenshot.png");
            dto.setContentType("image/png");
            dto.setFileUrl("https://storage.example.com/screenshot.png");
            dto.setFileSize(2048L);

            assertThat(dto.getFileName()).isEqualTo("screenshot.png");
            assertThat(dto.getContentType()).isEqualTo("image/png");
            assertThat(dto.getFileUrl()).isEqualTo("https://storage.example.com/screenshot.png");
            assertThat(dto.getFileSize()).isEqualTo(2048L);
        }

        @Test
        @DisplayName("should create with all args constructor")
        void shouldCreateWithAllArgsConstructor() {
            ResponseAttachmentDtoIn dto = new ResponseAttachmentDtoIn(
                    "image.gif",
                    "image/gif",
                    "https://storage.example.com/image.gif",
                    512L
            );

            assertThat(dto.getFileName()).isEqualTo("image.gif");
            assertThat(dto.getContentType()).isEqualTo("image/gif");
            assertThat(dto.getFileUrl()).isEqualTo("https://storage.example.com/image.gif");
            assertThat(dto.getFileSize()).isEqualTo(512L);
        }
    }

    @Nested
    @DisplayName("ResponseAttachmentDtoOut Tests")
    class ResponseAttachmentDtoOutTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            LocalDateTime now = LocalDateTime.now();
            ResponseAttachmentDtoOut dto = new ResponseAttachmentDtoOut();
            
            dto.setId(1L);
            dto.setResponseId(10L);
            dto.setFileName("screenshot.png");
            dto.setContentType("image/png");
            dto.setFileSize(2048L);
            dto.setUploadTime(now);
            dto.setDownloadUrl("https://storage.example.com/screenshot.png");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getResponseId()).isEqualTo(10L);
            assertThat(dto.getFileName()).isEqualTo("screenshot.png");
            assertThat(dto.getContentType()).isEqualTo("image/png");
            assertThat(dto.getFileSize()).isEqualTo(2048L);
            assertThat(dto.getUploadTime()).isEqualTo(now);
            assertThat(dto.getDownloadUrl()).isEqualTo("https://storage.example.com/screenshot.png");
        }
    }

    // ==================== TicketReferenceService Tests ====================

    @Nested
    @DisplayName("TicketReferenceService Tests")
    class TicketReferenceServiceTests {

        private TicketReferenceService referenceService;

        @BeforeEach
        void setUp() {
            referenceService = new TicketReferenceService();
        }

        @Test
        @DisplayName("should generate reference with correct prefix")
        void shouldGenerateReferenceWithCorrectPrefix() {
            String reference = referenceService.generateTicketReference();
            assertThat(reference).startsWith("CIO-");
        }

        @Test
        @DisplayName("should generate reference with date part")
        void shouldGenerateReferenceWithDatePart() {
            String reference = referenceService.generateTicketReference();
            // Reference format: CIO-YYYYMMDD-XXXX
            assertThat(reference).matches("CIO-\\d{8}-\\d{4}");
        }

        @Test
        @DisplayName("should generate unique references")
        void shouldGenerateUniqueReferences() {
            Set<String> references = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                references.add(referenceService.generateTicketReference());
            }
            // While not guaranteed to be unique, statistically should be mostly unique
            assertThat(references.size()).isGreaterThan(90);
        }

        @Test
        @DisplayName("should generate reference with correct format")
        void shouldGenerateReferenceWithCorrectFormat() {
            String reference = referenceService.generateTicketReference();
            String[] parts = reference.split("-");
            
            assertThat(parts).hasSize(3);
            assertThat(parts[0]).isEqualTo("CIO");
            assertThat(parts[1]).hasSize(8); // YYYYMMDD
            assertThat(parts[2]).hasSize(4); // XXXX
        }

        @Test
        @DisplayName("should generate reference not exceeding max length")
        void shouldGenerateReferenceNotExceedingMaxLength() {
            String reference = referenceService.generateTicketReference();
            assertThat(reference.length()).isLessThanOrEqualTo(20);
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle null IP address gracefully")
        void shouldHandleNullIpAddressGracefully() {
            when(httpServletRequest.getRemoteAddr()).thenReturn(null);
            when(ticketService.createTicket(any(), isNull())).thenReturn(testTicket);
            when(ticketService.convertToDto(testTicket)).thenReturn(testTicketDtoOut);

            ResponseEntity<SupportTicketDtoOut> response = controller.createTicket(testTicketDtoIn, httpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(ticketService).createTicket(any(), isNull());
        }

        @Test
        @DisplayName("should handle very long subject")
        void shouldHandleVeryLongSubject() {
            String longSubject = "A".repeat(255);
            testTicketDtoIn.setSubject(longSubject);
            
            when(httpServletRequest.getRemoteAddr()).thenReturn(TEST_IP);
            when(ticketService.createTicket(any(), anyString())).thenReturn(testTicket);
            when(ticketService.convertToDto(testTicket)).thenReturn(testTicketDtoOut);

            ResponseEntity<SupportTicketDtoOut> response = controller.createTicket(testTicketDtoIn, httpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle very long description")
        void shouldHandleVeryLongDescription() {
            String longDescription = "B".repeat(5000);
            testTicketDtoIn.setDescription(longDescription);
            
            when(httpServletRequest.getRemoteAddr()).thenReturn(TEST_IP);
            when(ticketService.createTicket(any(), anyString())).thenReturn(testTicket);
            when(ticketService.convertToDto(testTicket)).thenReturn(testTicketDtoOut);

            ResponseEntity<SupportTicketDtoOut> response = controller.createTicket(testTicketDtoIn, httpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle empty search query")
        void shouldHandleEmptySearchQuery() {
            setupSecurityContextWithAdmin();
            Pageable pageable = PageRequest.of(0, 20);
            Page<SupportTicketDtoOut> expectedPage = new PageImpl<>(Collections.emptyList());
            
            when(ticketService.findTickets(any(), any(), eq(""), any())).thenReturn(expectedPage);

            controller.getTickets(null, null, "", pageable);

            verify(ticketService).findTickets(isNull(), isNull(), eq(""), eq(pageable));
        }

        @Test
        @DisplayName("should handle special characters in search query")
        void shouldHandleSpecialCharactersInSearchQuery() {
            setupSecurityContextWithAdmin();
            Pageable pageable = PageRequest.of(0, 20);
            Page<SupportTicketDtoOut> expectedPage = new PageImpl<>(Collections.emptyList());
            String specialQuery = "test@#$%^&*()";
            
            when(ticketService.findTickets(any(), any(), eq(specialQuery), any())).thenReturn(expectedPage);

            controller.getTickets(null, null, specialQuery, pageable);

            verify(ticketService).findTickets(isNull(), isNull(), eq(specialQuery), eq(pageable));
        }

        @Test
        @DisplayName("should handle zero file size in attachment")
        void shouldHandleZeroFileSizeInAttachment() {
            testAttachmentDtoIn.setFileSize(0L);
            List<TicketAttachmentDtoIn> attachments = Collections.singletonList(testAttachmentDtoIn);
            
            when(ticketService.addTicketAttachments(eq(TEST_TICKET_ID), eq(attachments))).thenReturn(Collections.emptyList());

            ResponseEntity<List<TicketAttachmentDtoOut>> response = controller.addTicketAttachments(TEST_TICKET_ID, attachments);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle large file size in attachment")
        void shouldHandleLargeFileSizeInAttachment() {
            testAttachmentDtoIn.setFileSize(Long.MAX_VALUE);
            List<TicketAttachmentDtoIn> attachments = Collections.singletonList(testAttachmentDtoIn);
            
            when(ticketService.addTicketAttachments(eq(TEST_TICKET_ID), eq(attachments)))
                    .thenReturn(Collections.singletonList(testAttachmentDtoOut));

            ResponseEntity<List<TicketAttachmentDtoOut>> response = controller.addTicketAttachments(TEST_TICKET_ID, attachments);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ==================== ResponseStatusDto Tests ====================

    @Nested
    @DisplayName("ResponseStatusDto Tests")
    class ResponseStatusDtoTests {

        @Test
        @DisplayName("should set and get all dto properties")
        void shouldSetAndGetAllDtoProperties() {
            ResponseStatusDto dto = new ResponseStatusDto();
            
            dto.setSuccess(true);
            dto.setMessage("Operation successful");

            assertThat(dto.isSuccess()).isTrue();
            assertThat(dto.getMessage()).isEqualTo("Operation successful");
        }
    }
}
