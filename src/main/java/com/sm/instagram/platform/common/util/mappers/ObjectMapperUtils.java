package com.sm.instagram.platform.common.util.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Static accessor for the Spring-managed {@link ObjectMapper}.
 *
 * <p>Wires Spring's primary mapper into a static slot so legacy call sites
 * that cannot easily switch to constructor injection still get the same
 * configured instance (modules registered, naming strategy applied, etc.).
 *
 * <p>The slot is held in an {@link AtomicReference} for visibility across
 * threads — Spring publishes the mapper from the application start-up
 * thread, but downstream {@link #getObjectMapper()} calls happen on
 * request-handler threads.
 *
 * @deprecated Prefer constructor or method injection of {@link ObjectMapper}
 *             directly; this static holder remains only for legacy classes
 *             that pre-date the Spring 4.3 single-constructor convention.
 */
@Deprecated
@UtilityClass
public class ObjectMapperUtils {

    private static final AtomicReference<ObjectMapper> SHARED_MAPPER = new AtomicReference<>();

    /**
     * Return the application's shared {@link ObjectMapper}.
     *
     * @throws IllegalStateException if invoked before the Spring context has
     *                               had a chance to publish the mapper —
     *                               typically a sign the call site is being
     *                               exercised from a unit test that did not
     *                               bootstrap the holder bean
     */
    public static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = SHARED_MAPPER.get();
        if (mapper == null) {
            throw new IllegalStateException(
                    "ObjectMapper not yet initialized. Make sure Spring context is loaded.");
        }
        return mapper;
    }

    private static void publish(ObjectMapper mapper) {
        SHARED_MAPPER.set(Objects.requireNonNull(mapper, "objectMapper"));
    }

    /**
     * Bridges Spring's managed {@link ObjectMapper} into the static holder
     * above. Defined inside this file so the publication path is private to
     * the utility — no other code can mutate the slot.
     */
    @Component
    static final class ObjectMapperHolder {

        @Autowired
        ObjectMapperHolder(ObjectMapper objectMapper) {
            ObjectMapperUtils.publish(objectMapper);
        }
    }
}
