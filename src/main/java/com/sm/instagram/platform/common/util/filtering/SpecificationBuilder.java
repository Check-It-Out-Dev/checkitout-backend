package com.sm.instagram.platform.common.util.filtering;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SpecificationBuilder<T> {
    
    private static final String FROM_SUFFIX = "From";
    private static final String TO_SUFFIX = "To";
    private static final String CITY_FIELD = "city";
    private static final String NAME_PROPERTY = "name";
    private static final String WILDCARD_SUFFIX = "%";

    public Specification<T> createSpecification(Map<String, String> filters) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            filters.forEach((fieldName, fieldValue) -> {
                if (fieldValue != null && !fieldValue.isEmpty()) {
                    processFilter(root, criteriaBuilder, predicates, fieldName, fieldValue);
                }
            });

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void processFilter(Root<?> root, jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                              List<Predicate> predicates, String fieldName, String fieldValue) {
        try {
            // Check if this is a NOT operation (fieldName ends with !)
            boolean isNotOperation = fieldName.endsWith("!");
            String actualFieldName = isNotOperation ? fieldName.substring(0, fieldName.length() - 1) : fieldName;
            
            // Handle date range filters
            if (isDateRangeFilter(actualFieldName)) {
                handleDateRangeFilter(root, criteriaBuilder, predicates, actualFieldName, fieldValue);
                return;
            }

            createFieldPredicate(root, criteriaBuilder, predicates, actualFieldName, fieldValue, isNotOperation);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument for field: {} - {}", fieldName, e.getMessage());
        }
    }

    private boolean isDateRangeFilter(String fieldName) {
        return fieldName.endsWith(FROM_SUFFIX) || fieldName.endsWith(TO_SUFFIX);
    }

    private void createFieldPredicate(Root<?> root, jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                     List<Predicate> predicates, String actualFieldName, String fieldValue, 
                                     boolean isNotOperation) {
        Path<?> path = resolvePath(root, actualFieldName);
        Field field = getFieldSafely(root.getJavaType(), actualFieldName);

        if (field == null) {
            logFieldNotFound(actualFieldName);
            return;
        }

        Class<?> fieldType = field.getType();
        Predicate predicate = createPredicateByType(criteriaBuilder, path, fieldType, fieldValue, actualFieldName);
        
        if (predicate != null) {
            if (isNotOperation) {
                predicate = criteriaBuilder.not(predicate);
                log.debug("Applied NOT operation to field: {}", actualFieldName);
            }
            predicates.add(predicate);
        }
    }

    private Predicate createPredicateByType(jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                           Path<?> path, Class<?> fieldType, String fieldValue, String actualFieldName) {
        if (String.class.equals(fieldType)) {
            return createStringPredicate(criteriaBuilder, path, fieldValue);
        } else if (Enum.class.isAssignableFrom(fieldType)) {
            return createEnumPredicate(criteriaBuilder, path, fieldType, fieldValue, actualFieldName);
        } else if (Number.class.isAssignableFrom(fieldType) || fieldType.isPrimitive()) {
            return createNumberPredicate(criteriaBuilder, path, fieldType, fieldValue, actualFieldName);
        } else if (CITY_FIELD.equals(getRootFieldName(actualFieldName))) {
            return createCityPredicate(criteriaBuilder, path, fieldValue);
        } else if (java.time.LocalDateTime.class.equals(fieldType)) {
            return createDateTimePredicate(criteriaBuilder, path, fieldValue, actualFieldName);
        }
        return null;
    }

    private Predicate createStringPredicate(jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                           Path<?> path, String fieldValue) {
        return criteriaBuilder.like(
                criteriaBuilder.lower(path.as(String.class)),
                fieldValue.toLowerCase() + WILDCARD_SUFFIX);
    }

    private Predicate createEnumPredicate(jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                         Path<?> path, Class<?> fieldType, String fieldValue, String actualFieldName) {
        if (fieldValue.contains(",")) {
            return createMultipleEnumPredicate(path, fieldType, fieldValue, actualFieldName);
        } else {
            return createSingleEnumPredicate(criteriaBuilder, path, fieldType, fieldValue, actualFieldName);
        }
    }

    private Predicate createMultipleEnumPredicate(Path<?> path, Class<?> fieldType, String fieldValue, String actualFieldName) {
        String[] enumValues = fieldValue.split(",");
        List<Enum<?>> convertedEnums = new ArrayList<>();
        
        for (String enumValue : enumValues) {
            convertToEnumSafely(fieldType, enumValue.trim(), actualFieldName)
                    .ifPresent(convertedEnums::add);
        }
        
        return convertedEnums.isEmpty() ? null : path.in(convertedEnums);
    }

    private Predicate createSingleEnumPredicate(jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                               Path<?> path, Class<?> fieldType, String fieldValue, String actualFieldName) {
        return convertToEnumSafely(fieldType, fieldValue.trim(), actualFieldName)
                .map(enumConstant -> criteriaBuilder.equal(path, enumConstant))
                .orElse(null);
    }

    private java.util.Optional<Enum<?>> convertToEnumSafely(Class<?> fieldType, String value, String fieldName) {
        try {
            return java.util.Optional.of(convertToEnum(fieldType, value));
        } catch (IllegalArgumentException e) {
            logInvalidEnumValue(value, fieldName);
            return java.util.Optional.empty();
        }
    }

    private Predicate createNumberPredicate(jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                           Path<?> path, Class<?> fieldType, String fieldValue, String actualFieldName) {
        try {
            if (fieldType.equals(boolean.class) || fieldType.equals(Boolean.class)) {
                boolean boolValue = Boolean.parseBoolean(fieldValue);
                return criteriaBuilder.equal(path, boolValue);
            } else {
                return criteriaBuilder.equal(path, parseNumber(fieldValue, fieldType));
            }
        } catch (NumberFormatException e) {
            log.error("Invalid number format for field: {} value: {}", actualFieldName, fieldValue);
            return null;
        }
    }

    private Predicate createCityPredicate(jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                         Path<?> path, String fieldValue) {
        return criteriaBuilder.like(
                criteriaBuilder.lower(path.get(NAME_PROPERTY)),
                fieldValue.toLowerCase() + WILDCARD_SUFFIX);
    }

    private Predicate createDateTimePredicate(jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                             Path<?> path, String fieldValue, String actualFieldName) {
        try {
            java.time.LocalDateTime dateValue = java.time.LocalDateTime.parse(fieldValue);
            return criteriaBuilder.equal(path, dateValue);
        } catch (java.time.format.DateTimeParseException e) {
            log.error("Invalid date format for field: {} value: {}", actualFieldName, fieldValue);
            return null;
        }
    }

    private Path<?> resolvePath(Root<?> root, String fieldName) {
        String[] parts = fieldName.split("\\.");
        Path<?> path = root;
        for (String part : parts) {
            try {
                path = path.get(part);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid field path: " + fieldName, e);
            }
        }
        return path;
    }

    private String getRootFieldName(String fieldName) {
        int dotIndex = fieldName.indexOf('.');
        return (dotIndex != -1) ? fieldName.substring(0, dotIndex) : fieldName;
    }

    private Number parseNumber(String value, Class<?> fieldType) {
        if (Integer.class.equals(fieldType)) {
            return Integer.parseInt(value);
        } else if (Long.class.equals(fieldType)) {
            return Long.parseLong(value);
        } else if (Double.class.equals(fieldType)) {
            return Double.parseDouble(value);
        } else if (Float.class.equals(fieldType)) {
            return Float.parseFloat(value);
        } else {
            throw new IllegalArgumentException("Unsupported number type: " + fieldType.getName());
        }
    }

    private Field getFieldSafely(Class<?> clazz, String fieldName) {
        String[] parts = fieldName.split("\\.");
        Class<?> currentClass = clazz;
        Field field = null;

        try {
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                field = currentClass.getDeclaredField(part);

                // If this is not the last part, we need to traverse to the next class
                if (i < parts.length - 1) {
                    currentClass = getNextClass(field);
                }
            }
        } catch (NoSuchFieldException e) {
            logFieldNotFound(fieldName);
            return null;
        } catch (Exception e) {
            log.warn("Error resolving field path: {} - {}", fieldName, e.getMessage());
            return null;
        }

        return field;
    }

    private Class<?> getNextClass(Field field) {
        Class<?> fieldType = field.getType();
        
        // Handle collections - get the generic type
        if (Collection.class.isAssignableFrom(fieldType)) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            return (Class<?>) parameterizedType.getActualTypeArguments()[0];
        } else {
            return fieldType;
        }
    }

    private void handleDateRangeFilter(Root<?> root, jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                       List<Predicate> predicates, String fieldName, String fieldValue) {
        try {
            java.time.LocalDateTime dateValue = java.time.LocalDateTime.parse(fieldValue);

            if (fieldName.endsWith(FROM_SUFFIX)) {
                String baseFieldName = fieldName.substring(0, fieldName.length() - FROM_SUFFIX.length());
                Path<?> path = resolvePath(root, baseFieldName);
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(path.as(java.time.LocalDateTime.class), dateValue));
            } else if (fieldName.endsWith(TO_SUFFIX)) {
                String baseFieldName = fieldName.substring(0, fieldName.length() - TO_SUFFIX.length());
                Path<?> path = resolvePath(root, baseFieldName);
                predicates.add(criteriaBuilder.lessThanOrEqualTo(path.as(java.time.LocalDateTime.class), dateValue));
            }
        } catch (java.time.format.DateTimeParseException e) {
            log.error("Invalid date format for range filter: {} value: {}", fieldName, fieldValue);
        } catch (IllegalArgumentException e) {
            log.error("Invalid field for date range filter: {} - {}", fieldName, e.getMessage());
        }
    }

    /**
     * Helper method to convert string to enum
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Enum<?> convertToEnum(Class<?> enumClass, String value) {
        if (!enumClass.isEnum()) {
            throw new IllegalArgumentException("Field type is not an enum: " + enumClass.getSimpleName());
        }

        // Get all enum constants and find match (case-insensitive)
        Object[] enumConstants = enumClass.getEnumConstants();
        for (Object enumConstant : enumConstants) {
            if (enumConstant.toString().equalsIgnoreCase(value)) {
                return (Enum<?>) enumConstant;
            }
        }

        // If no case-insensitive match, try exact match
        Class<? extends Enum> typedEnumClass = (Class<? extends Enum>) enumClass;
        return Enum.valueOf(typedEnumClass, value.toUpperCase());
    }

    private void logFieldNotFound(String fieldName) {
        log.warn("Field not found in entity: {}", fieldName);
        
        // GDPR logging if filtering personal data fields
        if (isPersonalDataField(fieldName)) {
            log.info("GDPR: Operation=data_filtering, Field={}, Purpose=user_request, LegalBasis=contract",
                fieldName);
        }
    }

    private void logInvalidEnumValue(String value, String fieldName) {
        log.warn("Invalid enum value '{}' for field: {}", value, fieldName);
    }
    
    /**
     * Check if a field contains personal data
     */
    private boolean isPersonalDataField(String fieldName) {
        // List of fields that contain personal data
        return fieldName != null && (
            fieldName.toLowerCase().contains("email") ||
            fieldName.toLowerCase().contains("name") ||
            fieldName.toLowerCase().contains("phone") ||
            fieldName.toLowerCase().contains("address") ||
            fieldName.toLowerCase().contains("birthday") ||
            fieldName.toLowerCase().contains("dob") ||
            fieldName.toLowerCase().contains("ssn") ||
            fieldName.toLowerCase().contains("user")
        );
    }
}
