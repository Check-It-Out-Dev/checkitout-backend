package com.sm.instagram.platform.unit.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.user.CompanyPublicProfileDto;
import com.sm.instagram.platform.user.InfluencerPublicProfileDto;
import com.sm.instagram.platform.user.PublicProfileDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A consumer of a public profile can tell which of the two it received.
 *
 * <p>{@code GET /users/paged/public-profile} returns a page of {@link PublicProfileDto}, which is
 * either shape, and nothing on the wire said which -- so the document's {@code oneOf} was a claim
 * no validator could check. Neither branch declared a required property, so a company profile
 * validated as an influencer profile too, and the fuzzer reported the body as unparseable against
 * the server's own schema.
 *
 * <p>Two assertions, and both matter. The value has to be on the wire, or a consumer still cannot
 * dispatch. And each branch has to CONSTRAIN it, or {@code oneOf} stays ambiguous: OpenAPI's
 * {@code discriminator} is a hint for code generators and a JSON Schema validator ignores it
 * entirely.
 */
@DisplayName("A public profile says which of the two shapes it is")
class PublicProfileDiscriminatorUnitTest {

    private static final Path SPEC = Path.of("docs", "openapi", "openapi.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode spec;

    @BeforeAll
    static void readSpec() throws IOException {
        spec = MAPPER.readTree(Files.readString(SPEC));
    }

    @Test
    @DisplayName("a company profile carries profileType COMPANY on the wire")
    void companyCarriesItsType() throws Exception {
        JsonNode body = MAPPER.valueToTree(new CompanyPublicProfileDto());

        assertThat(body.path("profileType").asText()).isEqualTo("COMPANY");
    }

    @Test
    @DisplayName("an influencer profile carries profileType INFLUENCER on the wire")
    void influencerCarriesItsType() {
        JsonNode body = MAPPER.valueToTree(new InfluencerPublicProfileDto());

        assertThat(body.path("profileType").asText()).isEqualTo("INFLUENCER");
    }

    @Test
    @DisplayName("each branch of the union constrains profileType to its own value")
    void eachBranchConstrainsTheDiscriminator() {
        assertThat(allowedValues("CompanyPublicProfileDto")).containsExactly("COMPANY");
        assertThat(allowedValues("InfluencerPublicProfileDto")).containsExactly("INFLUENCER");
    }

    @Test
    @DisplayName("and requires it, so a body missing it matches neither")
    void eachBranchRequiresTheDiscriminator() {
        for (String branch : new String[] {"CompanyPublicProfileDto", "InfluencerPublicProfileDto"}) {
            JsonNode required = schema(branch).path("required");
            assertThat(required.toString()).as(branch).contains("profileType");
        }
    }

    @Test
    @DisplayName("the union names the property the two branches are told apart by")
    void theUnionDeclaresTheDiscriminator() {
        assertThat(schema("PublicProfileDto").path("discriminator").path("propertyName").asText())
                .isEqualTo("profileType");
    }

    @Test
    @DisplayName("the influencer branch describes its body, rather than being an empty object")
    void theInfluencerBranchHasAContract() {
        // It published `{"type":"object"}` until the union named its implementations, so the
        // endpoint that returns one had no contract at all and every generated client said `any`.
        assertThat(schema("InfluencerPublicProfileDto").path("properties").size())
                .isGreaterThan(5);
    }

    private static JsonNode schema(String name) {
        JsonNode node = spec.path("components").path("schemas").path(name);
        assertThat(node.isMissingNode()).as("%s is in the document", name).isFalse();
        return node;
    }

    private static java.util.List<String> allowedValues(String branch) {
        JsonNode values = schema(branch).path("properties").path("profileType").path("enum");
        java.util.List<String> allowed = new java.util.ArrayList<>();
        values.forEach(value -> allowed.add(value.asText()));
        return allowed;
    }
}
