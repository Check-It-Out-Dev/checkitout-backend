package com.sm.instagram.platform.unit.registry;

import com.sm.instagram.platform.registry.adapter.gus.GusBir1ResponseParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GusBir1ResponseParser.
 * Tests SOAP XML parsing for GUS BIR 1.1 API responses.
 */
@DisplayName("GusBir1ResponseParser")
class GusBir1ResponseParserUnitTest {

    // Since methods are package-private, we access them via reflection
    // This is acceptable for testing internal parsing logic

    @Nested
    @DisplayName("extractSessionId")
    class ExtractSessionId {

        @Test
        @DisplayName("should extract session ID from valid login response")
        void shouldExtractSessionIdFromValidResponse() throws Exception {
            String soapResponse = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                                xmlns:a="http://www.w3.org/2005/08/addressing">
                        <s:Body>
                            <ZalogujResponse xmlns="http://CIS/BIR/PUBL/2014/07/IUslugaBIRzwor686">
                                <ZalogujResult>abc123sessionid</ZalogujResult>
                            </ZalogujResponse>
                        </s:Body>
                    </s:Envelope>
                    """;

            String sessionId = invokeExtractSessionId(soapResponse);
            assertThat(sessionId).isEqualTo("abc123sessionid");
        }

        @Test
        @DisplayName("should return null for empty session ID")
        void shouldReturnNullForEmptySessionId() throws Exception {
            String soapResponse = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <ZalogujResponse xmlns="http://CIS/BIR/PUBL/2014/07/IUslugaBIRzwor686">
                                <ZalogujResult></ZalogujResult>
                            </ZalogujResponse>
                        </s:Body>
                    </s:Envelope>
                    """;

            String sessionId = invokeExtractSessionId(soapResponse);
            assertThat(sessionId).isNull();
        }

        @Test
        @DisplayName("should trim whitespace from session ID")
        void shouldTrimWhitespaceFromSessionId() throws Exception {
            String soapResponse = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <ZalogujResponse xmlns="http://CIS/BIR/PUBL/2014/07/IUslugaBIRzwor686">
                                <ZalogujResult>  session123  </ZalogujResult>
                            </ZalogujResponse>
                        </s:Body>
                    </s:Envelope>
                    """;

            String sessionId = invokeExtractSessionId(soapResponse);
            assertThat(sessionId).isEqualTo("session123");
        }
    }

    @Nested
    @DisplayName("extractSearchResult")
    class ExtractSearchResult {

        @Test
        @DisplayName("should extract search result fields from HTML-encoded XML")
        void shouldExtractSearchResultFromEncodedXml() throws Exception {
            String soapResponse = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <DaneSzukajPodmiotyResponse xmlns="http://CIS/BIR/PUBL/2014/07/IUslugaBIRzwor686">
                                <DaneSzukajPodmiotyResult>&lt;root&gt;&lt;dane&gt;&lt;Regon&gt;012345678&lt;/Regon&gt;&lt;Nip&gt;5261040828&lt;/Nip&gt;&lt;Nazwa&gt;Test Company&lt;/Nazwa&gt;&lt;Typ&gt;P&lt;/Typ&gt;&lt;/dane&gt;&lt;/root&gt;</DaneSzukajPodmiotyResult>
                            </DaneSzukajPodmiotyResponse>
                        </s:Body>
                    </s:Envelope>
                    """;

            Map<String, String> result = invokeExtractSearchResult(soapResponse);
            assertThat(result)
                    .containsEntry("Regon", "012345678")
                    .containsEntry("Nip", "5261040828")
                    .containsEntry("Nazwa", "Test Company")
                    .containsEntry("Typ", "P");
        }

        @Test
        @DisplayName("should return empty map for empty result")
        void shouldReturnEmptyMapForEmptyResult() throws Exception {
            String soapResponse = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <DaneSzukajPodmiotyResponse xmlns="http://CIS/BIR/PUBL/2014/07/IUslugaBIRzwor686">
                                <DaneSzukajPodmiotyResult></DaneSzukajPodmiotyResult>
                            </DaneSzukajPodmiotyResponse>
                        </s:Body>
                    </s:Envelope>
                    """;

            Map<String, String> result = invokeExtractSearchResult(soapResponse);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should skip blank values in data elements")
        void shouldSkipBlankValues() throws Exception {
            String soapResponse = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <DaneSzukajPodmiotyResponse xmlns="http://CIS/BIR/PUBL/2014/07/IUslugaBIRzwor686">
                                <DaneSzukajPodmiotyResult>&lt;root&gt;&lt;dane&gt;&lt;Regon&gt;012345678&lt;/Regon&gt;&lt;EmptyField&gt;   &lt;/EmptyField&gt;&lt;Nip&gt;5261040828&lt;/Nip&gt;&lt;/dane&gt;&lt;/root&gt;</DaneSzukajPodmiotyResult>
                            </DaneSzukajPodmiotyResponse>
                        </s:Body>
                    </s:Envelope>
                    """;

            Map<String, String> result = invokeExtractSearchResult(soapResponse);
            assertThat(result)
                    .containsKey("Regon")
                    .containsKey("Nip")
                    .doesNotContainKey("EmptyField");
        }
    }

    @Nested
    @DisplayName("extractFullReport")
    class ExtractFullReport {

        @Test
        @DisplayName("should extract full report fields from HTML-encoded XML")
        void shouldExtractFullReportFields() throws Exception {
            String soapResponse = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <DanePobierzPelnyRaportResponse xmlns="http://CIS/BIR/PUBL/2014/07/IUslugaBIRzwor686">
                                <DanePobierzPelnyRaportResult>&lt;root&gt;&lt;dane&gt;&lt;praw_regon14&gt;01234567800000&lt;/praw_regon14&gt;&lt;praw_nazwa&gt;Firma Testowa Sp. z o.o.&lt;/praw_nazwa&gt;&lt;praw_adSiedzMiejscowosc_Nazwa&gt;Warszawa&lt;/praw_adSiedzMiejscowosc_Nazwa&gt;&lt;praw_podstawowaFormaPrawna_Symbol&gt;1&lt;/praw_podstawowaFormaPrawna_Symbol&gt;&lt;praw_szczegolnaFormaPrawna_Symbol&gt;117&lt;/praw_szczegolnaFormaPrawna_Symbol&gt;&lt;/dane&gt;&lt;/root&gt;</DanePobierzPelnyRaportResult>
                            </DanePobierzPelnyRaportResponse>
                        </s:Body>
                    </s:Envelope>
                    """;

            Map<String, String> result = invokeExtractFullReport(soapResponse);
            assertThat(result)
                    .containsEntry("praw_regon14", "01234567800000")
                    .containsEntry("praw_nazwa", "Firma Testowa Sp. z o.o.")
                    .containsEntry("praw_adSiedzMiejscowosc_Nazwa", "Warszawa")
                    .containsEntry("praw_podstawowaFormaPrawna_Symbol", "1")
                    .containsEntry("praw_szczegolnaFormaPrawna_Symbol", "117");
        }
    }

    @Nested
    @DisplayName("isSoapFault")
    class IsSoapFault {

        @Test
        @DisplayName("should return true for SOAP fault response")
        void shouldReturnTrueForSoapFault() throws Exception {
            String response = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <s:Fault>
                                <s:Code><s:Value>s:Receiver</s:Value></s:Code>
                                <s:Reason><s:Text>Server error</s:Text></s:Reason>
                            </s:Fault>
                        </s:Body>
                    </s:Envelope>
                    """;

            assertThat(invokeIsSoapFault(response)).isTrue();
        }

        @Test
        @DisplayName("should return false for normal response")
        void shouldReturnFalseForNormalResponse() throws Exception {
            String response = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <ZalogujResponse>
                                <ZalogujResult>session123</ZalogujResult>
                            </ZalogujResponse>
                        </s:Body>
                    </s:Envelope>
                    """;

            assertThat(invokeIsSoapFault(response)).isFalse();
        }

        @Test
        @DisplayName("should return false for null response")
        void shouldReturnFalseForNullResponse() throws Exception {
            assertThat(invokeIsSoapFault(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("extractFaultMessage")
    class ExtractFaultMessage {

        @Test
        @DisplayName("should extract Reason from SOAP 1.2 fault")
        void shouldExtractReasonFromSoap12Fault() throws Exception {
            String response = """
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                        <s:Body>
                            <s:Fault>
                                <s:Code><s:Value>s:Receiver</s:Value></s:Code>
                                <s:Reason><s:Text>Internal server error occurred</s:Text></s:Reason>
                            </s:Fault>
                        </s:Body>
                    </s:Envelope>
                    """;

            String message = invokeExtractFaultMessage(response);
            assertThat(message).contains("Internal server error occurred");
        }

        @Test
        @DisplayName("should extract faultstring from SOAP 1.1 fault")
        void shouldExtractFaultstringFromSoap11Fault() throws Exception {
            String response = """
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                        <soap:Body>
                            <soap:Fault>
                                <faultcode>soap:Server</faultcode>
                                <faultstring>Something went wrong</faultstring>
                            </soap:Fault>
                        </soap:Body>
                    </soap:Envelope>
                    """;

            String message = invokeExtractFaultMessage(response);
            assertThat(message).isEqualTo("Something went wrong");
        }
    }

    // =========================================================================
    // Reflection helpers for package-private methods
    // =========================================================================

    private String invokeExtractSessionId(String response) throws Exception {
        Method method = GusBir1ResponseParser.class.getDeclaredMethod("extractSessionId", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> invokeExtractSearchResult(String response) throws Exception {
        Method method = GusBir1ResponseParser.class.getDeclaredMethod("extractSearchResult", String.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(null, response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> invokeExtractFullReport(String response) throws Exception {
        Method method = GusBir1ResponseParser.class.getDeclaredMethod("extractFullReport", String.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(null, response);
    }

    private boolean invokeIsSoapFault(String response) throws Exception {
        Method method = GusBir1ResponseParser.class.getDeclaredMethod("isSoapFault", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, response);
    }

    private String invokeExtractFaultMessage(String response) throws Exception {
        Method method = GusBir1ResponseParser.class.getDeclaredMethod("extractFaultMessage", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, response);
    }
}
