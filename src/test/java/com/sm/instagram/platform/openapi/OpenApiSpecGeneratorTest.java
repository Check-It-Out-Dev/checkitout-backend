package com.sm.instagram.platform.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sm.instagram.platform.integration.config.ServiceIntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(ServiceIntegrationTestConfig.class)
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiSpecGeneratorTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void generateOpenApiSpec() throws IOException {
        String url = "http://localhost:" + port + "/api/v3/api-docs";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertEquals(200, response.getStatusCode().value(),
                "OpenAPI spec endpoint should return 200");
        assertNotNull(response.getBody(), "OpenAPI spec should not be null");

        String spec = response.getBody();

        assertTrue(spec.contains("\"openapi\""), "Should contain openapi version field");
        assertTrue(spec.contains("\"paths\""), "Should contain paths section");
        assertTrue(spec.contains("\"components\""), "Should contain components section");

        // Verify enums are present as named schemas (enumsAsRef = true)
        assertTrue(spec.contains("\"OpportunityStatus\""),
                "Should contain OpportunityStatus as a named schema");
        assertTrue(spec.contains("\"AccountStatus\""),
                "Should contain AccountStatus as a named schema");

        // Pretty-print the JSON before writing
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        Object json = mapper.readValue(spec, Object.class);
        String prettySpec = mapper.writeValueAsString(json);

        Path outputDir = Paths.get("docs", "openapi");
        Files.createDirectories(outputDir);
        Path outputPath = outputDir.resolve("openapi.json");
        Files.writeString(outputPath, prettySpec, StandardCharsets.UTF_8);

        System.out.println("[OpenAPI] Spec written to: " + outputPath.toAbsolutePath());
        System.out.println("[OpenAPI] Spec size: " + prettySpec.length() + " characters");
    }
}
