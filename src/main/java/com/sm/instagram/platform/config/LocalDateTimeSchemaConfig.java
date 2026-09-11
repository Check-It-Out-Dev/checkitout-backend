package com.sm.instagram.platform.config;

import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * Stop the document claiming RFC 3339 {@code date-time} for values that carry no offset.
 *
 * <p>159 fields across this API are {@link LocalDateTime}. Jackson writes them the only way it can,
 * as a local time with no zone -- {@code 2026-09-11T09:05:26.114536} -- and springdoc declares them
 * {@code "type":"string","format":"date-time"} by default. RFC 3339, which is what OpenAPI's
 * {@code date-time} means, requires an offset. So the document was wrong about 74 fields, and
 * anything that validates against it says so: Schemathesis reported
 * {@code "2026-09-11T09:05:26.114536" is not a "date-time"} on {@code /address/paged},
 * {@code /consent/my} and {@code /dictionary/all} in one night run.
 *
 * <p>The server is not at fault -- a {@code LocalDateTime} has no offset to report, and inventing
 * one would be a lie about the data and a breaking change for every existing client. The document
 * is at fault, so the document is what changes: the type stays {@code string} and the format goes,
 * with an example that shows the shape. The frontend's generated client already types these as
 * {@code string} rather than {@code Date}, so nothing downstream moves.
 *
 * <p>{@link java.time.Instant} keeps {@code date-time}, and correctly: it serialises with a
 * {@code Z}. {@code OpenApiDateTimeFormatUnitTest} is what keeps the two apart as fields are added.
 *
 * <p>Registered from a static initialiser because springdoc consults this registry while it builds
 * schemas, which happens before any bean of ours could speak. The class is a {@code @Configuration}
 * so that the initialiser is guaranteed to run.
 */
@Configuration
public class LocalDateTimeSchemaConfig {

    /** The shape callers should expect: ISO-8601, no offset, microsecond precision as written. */
    public static final String LOCAL_DATE_TIME_EXAMPLE = "2026-09-11T09:05:26.114536";

    static {
        SpringDocUtils.getConfig().replaceWithSchema(
                LocalDateTime.class,
                new StringSchema()
                        .format(null)
                        .example(LOCAL_DATE_TIME_EXAMPLE)
                        .description("Local date and time, ISO-8601, with no UTC offset. "
                                + "Not an RFC 3339 date-time: this value names no time zone."));
    }
}
