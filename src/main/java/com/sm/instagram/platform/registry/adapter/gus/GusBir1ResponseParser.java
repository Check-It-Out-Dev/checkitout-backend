package com.sm.instagram.platform.registry.adapter.gus;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

/**
 * Parses GUS BIR 1.1 SOAP XML responses using JDK's built-in DOM parser.
 * No external XML libraries required.
 */
@Slf4j
public final class GusBir1ResponseParser {

    private GusBir1ResponseParser() {
    }

    /**
     * Extracts the session ID from a Zaloguj (login) response.
     * The session ID is in the ZalogujResult element.
     */
    static String extractSessionId(String soapResponse) {
        String value = extractSingleValue(soapResponse, "ZalogujResult");
        if (value == null || value.isBlank()) {
            log.error("GUS BIR1: Failed to extract session ID from login response");
            return null;
        }
        return value.trim();
    }

    /**
     * Extracts the search result XML from DaneSzukajPodmioty response.
     * The result is HTML-encoded XML inside DaneSzukajPodmiotyResult.
     * Returns a map of field names to values from the first dane element.
     */
    static Map<String, String> extractSearchResult(String soapResponse) {
        String encodedXml = extractSingleValue(soapResponse, "DaneSzukajPodmiotyResult");
        if (encodedXml == null || encodedXml.isBlank()) {
            return Map.of();
        }

        // The result is HTML-encoded XML that needs to be decoded
        String decodedXml = decodeHtmlEntities(encodedXml);
        return parseDataElements(decodedXml, "dane");
    }

    /**
     * Extracts the full report data from DanePobierzPelnyRaport response.
     * Returns a map of all field names to values.
     */
    static Map<String, String> extractFullReport(String soapResponse) {
        String encodedXml = extractSingleValue(soapResponse, "DanePobierzPelnyRaportResult");
        if (encodedXml == null || encodedXml.isBlank()) {
            return Map.of();
        }

        String decodedXml = decodeHtmlEntities(encodedXml);
        return parseDataElements(decodedXml, "dane");
    }

    /**
     * Extracts PKD codes from a PKD-specific full report response.
     * Each {@code <dane>} element represents one PKD code entry.
     */
    static List<Map<String, Object>> extractPkdCodes(String soapResponse) {
        String encodedXml = extractSingleValue(soapResponse, "DanePobierzPelnyRaportResult");
        if (encodedXml == null || encodedXml.isBlank()) {
            return List.of();
        }

        String decodedXml = decodeHtmlEntities(encodedXml);
        return parsePkdElements(decodedXml);
    }

    private static List<Map<String, Object>> parsePkdElements(String xml) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            String wrappedXml = xml.trim();
            if (!wrappedXml.startsWith("<?xml") && !wrappedXml.startsWith("<root")) {
                wrappedXml = "<root>" + wrappedXml + "</root>";
            }

            DocumentBuilder builder = createDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(wrappedXml)));

            NodeList daneNodes = doc.getElementsByTagName("dane");
            for (int i = 0; i < daneNodes.getLength(); i++) {
                Element dane = (Element) daneNodes.item(i);
                Map<String, String> fields = new LinkedHashMap<>();
                NodeList children = dane.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    if (children.item(j) instanceof Element child) {
                        String value = child.getTextContent();
                        if (value != null && !value.isBlank()) {
                            fields.put(child.getTagName(), value.trim());
                        }
                    }
                }
                if (!fields.isEmpty()) {
                    Map<String, Object> pkd = new LinkedHashMap<>();
                    // PKD fields use different prefixes for legal persons vs sole proprietors
                    String code = fields.getOrDefault("pkd_Kod", fields.get("fiz_pkd_Kod"));
                    String desc = fields.getOrDefault("pkd_Nazwa", fields.get("fiz_pkd_Nazwa"));
                    String primary = fields.getOrDefault("pkd_Przewazajace", fields.get("fiz_pkd_Przewazajace"));
                    if (code != null) {
                        pkd.put("code", code);
                        pkd.put("description", desc);
                        pkd.put("isPrimary", "1".equals(primary));
                        result.add(pkd);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse PKD elements from XML: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Checks if the SOAP response contains a fault.
     */
    static boolean isSoapFault(String soapResponse) {
        return soapResponse != null && soapResponse.contains("Fault");
    }

    /**
     * Extracts a fault message from a SOAP fault response.
     */
    static String extractFaultMessage(String soapResponse) {
        String reason = extractSingleValue(soapResponse, "Reason");
        if (reason != null) return reason;
        return extractSingleValue(soapResponse, "faultstring");
    }

    private static String extractSingleValue(String xml, String tagName) {
        try {
            DocumentBuilder builder = createDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            // Try with and without namespace
            NodeList nodes = doc.getElementsByTagName(tagName);
            if (nodes.getLength() == 0) {
                // Try with namespace-aware local name matching
                nodes = doc.getElementsByTagNameNS("*", tagName);
            }
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            log.warn("Failed to parse XML for tag '{}': {}", tagName, e.getMessage());
        }
        return null;
    }

    private static Map<String, String> parseDataElements(String xml, String parentTagName) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            // Wrap in root element if needed
            String wrappedXml = xml.trim();
            if (!wrappedXml.startsWith("<?xml") && !wrappedXml.startsWith("<root")) {
                wrappedXml = "<root>" + wrappedXml + "</root>";
            }

            DocumentBuilder builder = createDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(wrappedXml)));

            NodeList daneNodes = doc.getElementsByTagName(parentTagName);
            if (daneNodes.getLength() > 0) {
                Element dane = (Element) daneNodes.item(0);
                NodeList children = dane.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element child) {
                        String value = child.getTextContent();
                        if (value != null && !value.isBlank()) {
                            result.put(child.getTagName(), value.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse data elements from XML: {}", e.getMessage());
        }
        return result;
    }

    private static String decodeHtmlEntities(String encoded) {
        if (encoded == null) return null;
        return encoded
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private static DocumentBuilder createDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Security: disable external entities to prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder();
    }
}
