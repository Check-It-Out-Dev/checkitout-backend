package com.sm.instagram.platform.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

@Component
public class ServiceAccountKeyValidator {

    /**
     * Validates that the service account key has necessary permissions.
     * Think of this as checking if your ID card has the right access levels.
     */
    public void validateServiceAccountKey(InputStream keyStream) {
        if (keyStream == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "keyStream");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> keyData = mapper.readValue(keyStream, Map.class);

            // Check required fields
            String[] requiredFields = {"type", "project_id", "private_key", "client_email"};

            for (String field : requiredFields) {
                if (!keyData.containsKey(field)) {
                    throw new ValidationTranslatableException("error.validation.missing_parameter", field);
                }
            }

            // Verify it's a service account key
            if (!"service_account".equals(keyData.get("type"))) {
                throw new BusinessRuleTranslatableException("error.validation.type_mismatch", "service_account");
            }
        } catch (ValidationTranslatableException | BusinessRuleTranslatableException e) {
            // Re-throw translatable exceptions
            throw e;
        } catch (Exception e) {
            // Wrap any other exceptions (JSON parsing, etc.)
            throw new ValidationTranslatableException("error.validation.invalid_json");
        }
    }
}
