package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.RequestContextUtils;
import com.sm.instagram.platform.legal.*;
import com.sm.instagram.platform.legal.dto.AnonymousConsentDtoIn;
import com.sm.instagram.platform.legal.dto.ConsentProofDtoIn;
import com.sm.instagram.platform.legal.dto.ConsentRecordDtoIn;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserAccountOrchestrator;
import com.sm.instagram.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LegalConsentService Unit Tests")
class LegalConsentServiceUnitTest {

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private LegalDocumentService legalDocumentService;

    @Mock
    private ConsentCookieService consentCookieService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private UserAccountOrchestrator userAccountOrchestrator;

    private LegalConsentService service;

    @BeforeEach
    void setUp() {
        service = new LegalConsentService(consentRecordRepository, legalDocumentService,
                consentCookieService, userRepository, userCacheService, userAccountOrchestrator);
        ReflectionTestUtils.setField(service, "gracePeriodDays", 38);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId("firebase_uid_" + id);
        user.setNewestConsentsAccepted(false);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        return user;
    }

    private LegalDocument createDocument(LegalDocumentType type) {
        LegalDocument doc = new LegalDocument();
        doc.setId((long) (type.ordinal() + 1));
        doc.setType(type);
        doc.setLanguage("pl");
        doc.setVersion(1);
        doc.setContentHash("sha256-" + type.name().toLowerCase());
        doc.setDocumentUrl("https://example.com/" + type.name().toLowerCase() + ".pdf");
        doc.setPublishedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return doc;
    }

    private AnonymousConsentDtoIn createAnonymousConsentDtoIn() {
        AnonymousConsentDtoIn dto = new AnonymousConsentDtoIn();
        dto.setDocumentName("cookie_policy_v2_pl.pdf");
        dto.setLanguage("pl");
        dto.setIsTrusted(true);
        dto.setUserAgent("TestBrowser/1.0");
        return dto;
    }

    private ConsentRecordDtoIn createConsentRecordDtoIn(String documentType) {
        ConsentRecordDtoIn dto = new ConsentRecordDtoIn();
        dto.setDocumentType(documentType);
        dto.setAction("ACCEPT");
        dto.setProof(createConsentProofDtoIn());
        return dto;
    }

    private ConsentProofDtoIn createConsentProofDtoIn() {
        ConsentProofDtoIn proof = new ConsentProofDtoIn();
        proof.setEventTrusted(true);
        proof.setTimestamp(1709971200000L); // 2024-03-09
        proof.setScreenX(100.0);
        proof.setScreenY(200.0);
        proof.setCheckboxId("tos-checkbox");
        proof.setDocumentHash("sha256-hash");
        return proof;
    }

    private MockHttpServletRequest createMockRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "TestBrowser/1.0");
        return request;
    }

    private ConsentRecord createAnonymousConsentRecord(Long id, LegalDocument document) {
        ConsentRecord record = new ConsentRecord();
        record.setId(id);
        record.setUser(null);
        record.setDocument(document);
        record.setSource(ConsentSource.COOKIE_BANNER);
        return record;
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Nested
    @DisplayName("recordAnonymousConsent")
    class RecordAnonymousConsent {

        @Test
        @DisplayName("should create record with null user and return id")
        void should_create_record_with_null_user_and_return_id() {
            AnonymousConsentDtoIn dto = createAnonymousConsentDtoIn();
            LegalDocument doc = createDocument(LegalDocumentType.COOKIE_POLICY);
            MockHttpServletRequest request = createMockRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(legalDocumentService.findByDocumentName("cookie_policy_v2_pl.pdf")).thenReturn(doc);
            when(consentRecordRepository.save(any(ConsentRecord.class))).thenAnswer(inv -> {
                ConsentRecord cr = inv.getArgument(0);
                cr.setId(42L);
                return cr;
            });

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                Long result = service.recordAnonymousConsent(dto, request, response);

                assertThat(result).isEqualTo(42L);
            }

            ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
            verify(consentRecordRepository).save(captor.capture());
            ConsentRecord saved = captor.getValue();
            assertThat(saved.getUser()).isNull();
            assertThat(saved.getSource()).isEqualTo(ConsentSource.COOKIE_BANNER);
            assertThat(saved.getDocument()).isEqualTo(doc);
        }

        @Test
        @DisplayName("should set HMAC cookie with record id")
        void should_set_hmac_cookie_with_record_id() {
            AnonymousConsentDtoIn dto = createAnonymousConsentDtoIn();
            LegalDocument doc = createDocument(LegalDocumentType.COOKIE_POLICY);
            MockHttpServletRequest request = createMockRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(legalDocumentService.findByDocumentName(anyString())).thenReturn(doc);
            when(consentRecordRepository.save(any(ConsentRecord.class))).thenAnswer(inv -> {
                ConsentRecord cr = inv.getArgument(0);
                cr.setId(99L);
                return cr;
            });

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.recordAnonymousConsent(dto, request, response);
            }

            verify(consentCookieService).setConsentCookie(
                    eq(response), eq(ConsentCookieService.COOKIE_CONSENT_COOKIE_POLICY), any(ConsentProofPayload.class));
        }

        @Test
        @DisplayName("should use request User-Agent when DTO has none")
        void should_use_request_user_agent_when_dto_has_none() {
            AnonymousConsentDtoIn dto = createAnonymousConsentDtoIn();
            dto.setUserAgent(null);
            LegalDocument doc = createDocument(LegalDocumentType.COOKIE_POLICY);
            MockHttpServletRequest request = createMockRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(legalDocumentService.findByDocumentName(anyString())).thenReturn(doc);
            when(consentRecordRepository.save(any(ConsentRecord.class))).thenAnswer(inv -> {
                ConsentRecord cr = inv.getArgument(0);
                cr.setId(1L);
                return cr;
            });

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.recordAnonymousConsent(dto, request, response);
            }

            ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
            verify(consentRecordRepository).save(captor.capture());
            assertThat(captor.getValue().getConsentProof().getUserAgent()).isEqualTo("TestBrowser/1.0");
        }
    }

    @Nested
    @DisplayName("prepareConsentCookie")
    class PrepareConsentCookie {

        @Test
        @DisplayName("should set consent cookie for valid document type")
        void should_set_consent_cookie_for_valid_document_type() {
            MockHttpServletRequest request = createMockRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            ConsentProofDtoIn proofIn = createConsentProofDtoIn();

            service.prepareConsentCookie("TERMS_OF_SERVICE", 1, "sha256-hash", proofIn, request, response);

            verify(consentCookieService).setConsentCookie(
                    eq(response), eq("consent_terms_of_service"), any(ConsentProofPayload.class));
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid type")
        void should_throw_ValidationTranslatableException_for_invalid_type() {
            MockHttpServletRequest request = createMockRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            ConsentProofDtoIn proofIn = createConsentProofDtoIn();

            assertThatThrownBy(() -> service.prepareConsentCookie(
                    "INVALID_TYPE", 1, "hash", proofIn, request, response))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("processRegistrationConsents")
    class ProcessRegistrationConsents {

        private User user;
        private MockHttpServletRequest request;
        private MockHttpServletResponse response;

        @BeforeEach
        void setUpRegistration() {
            user = createUser(1L);
            request = createMockRequest();
            response = new MockHttpServletResponse();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(legalDocumentService.findLatest(any(LegalDocumentType.class), eq("pl")))
                    .thenReturn(createDocument(LegalDocumentType.TERMS_OF_SERVICE));
            when(consentRecordRepository.save(any(ConsentRecord.class))).thenAnswer(inv -> {
                ConsentRecord cr = inv.getArgument(0);
                if (cr.getId() == null) cr.setId(100L);
                return cr;
            });
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("should process all three consent types")
        void should_process_all_three_consent_types() {
            // Cookie consent: valid cookie with record ID in payload
            ConsentProofPayload cpPayload = ConsentProofPayload.builder().isTrusted(true).consentRecordId(10L).build();
            when(consentCookieService.readConsentCookie(request, "consent_cookie_policy", ConsentProofPayload.class))
                    .thenReturn(cpPayload);
            when(consentRecordRepository.findById(10L))
                    .thenReturn(Optional.of(createAnonymousConsentRecord(10L, createDocument(LegalDocumentType.COOKIE_POLICY))));

            // ToS and Privacy: valid cookies
            ConsentProofPayload tosPayload = ConsentProofPayload.builder().isTrusted(true).build();
            ConsentProofPayload privPayload = ConsentProofPayload.builder().isTrusted(true).build();
            when(consentCookieService.readConsentCookie(request, "consent_terms_of_service", ConsentProofPayload.class))
                    .thenReturn(tosPayload);
            when(consentCookieService.readConsentCookie(request, "consent_privacy_policy", ConsentProofPayload.class))
                    .thenReturn(privPayload);

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.processRegistrationConsents(1L, request, response, ConsentSource.REGISTRATION);
            }

            // 1 anonymous record update + 2 new document consent records = save called at least 3 times
            // (plus the anonymous record link + user save)
            verify(consentRecordRepository, atLeast(2)).save(any(ConsentRecord.class));
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("should link anonymous cookie consent to user")
        void should_link_anonymous_cookie_consent_to_user() {
            ConsentRecord anonymousRecord = createAnonymousConsentRecord(10L, createDocument(LegalDocumentType.COOKIE_POLICY));
            ConsentProofPayload cpPayload = ConsentProofPayload.builder().isTrusted(true).consentRecordId(10L).build();
            when(consentCookieService.readConsentCookie(request, "consent_cookie_policy", ConsentProofPayload.class))
                    .thenReturn(cpPayload);
            when(consentRecordRepository.findById(10L)).thenReturn(Optional.of(anonymousRecord));
            // BUG-17: the link path uses getReferenceById for managed-entity safety
            when(userRepository.getReferenceById(1L)).thenReturn(user);

            // ToS and Privacy: no cookies (fallback path)
            when(consentCookieService.readConsentCookie(eq(request), eq("consent_terms_of_service"), eq(ConsentProofPayload.class)))
                    .thenReturn(null);
            when(consentCookieService.readConsentCookie(eq(request), eq("consent_privacy_policy"), eq(ConsentProofPayload.class)))
                    .thenReturn(null);

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.processRegistrationConsents(1L, request, response, ConsentSource.REGISTRATION);
            }

            assertThat(anonymousRecord.getUser()).isEqualTo(user);
            verify(consentRecordRepository).save(anonymousRecord);
        }

        /**
         * BUG-17 (latent footgun): processCookieConsent's login-path twin
         * {@code linkAnonymousCookieConsentToUser} correctly fetches a managed reference via
         * {@code userRepository.getReferenceById(user.getId())} before assigning it to the
         * consent record — defensive against detached-entity hazards across @Transactional
         * boundaries. The registration-path {@code processCookieConsent} previously assigned
         * the caller-supplied User instance directly. Today it happens to be safe because
         * {@code processRegistrationConsents} loads the User inside the same outer
         * @Transactional, but any future refactor that splits the tx (or passes a detached
         * User down) would reintroduce a JPA detached-entity bug. This test pins the
         * managed-reference contract by giving findById and getReferenceById DIFFERENT
         * User instances and asserting the consent record's attached user is the reference,
         * not the caller-supplied instance.
         */
        @Test
        @DisplayName("BUG-17: processCookieConsent uses getReferenceById for managed-entity safety")
        void should_attach_managed_reference_not_caller_supplied_user() {
            // Caller's User (what findById returns — used by processRegistrationConsents)
            User callerSuppliedUser = createUser(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(callerSuppliedUser));

            // Managed reference (what getReferenceById returns — what processCookieConsent must use)
            User managedReference = createUser(1L);
            when(userRepository.getReferenceById(1L)).thenReturn(managedReference);

            ConsentRecord anonymousRecord = createAnonymousConsentRecord(10L,
                    createDocument(LegalDocumentType.COOKIE_POLICY));
            ConsentProofPayload cpPayload = ConsentProofPayload.builder()
                    .isTrusted(true).consentRecordId(10L).build();
            when(consentCookieService.readConsentCookie(request, "consent_cookie_policy", ConsentProofPayload.class))
                    .thenReturn(cpPayload);
            when(consentRecordRepository.findById(10L)).thenReturn(Optional.of(anonymousRecord));

            // ToS + Privacy: no cookies (we only care about the cookie-link path here)
            when(consentCookieService.readConsentCookie(eq(request), eq("consent_terms_of_service"), eq(ConsentProofPayload.class)))
                    .thenReturn(null);
            when(consentCookieService.readConsentCookie(eq(request), eq("consent_privacy_policy"), eq(ConsentProofPayload.class)))
                    .thenReturn(null);

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.processRegistrationConsents(1L, request, response, ConsentSource.REGISTRATION);
            }

            // The consent record must carry the MANAGED reference, not the caller-supplied User.
            assertThat(anonymousRecord.getUser())
                    .as("BUG-17: consent record must attach the managed reference from getReferenceById, not the caller-supplied User")
                    .isSameAs(managedReference);
            verify(userRepository).getReferenceById(1L);
        }

        @Test
        @DisplayName("should set newest_consents_accepted to true")
        void should_set_newest_consents_accepted_true() {
            when(consentCookieService.readConsentCookie(any(), anyString(), any())).thenReturn(null);

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.processRegistrationConsents(1L, request, response, ConsentSource.REGISTRATION);
            }

            assertThat(user.getNewestConsentsAccepted()).isTrue();
        }

        @Test
        @DisplayName("should clear all cookies after processing")
        void should_clear_all_cookies_after_processing() {
            when(consentCookieService.readConsentCookie(any(), anyString(), any())).thenReturn(null);

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.processRegistrationConsents(1L, request, response, ConsentSource.REGISTRATION);
            }

            verify(consentCookieService).clearAllConsentCookies(response);
        }

        @Test
        @DisplayName("should throw when user not found")
        void should_throw_when_user_not_found() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.processRegistrationConsents(
                    999L, request, response, ConsentSource.REGISTRATION))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should create record without proof when cookie missing (fallback)")
        void should_create_record_without_proof_when_cookie_missing() {
            when(consentCookieService.readConsentCookie(eq(request), anyString(), eq(ConsentProofPayload.class)))
                    .thenReturn(null);

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.processRegistrationConsents(1L, request, response, ConsentSource.REGISTRATION);
            }

            // Should still save records (without proof) for ToS and Privacy
            ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
            verify(consentRecordRepository, atLeast(2)).save(captor.capture());

            List<ConsentRecord> records = captor.getAllValues();
            // Fallback records should have null consentProof
            assertThat(records).anyMatch(r -> r.getConsentProof() == null && r.getUser() != null);
        }

        @Test
        @DisplayName("should create cookie policy record via processDocumentConsent when cookie has no consentRecordId (clickwrap path)")
        void should_create_cookie_policy_record_via_clickwrap_fallback() {
            // Cookie policy: valid cookie but NO consentRecordId (clickwrap path, not banner)
            ConsentProofPayload cpPayload = ConsentProofPayload.builder()
                    .isTrusted(true)
                    .documentHash("sha256-cookie_policy")
                    .checkboxId("cp-checkbox")
                    .build(); // no consentRecordId
            when(consentCookieService.readConsentCookie(request, "consent_cookie_policy", ConsentProofPayload.class))
                    .thenReturn(cpPayload);

            // ToS and Privacy: valid cookies
            ConsentProofPayload tosPayload = ConsentProofPayload.builder().isTrusted(true).build();
            ConsentProofPayload privPayload = ConsentProofPayload.builder().isTrusted(true).build();
            when(consentCookieService.readConsentCookie(request, "consent_terms_of_service", ConsentProofPayload.class))
                    .thenReturn(tosPayload);
            when(consentCookieService.readConsentCookie(request, "consent_privacy_policy", ConsentProofPayload.class))
                    .thenReturn(privPayload);

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");
                service.processRegistrationConsents(1L, request, response, ConsentSource.REGISTRATION);
            }

            // Should create 3 new consent records (CP via fallback + ToS + PP)
            ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
            verify(consentRecordRepository, times(3)).save(captor.capture());

            // All records should be linked to the user (not anonymous)
            List<ConsentRecord> records = captor.getAllValues();
            assertThat(records).allMatch(r -> r.getUser() != null);
            // CP record should have the clickwrap proof
            assertThat(records).anyMatch(r -> r.getConsentProof() != null
                    && r.getConsentProof().getCheckboxId() != null
                    && r.getConsentProof().getCheckboxId().equals("cp-checkbox"));
        }

        @Test
        @DisplayName("should not link cookie consent when record already has user")
        void should_not_link_cookie_consent_when_record_already_has_user() {
            User existingUser = createUser(999L);
            ConsentRecord existingRecord = createAnonymousConsentRecord(10L, createDocument(LegalDocumentType.COOKIE_POLICY));
            existingRecord.setUser(existingUser); // Already linked to another user

            ConsentProofPayload cpPayload = ConsentProofPayload.builder().isTrusted(true).consentRecordId(10L).build();
            when(consentCookieService.readConsentCookie(request, "consent_cookie_policy", ConsentProofPayload.class))
                    .thenReturn(cpPayload);
            when(consentRecordRepository.findById(10L)).thenReturn(Optional.of(existingRecord));
            when(consentCookieService.readConsentCookie(eq(request), eq("consent_terms_of_service"), eq(ConsentProofPayload.class)))
                    .thenReturn(null);
            when(consentCookieService.readConsentCookie(eq(request), eq("consent_privacy_policy"), eq(ConsentProofPayload.class)))
                    .thenReturn(null);

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.processRegistrationConsents(1L, request, response, ConsentSource.REGISTRATION);
            }

            // The existing record's user should not be changed
            assertThat(existingRecord.getUser()).isEqualTo(existingUser);
        }
    }

    @Nested
    @DisplayName("recordAuthenticatedConsentBatch")
    class RecordAuthenticatedConsentBatch {

        @Test
        @DisplayName("should create consent records for all document types atomically")
        void should_create_consent_records_for_all_document_types() {
            User user = createUser(1L);
            MockHttpServletRequest request = createMockRequest();

            List<ConsentRecordDtoIn> records = List.of(
                    createConsentRecordDtoIn("COOKIE_POLICY"),
                    createConsentRecordDtoIn("TERMS_OF_SERVICE"),
                    createConsentRecordDtoIn("PRIVACY_POLICY")
            );

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(legalDocumentService.findLatest(any(LegalDocumentType.class), eq("pl")))
                    .thenReturn(createDocument(LegalDocumentType.TERMS_OF_SERVICE));
            when(consentRecordRepository.save(any(ConsentRecord.class))).thenAnswer(inv -> {
                ConsentRecord cr = inv.getArgument(0);
                if (cr.getId() == null) cr.setId(100L);
                return cr;
            });
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.recordAuthenticatedConsentBatch(1L, records, request);
            }

            verify(consentRecordRepository, times(3)).save(any(ConsentRecord.class));
            assertThat(user.getNewestConsentsAccepted()).isTrue();
            verify(userRepository).save(user);
            verify(userCacheService).evict(user.getFirebaseUserId());
        }

        @Test
        @DisplayName("should throw when required document type is missing")
        void should_throw_when_required_document_type_is_missing() {
            User user = createUser(1L);
            MockHttpServletRequest request = createMockRequest();

            // Only 2 out of 3 required types
            List<ConsentRecordDtoIn> records = List.of(
                    createConsentRecordDtoIn("COOKIE_POLICY"),
                    createConsentRecordDtoIn("TERMS_OF_SERVICE")
                    // PRIVACY_POLICY missing
            );

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.recordAuthenticatedConsentBatch(1L, records, request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should revert BLOCKED status to ACTIVE and increment tokenVersion")
        void should_revert_blocked_status_and_increment_token_version() {
            User user = createUser(1L);
            user.setAccountStatus(AccountStatus.BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS);
            user.setTokenVersion(5L);
            MockHttpServletRequest request = createMockRequest();

            List<ConsentRecordDtoIn> records = List.of(
                    createConsentRecordDtoIn("COOKIE_POLICY"),
                    createConsentRecordDtoIn("TERMS_OF_SERVICE"),
                    createConsentRecordDtoIn("PRIVACY_POLICY")
            );

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(legalDocumentService.findLatest(any(LegalDocumentType.class), eq("pl")))
                    .thenReturn(createDocument(LegalDocumentType.TERMS_OF_SERVICE));
            when(consentRecordRepository.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.recordAuthenticatedConsentBatch(1L, records, request);
            }

            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(user.getTokenVersion()).isEqualTo(6L);
            assertThat(user.getNewestConsentsAccepted()).isTrue();
        }

        @Test
        @DisplayName("should throw when user not found")
        void should_throw_when_user_not_found() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            MockHttpServletRequest request = createMockRequest();

            List<ConsentRecordDtoIn> records = List.of(createConsentRecordDtoIn("COOKIE_POLICY"));

            assertThatThrownBy(() -> service.recordAuthenticatedConsentBatch(999L, records, request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should set source to LOGIN_PROMPT for all records")
        void should_set_source_to_LOGIN_PROMPT_for_all_records() {
            User user = createUser(1L);
            MockHttpServletRequest request = createMockRequest();

            List<ConsentRecordDtoIn> records = List.of(
                    createConsentRecordDtoIn("COOKIE_POLICY"),
                    createConsentRecordDtoIn("TERMS_OF_SERVICE"),
                    createConsentRecordDtoIn("PRIVACY_POLICY")
            );

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(legalDocumentService.findLatest(any(LegalDocumentType.class), eq("pl")))
                    .thenReturn(createDocument(LegalDocumentType.TERMS_OF_SERVICE));
            when(consentRecordRepository.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<RequestContextUtils> rcu = mockStatic(RequestContextUtils.class)) {
                rcu.when(() -> RequestContextUtils.getClientIpAddress(any())).thenReturn("127.0.0.1");

                service.recordAuthenticatedConsentBatch(1L, records, request);
            }

            ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
            verify(consentRecordRepository, times(3)).save(captor.capture());
            assertThat(captor.getAllValues()).allSatisfy(record ->
                    assertThat(record.getSource()).isEqualTo(ConsentSource.LOGIN_PROMPT));
        }
    }

    @Nested
    @DisplayName("hasAcceptedAllCurrentDocuments")
    class HasAcceptedAllCurrentDocuments {

        @Test
        @DisplayName("should return true when all three accepted")
        void should_return_true_when_all_three_accepted() {
            when(legalDocumentService.getLatestVersion(any())).thenReturn(Optional.of(1));
            when(consentRecordRepository.hasUserAcceptedDocumentVersion(eq(1L), any(), eq(1)))
                    .thenReturn(true);

            boolean result = service.hasAcceptedAllCurrentDocuments(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when one missing")
        void should_return_false_when_one_missing() {
            when(legalDocumentService.getLatestVersion(any())).thenReturn(Optional.of(1));
            when(consentRecordRepository.hasUserAcceptedDocumentVersion(1L, LegalDocumentType.COOKIE_POLICY, 1))
                    .thenReturn(true);
            when(consentRecordRepository.hasUserAcceptedDocumentVersion(1L, LegalDocumentType.TERMS_OF_SERVICE, 1))
                    .thenReturn(false);

            boolean result = service.hasAcceptedAllCurrentDocuments(1L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when no documents exist (vacuous truth)")
        void should_return_true_when_no_documents_exist() {
            when(legalDocumentService.getLatestVersion(any())).thenReturn(Optional.empty());

            boolean result = service.hasAcceptedAllCurrentDocuments(1L);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("computeDaysToAcceptNewTerms")
    class ComputeDaysToAcceptNewTerms {

        @Test
        @DisplayName("should return positive days when within grace period")
        void should_return_positive_days_when_within_grace_period() {
            // Published 10 days ago, so 38 - 10 = 28 days remaining
            LocalDateTime tenDaysAgo = LocalDateTime.now().minusDays(10);
            when(legalDocumentService.getLatestPublishedAt()).thenReturn(Optional.of(tenDaysAgo));

            Integer result = service.computeDaysToAcceptNewTerms();

            assertThat(result).isBetween(27, 29); // Allow for time precision
        }

        @Test
        @DisplayName("should return zero when past deadline")
        void should_return_zero_when_past_deadline() {
            LocalDateTime fiftyDaysAgo = LocalDateTime.now().minusDays(50);
            when(legalDocumentService.getLatestPublishedAt()).thenReturn(Optional.of(fiftyDaysAgo));

            Integer result = service.computeDaysToAcceptNewTerms();

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should return null when no documents published")
        void should_return_null_when_no_documents_published() {
            when(legalDocumentService.getLatestPublishedAt()).thenReturn(Optional.empty());

            Integer result = service.computeDaysToAcceptNewTerms();

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getRequiredConsentsHeaderValue")
    class GetRequiredConsentsHeaderValue {

        @Test
        @DisplayName("should return comma-separated types when not accepted")
        void should_return_comma_separated_types_when_not_accepted() {
            when(legalDocumentService.getLatestVersion(LegalDocumentType.TERMS_OF_SERVICE))
                    .thenReturn(Optional.of(2));
            when(legalDocumentService.getLatestVersion(LegalDocumentType.PRIVACY_POLICY))
                    .thenReturn(Optional.of(1));
            when(consentRecordRepository.hasUserAcceptedDocumentVersion(eq(1L), any(), anyInt()))
                    .thenReturn(false);

            String result = service.getRequiredConsentsHeaderValue(1L);

            assertThat(result).contains("TERMS_OF_SERVICE:2");
            assertThat(result).contains("PRIVACY_POLICY:1");
            assertThat(result).contains(",");
        }

        @Test
        @DisplayName("should return null when all accepted")
        void should_return_null_when_all_accepted() {
            when(legalDocumentService.getLatestVersion(any())).thenReturn(Optional.of(1));
            when(consentRecordRepository.hasUserAcceptedDocumentVersion(eq(1L), any(), eq(1)))
                    .thenReturn(true);

            String result = service.getRequiredConsentsHeaderValue(1L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return single type when one not accepted")
        void should_return_single_type_when_one_not_accepted() {
            when(legalDocumentService.getLatestVersion(LegalDocumentType.TERMS_OF_SERVICE))
                    .thenReturn(Optional.of(2));
            when(legalDocumentService.getLatestVersion(LegalDocumentType.PRIVACY_POLICY))
                    .thenReturn(Optional.of(1));
            when(consentRecordRepository.hasUserAcceptedDocumentVersion(1L, LegalDocumentType.TERMS_OF_SERVICE, 2))
                    .thenReturn(true);
            when(consentRecordRepository.hasUserAcceptedDocumentVersion(1L, LegalDocumentType.PRIVACY_POLICY, 1))
                    .thenReturn(false);

            String result = service.getRequiredConsentsHeaderValue(1L);

            assertThat(result).isEqualTo("PRIVACY_POLICY:1");
            assertThat(result).doesNotContain("TERMS_OF_SERVICE");
        }
    }

    @Nested
    @DisplayName("blockExpiredUsers")
    class BlockExpiredUsers {

        @Test
        @DisplayName("should block users when grace period expired")
        void should_block_users_when_grace_period_expired() {
            // Published 50 days ago, grace period is 38 days, so expired
            LocalDateTime fiftyDaysAgo = LocalDateTime.now().minusDays(50);
            when(legalDocumentService.getLatestPublishedAt()).thenReturn(Optional.of(fiftyDaysAgo));

            User user1 = createUser(1L);
            user1.setTokenVersion(1L);
            User user2 = createUser(2L);
            user2.setTokenVersion(3L);
            when(userRepository.findByNewestConsentsAcceptedFalseAndAccountStatusIn(
                    List.of(AccountStatus.ACTIVE, AccountStatus.IN_VALIDATION)))
                    .thenReturn(List.of(user1, user2));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.blockExpiredUsers();

            assertThat(user1.getAccountStatus()).isEqualTo(AccountStatus.BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS);
            assertThat(user1.getTokenVersion()).isEqualTo(2L);
            assertThat(user2.getAccountStatus()).isEqualTo(AccountStatus.BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS);
            assertThat(user2.getTokenVersion()).isEqualTo(4L);
            verify(userRepository, times(2)).save(any(User.class));
            verify(userCacheService).evict("firebase_uid_1");
            verify(userCacheService).evict("firebase_uid_2");
        }

        @Test
        @DisplayName("should skip when no legal documents")
        void should_skip_when_no_legal_documents() {
            when(legalDocumentService.getLatestPublishedAt()).thenReturn(Optional.empty());

            service.blockExpiredUsers();

            verify(userRepository, never()).findByNewestConsentsAcceptedFalseAndAccountStatusIn(any());
        }

        @Test
        @DisplayName("should skip when grace period not expired")
        void should_skip_when_grace_period_not_expired() {
            LocalDateTime tenDaysAgo = LocalDateTime.now().minusDays(10);
            when(legalDocumentService.getLatestPublishedAt()).thenReturn(Optional.of(tenDaysAgo));

            service.blockExpiredUsers();

            verify(userRepository, never()).findByNewestConsentsAcceptedFalseAndAccountStatusIn(any());
        }
    }

    @Nested
    @DisplayName("cleanupAnonymousRecords")
    class CleanupAnonymousRecords {

        @Test
        @DisplayName("should delegate to repository and return count")
        void should_delegate_to_repository_and_return_count() {
            when(consentRecordRepository.deleteAnonymousRecordsBefore(any(LocalDateTime.class)))
                    .thenReturn(15);

            int result = service.cleanupAnonymousRecords();

            assertThat(result).isEqualTo(15);
            verify(consentRecordRepository).deleteAnonymousRecordsBefore(any(LocalDateTime.class));
        }
    }
}
