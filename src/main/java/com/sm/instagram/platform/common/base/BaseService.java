package com.sm.instagram.platform.common.base;

import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.city.CityRepository;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class BaseService<T, I, D> implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(BaseService.class);

    protected final SpecificationBuilder<T> specificationBuilder;
    protected final BaseRepository<T, I> repository;
    protected final ModelMapper modelMapper;
    protected final RepositoryResolver repositoryResolver;

    protected ApplicationContext applicationContext;

    protected BaseService(ApplicationContext applicationContext,
                          SpecificationBuilder<T> specificationBuilder,
                          BaseRepository<T, I> repository,
                          ModelMapper modelMapper,
                          RepositoryResolver repositoryResolver) {
        this.specificationBuilder = specificationBuilder;
        this.repository = repository;
        this.modelMapper = modelMapper;
        this.repositoryResolver = repositoryResolver;
        this.applicationContext = applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @SuppressWarnings("unchecked")
    protected BaseService<T, I, D> getSelf() {
        return applicationContext.getBean((Class<BaseService<T, I, D>>) this.getClass());
    }

    public Specification<T> createSpecification(Map<String, String> filters) {
        return specificationBuilder.createSpecification(filters);
    }

    public T findById(I id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Entity"));
    }

    public Page<T> getDataPagedAndFiltered(Pageable pageable, Map<String, String> filters) {
        Specification<T> spec = createSpecification(filters);
        return repository.findAll(spec, pageable);
    }

    public T save(T entity) {
        return repository.save(getSelf().updateEntityUpdater(entity));
    }

    @Transactional
    public T updateEntityUpdater(T entity) {
        if (entity instanceof UpdaterTracking updaterTracking) {
            String userId = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();
            updaterTracking.setAutoUpdaterId(userId);
        }
        return entity;
    }

    public T update(I id, D dtoIn) {
        T existingEntity = findById(id);
        modelMapper.map(dtoIn, existingEntity);
        return repository.save(getSelf().updateEntityUpdater(existingEntity));
    }

    public T patch(I id, Map<String, Object> updates) {
        return patch(id, updates, Set.of("id", "createdTime", "lastUpdateTime"));
    }

    public T patch(I id, Map<String, Object> updates, Set<String> ignoredFields) {
        T existingEntity = findById(id);

        updates.forEach((fieldName, value) -> {
            if (ignoredFields.contains(fieldName)) return;

            try {
                setFieldValue(existingEntity, fieldName, value);
            } catch (NoSuchFieldException e) {
                throw new ValidationTranslatableException("error.validation.invalid_structure", fieldName);
            } catch (IllegalAccessException e) {
                throw new ValidationTranslatableException("error.validation.failed");
            }
        });

        return repository.save(getSelf().updateEntityUpdater(existingEntity));
    }

    /**
     * Sets a field value using reflection with proper accessibility handling.
     */
    private void setFieldValue(T entity, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = entity.getClass().getDeclaredField(fieldName);
        boolean wasAccessible = field.canAccess(entity);

        try {
            if (!wasAccessible) {
                field.setAccessible(true);
            }
            Object castValue = convertValueToFieldType(field, value);
            field.set(entity, castValue);
        } finally {
            if (!wasAccessible) {
                field.setAccessible(false);
            }
        }
    }

    protected Object convertValueToFieldType(Field field, Object value) {
        Class<?> fieldType = field.getType();

        if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
            return Integer.parseInt(value.toString());
        } else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
            return Long.parseLong(value.toString());
        } else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
            return Double.parseDouble(value.toString());
        } else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
            return Boolean.parseBoolean(value.toString());
        } else if (fieldType.equals(String.class)) {
            return value.toString();
        } else if (fieldType.equals(LocalDateTime.class)) {
            String dateTimeStr = value.toString().trim();
            try {
                return LocalDateTime.parse(dateTimeStr);
            } catch (DateTimeParseException e) {
                try {
                    return OffsetDateTime.parse(dateTimeStr).toLocalDateTime();
                } catch (DateTimeParseException e2) {
                    throw new ValidationTranslatableException("error.validation.date_format_invalid");
                }
            }
        } else if (isEntityClass(fieldType)) {
            if (fieldType.equals(City.class)) {
                CityRepository cityRepo = (CityRepository) repositoryResolver.getRepository(City.class);
                return cityRepo.findByName(value.toString())
                        .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "City"));
            } else {
                Long id = Long.parseLong(value.toString());
                return findEntity(fieldType, id);
            }
        } else if (List.class.isAssignableFrom(fieldType)) {
            ParameterizedType genericType = (ParameterizedType) field.getGenericType();
            Class<?> elementType = (Class<?>) genericType.getActualTypeArguments()[0];

            if (value == null) {
                return null;
            }
            List<?> convertedList = (List<?>) value;

            return convertedList.stream()
                    .filter(Objects::nonNull)
                    .map(v -> convertValueToFieldTypeForListElement(elementType, v))
                    .filter(Objects::nonNull)
                    .toList();
        } else if (fieldType.isEnum()) {
            return convertToEnum(fieldType, value.toString());
        } else {
            return value.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> E convertToEnum(Class<?> enumClass, String value) {
        if (!enumClass.isEnum()) {
            throw new ValidationTranslatableException("error.validation.invalid_argument");
        }
        Class<E> typedEnumClass = (Class<E>) enumClass;
        return Enum.valueOf(typedEnumClass, value);
    }

    private Object convertValueToFieldTypeForListElement(Class<?> elementType, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return createObjectFromMap(elementType, (Map<?, ?>) value);
        }
        return null;
    }

    private Object createObjectFromMap(Class<?> elementType, Map<?, ?> valueMap) {
        try {
            Object obj = elementType.getDeclaredConstructor().newInstance();

            for (Field field : elementType.getDeclaredFields()) {
                if (valueMap.containsKey(field.getName())) {
                    Object fieldValue = valueMap.get(field.getName());
                    Object castValue = convertValueToFieldType(field, fieldValue);
                    setFieldValueSafely(obj, field, castValue);
                }
            }
            return obj;
        } catch (NoSuchMethodException | InstantiationException e) {
            throw new IllegalStateException("Failed to create instance of " + elementType.getSimpleName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to access field in " + elementType.getSimpleName(), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Constructor threw an exception for " + elementType.getSimpleName(), e);
        }
    }

    private void setFieldValueSafely(Object obj, Field field, Object value) throws IllegalAccessException {
        boolean wasAccessible = field.canAccess(obj);

        try {
            if (!wasAccessible) {
                field.setAccessible(true);
            }
            field.set(obj, value);
        } finally {
            if (!wasAccessible) {
                field.setAccessible(false);
            }
        }
    }

    private boolean isEntityClass(Class<?> fieldType) {
        try {
            repositoryResolver.getRepository(fieldType);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Object findEntity(Class<?> fieldType, Long id) {
        BaseRepository<?, Long> repo = repositoryResolver.getRepository(fieldType);
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", fieldType.getSimpleName()));
    }

    public void delete(I id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("error.business.item_not_found", "Entity");
        }
        repository.deleteById(id);
    }

    public void deleteAll(List<I> ids) {
        for (I entityToDelete : ids) {
            delete(entityToDelete);
        }
    }

    @Transactional(readOnly = true)
    public abstract <O> Page<O> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters);

    @Transactional(readOnly = true)
    public abstract <O> O toDto(T entity);

    @Transactional(readOnly = true)
    public <O> O findByIdAsDto(I id) {
        T entity = getSelf().findById(id);
        return getSelf().toDto(entity);
    }

    @Transactional
    public <O> O saveAsDto(T entity) {
        T saved = getSelf().save(entity);
        return getSelf().toDto(saved);
    }

    @Transactional
    public abstract <O> O createFromDtoAsDto(D dto);

    @Transactional
    public <O> O updateAsDto(I id, D dto) {
        T updated = getSelf().update(id, dto);
        return getSelf().toDto(updated);
    }

    @Transactional
    public <O> O patchAsDto(I id, Map<String, Object> updates) {
        T patched = getSelf().patch(id, updates);
        return getSelf().toDto(patched);
    }
}