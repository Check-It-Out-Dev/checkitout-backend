package com.sm.instagram.platform.common.util.mappers;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;

@Slf4j
@Service
public class EnumTranslationService {

    public EnumTranslationService() {
        log.debug("EnumTranslationService initialized");
    }

    // Converts String to Enum
    public <T extends Enum<T>> T translateToEnum(Class<T> enumClass, String value) {
        log.debug("Translating string '{}' to enum type: {}", value, enumClass.getSimpleName());

        for (T enumConstant : enumClass.getEnumConstants()) {
            if (enumConstant.name().equals(value)) {
                log.debug("Successfully translated '{}' to {}.{}", value, enumClass.getSimpleName(), enumConstant.name());
                return enumConstant;
            }
        }

        log.error("Failed to translate invalid value: {} for enum: {}", value, enumClass.getSimpleName());
        throw new ValidationTranslatableException("error.validation.invalid_argument", value, enumClass.getSimpleName());
    }

    // Converts Enum to String
    public <T extends Enum<T>> String translateToString(T enumValue) {
        if (enumValue == null) {
            log.debug("Translating null enum value to null string");
            return null;
        }
        String result = enumValue.name();
        log.debug("Translated enum {} to string: {}", enumValue.getClass().getSimpleName(), result);
        return result;
    }

    // Translates a field value (either Enum to String or String to Enum)
    public <T extends Enum<T>> Object translate(Field field, Object value) {
        Class<?> fieldType = field.getType();
        log.debug("Translating field '{}' of type: {}", field.getName(), fieldType.getSimpleName());

        // Ensure the field type is an enum
        if (Enum.class.isAssignableFrom(fieldType)) {
            if (value instanceof String) {
                // Type safety: cast the field type to a specific enum type
                return translateToEnum((Class<? extends Enum>) fieldType, (String) value);
            } else if (value instanceof Enum) {
                // Fix the error by explicitly casting Enum to the specific type
                @SuppressWarnings("unchecked")
                T enumValue = (T) value;  // Safe cast to specific enum type
                return translateToString(enumValue);
            }
        }

        log.debug("No translation needed for field '{}', returning original value", field.getName());
        return value; // Return the original value if no conversion is needed
    }
}
