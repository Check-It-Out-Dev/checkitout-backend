package com.sm.instagram.platform.unit.validation;

import com.sm.instagram.platform.legal.dto.ConsentPrepareRequest;
import com.sm.instagram.platform.legal.dto.ConsentProofDtoIn;
import com.sm.instagram.platform.legal.dto.ConsentRecordBatchDtoIn;
import com.sm.instagram.platform.storage.model.FileUploadRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Input the boundary should refuse, refused at the boundary.
 *
 * <p>Every case here answered <b>500</b> in a night fuzz run, which is the server saying the fault
 * is its own. It was not: each is a body a caller sent, and each reached a service that
 * dereferenced it. Bean validation is where they belong, and the two that got through did so for
 * the same subtle reason — {@code @Valid} cascades, and there is nothing to cascade into on a null.
 *
 * <p>Built from the reproducing calls Schemathesis printed, verbatim, so a regression here is a
 * regression of the thing that was actually found.
 */
@DisplayName("A body the boundary should refuse never reaches a service")
class BoundaryRefusalUnitTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void open() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * The 254-character filename Schemathesis sent. It passed validation at max = 255, and then
     * the stored object's last path segment -- {@code {timestamp}_{filename}} -- came to 268 bytes,
     * which no filesystem will take. The upload failed after the boundary had approved it, and the
     * catch-all called that 507 Insufficient Storage: the caller was told their quota was full.
     */
    @Test
    @DisplayName("a filename the storage path cannot hold is refused before any upload begins")
    void tooLongFilenameIsRefused() {
        FileUploadRequest request = new FileUploadRequest();
        request.setFilename("0".repeat(254) + ".jpg");
        request.setContentType("image/jpeg");
        request.setFileSize(1L);

        assertThat(propertiesViolating(request)).contains("filename");
    }

    @Test
    @DisplayName("and one that fits is not")
    void filenameThatFitsIsAccepted() {
        FileUploadRequest request = new FileUploadRequest();
        request.setFilename("0".repeat(236) + ".jpg");
        request.setContentType("image/jpeg");
        request.setFileSize(1L);

        assertThat(propertiesViolating(request)).doesNotContain("filename");
    }

    private static Set<String> propertiesViolating(Object body) {
        return validator.validate(body).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static Set<String> propertiesInViolation(Object bean) {
        return validator.validate(bean).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("consent/prepare: a null proof is refused, not dereferenced")
    void nullProofIsRefused() {
        // curl -d '{"documentType":"COOKIE_POLICY","version":0,"documentHash":"","proof":null}'
        ConsentPrepareRequest request = new ConsentPrepareRequest();
        request.setDocumentType("COOKIE_POLICY");
        request.setVersion(0);
        request.setDocumentHash("");
        request.setProof(null);

        assertThat(propertiesInViolation(request))
                .as("both the null proof and the empty hash are the caller's to fix")
                .contains("proof", "documentHash");
    }

    @Test
    @DisplayName("consent/prepare: a complete request passes")
    void completeRequestPasses() {
        ConsentPrepareRequest request = new ConsentPrepareRequest();
        request.setDocumentType("COOKIE_POLICY");
        request.setVersion(3);
        request.setDocumentHash("2f8a1c9e");
        request.setProof(new ConsentProofDtoIn());

        assertThat(propertiesInViolation(request)).isEmpty();
    }

    @Test
    @DisplayName("consent/record-batch: a null element is refused, not dereferenced")
    void nullRecordElementIsRefused() {
        // curl -d '{"records":[null]}'
        ConsentRecordBatchDtoIn batch = new ConsentRecordBatchDtoIn();
        batch.setRecords(Collections.singletonList(null));

        assertThat(propertiesInViolation(batch))
                .as("@Valid on the list has nothing to cascade into on a null element")
                .anyMatch(path -> path.startsWith("records["));
    }

    @Test
    @DisplayName("consent/record-batch: an empty list is still refused")
    void emptyBatchIsRefused() {
        ConsentRecordBatchDtoIn batch = new ConsentRecordBatchDtoIn();
        batch.setRecords(List.of());

        assertThat(propertiesInViolation(batch)).contains("records");
    }
}
