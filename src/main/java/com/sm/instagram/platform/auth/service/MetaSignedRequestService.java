package com.sm.instagram.platform.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.dto.MetaCallbackPayload;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Parses and validates Meta's signed_request format used by
 * deauthorization and data deletion callbacks.
 * <p>
 * Format: base64url(signature).base64url(payload)
 * Signature: HMAC-SHA256 of the raw payload string using meta.app-secret.
 */
@Slf4j
@Service
public class MetaSignedRequestService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${meta.app-secret:}")
    private String appSecret;

    /**
     * Parse and validate a Meta signed_request.
     *
     * @param signedRequest the raw signed_request string from Meta
     * @return parsed callback payload
     * @throws AuthenticationTranslatableException if signature is invalid or payload is malformed
     */
    public MetaCallbackPayload parseSignedRequest(String signedRequest) {
        if (signedRequest == null || signedRequest.isBlank()) {
            throw new AuthenticationTranslatableException("error.instagram.invalid_signed_request");
        }

        String[] parts = signedRequest.split("\\.", 2);
        if (parts.length != 2) {
            log.warn("Meta signed_request has invalid format (no '.' separator)");
            throw new AuthenticationTranslatableException("error.instagram.invalid_signed_request");
        }

        String encodedSignature = parts[0];
        String encodedPayload = parts[1];

        // Verify HMAC-SHA256 signature
        verifySignature(encodedSignature, encodedPayload);

        // Decode and parse payload
        return decodePayload(encodedPayload);
    }

    private void verifySignature(String encodedSignature, String encodedPayload) {
        try {
            byte[] providedSignature = Base64.getUrlDecoder().decode(encodedSignature);

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] expectedSignature = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));

            // Constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(providedSignature, expectedSignature)) {
                log.warn("Meta signed_request HMAC verification failed");
                throw new AuthenticationTranslatableException("error.instagram.invalid_signed_request");
            }
        } catch (AuthenticationTranslatableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error verifying Meta signed_request signature: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.instagram.invalid_signed_request");
        }
    }

    private MetaCallbackPayload decodePayload(String encodedPayload) {
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedPayload);
            String json = new String(decodedBytes, StandardCharsets.UTF_8);

            JsonNode node = OBJECT_MAPPER.readTree(json);

            String userId = node.has("user_id") ? node.get("user_id").asText() : null;
            String algorithm = node.has("algorithm") ? node.get("algorithm").asText() : null;
            long issuedAt = node.has("issued_at") ? node.get("issued_at").asLong() : 0;

            if (userId == null || userId.isBlank()) {
                log.warn("Meta signed_request payload missing user_id");
                throw new AuthenticationTranslatableException("error.instagram.invalid_signed_request");
            }

            return new MetaCallbackPayload(userId, algorithm, issuedAt);
        } catch (AuthenticationTranslatableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error decoding Meta signed_request payload: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.instagram.invalid_signed_request");
        }
    }
}
