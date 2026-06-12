package com.sm.instagram.platform.e2e.multiuser.actor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents a named actor in a multi-user scenario.
 * Each actor has their own isolated session with real cookies.
 *
 * <p>Actors are created during login steps and registered with the ActorRegistry.
 * They can then make authenticated HTTP requests using their own session cookies,
 * allowing multiple users to interact in the same scenario without session bleeding.
 *
 * <p>Example usage in step definitions:
 * <pre>
 * Actor actor = actorRegistry.get("FashionCo");
 * ResponseEntity response = actor.post(restTemplate, url("/campaigns"), request);
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class Actor {

    @Getter
    private final String name;

    @Getter
    private final UserSession session;

    // Resources created by this actor (campaigns, applications, etc.)
    private final Map<String, Object> createdResources = new LinkedHashMap<>();

    /**
     * Makes authenticated GET request using this actor's real session cookies.
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @return the response
     */
    public ResponseEntity<Map> get(TestRestTemplate restTemplate, String url) {
        HttpEntity<Void> entity = new HttpEntity<>(session.buildAuthHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' GET {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Makes authenticated GET request with specific response type.
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @param responseType the expected response type
     * @param <T> the response type
     * @return the response
     */
    public <T> ResponseEntity<T> get(TestRestTemplate restTemplate, String url, Class<T> responseType) {
        HttpEntity<Void> entity = new HttpEntity<>(session.buildAuthHeaders());
        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' GET {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Makes authenticated POST request using this actor's real session cookies.
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @param body the request body
     * @param <T> the body type
     * @return the response
     */
    public <T> ResponseEntity<Map> post(TestRestTemplate restTemplate, String url, T body) {
        HttpEntity<T> entity = new HttpEntity<>(body, session.buildAuthHeaders());
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' POST {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Makes authenticated POST request with specific response type.
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @param body the request body
     * @param responseType the expected response type
     * @param <T> the body type
     * @param <R> the response type
     * @return the response
     */
    public <T, R> ResponseEntity<R> post(TestRestTemplate restTemplate, String url, T body, Class<R> responseType) {
        HttpEntity<T> entity = new HttpEntity<>(body, session.buildAuthHeaders());
        ResponseEntity<R> response = restTemplate.postForEntity(url, entity, responseType);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' POST {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Makes authenticated PUT request.
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @param body the request body
     * @param <T> the body type
     * @return the response
     */
    public <T> ResponseEntity<Map> put(TestRestTemplate restTemplate, String url, T body) {
        HttpEntity<T> entity = new HttpEntity<>(body, session.buildAuthHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' PUT {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Makes authenticated PATCH request.
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @param body the request body
     * @param <T> the body type
     * @return the response
     */
    public <T> ResponseEntity<Map> patch(TestRestTemplate restTemplate, String url, T body) {
        HttpEntity<T> entity = new HttpEntity<>(body, session.buildAuthHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PATCH, entity, Map.class);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' PATCH {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Makes authenticated PATCH request without body.
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @return the response
     */
    public ResponseEntity<Map> patch(TestRestTemplate restTemplate, String url) {
        HttpEntity<Void> entity = new HttpEntity<>(session.buildAuthHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PATCH, entity, Map.class);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' PATCH {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Makes authenticated PUT request without body (for query-param-only endpoints).
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @return the response
     */
    public ResponseEntity<Map> put(TestRestTemplate restTemplate, String url) {
        HttpEntity<Void> entity = new HttpEntity<>(session.buildAuthHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' PUT {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Makes authenticated DELETE request.
     *
     * @param restTemplate the TestRestTemplate to use
     * @param url the full URL to request
     * @return the response
     */
    public ResponseEntity<Map> delete(TestRestTemplate restTemplate, String url) {
        HttpEntity<Void> entity = new HttpEntity<>(session.buildAuthHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Map.class);
        session.setLastResponse(response);
        log.debug("[E2E] Actor '{}' DELETE {} -> {}", name, url, response.getStatusCode());
        return response;
    }

    /**
     * Stores a resource created by this actor for later reference.
     * Resources are stored with a key (e.g., "campaign:Summer2025") and can be
     * retrieved later in subsequent steps.
     *
     * @param key the resource key
     * @param resource the resource to store
     */
    public void storeResource(String key, Object resource) {
        createdResources.put(key, resource);
        log.debug("[E2E] Actor '{}' stored resource: {}", name, key);
    }

    /**
     * Retrieves a previously stored resource.
     *
     * @param key the resource key
     * @param <T> the expected resource type
     * @return the resource or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getResource(String key) {
        return (T) createdResources.get(key);
    }

    /**
     * Retrieves a previously stored resource, throwing if not found.
     *
     * @param key the resource key
     * @param <T> the expected resource type
     * @return the resource
     * @throws IllegalStateException if resource not found
     */
    @SuppressWarnings("unchecked")
    public <T> T requireResource(String key) {
        T resource = (T) createdResources.get(key);
        if (resource == null) {
            throw new IllegalStateException(
                String.format("Actor '%s' has no resource '%s'. Available: %s", name, key, createdResources.keySet()));
        }
        return resource;
    }

    /**
     * Checks if actor has a specific resource.
     *
     * @param key the resource key
     * @return true if resource exists
     */
    public boolean hasResource(String key) {
        return createdResources.containsKey(key);
    }

    /**
     * Returns all resource keys stored by this actor.
     *
     * @return set of resource keys
     */
    public Set<String> resourceKeys() {
        return createdResources.keySet();
    }

    /**
     * Gets the last HTTP response for this actor.
     *
     * @return the last response or null
     */
    public ResponseEntity<?> getLastResponse() {
        return session.getLastResponse();
    }

    /**
     * Gets the last response status code.
     *
     * @return status code value or -1 if no response
     */
    public int getLastStatusCode() {
        ResponseEntity<?> response = session.getLastResponse();
        return response != null ? response.getStatusCode().value() : -1;
    }

    @Override
    public String toString() {
        return String.format("Actor[name=%s, role=%s, authenticated=%s]",
            name, session.getRole(), session.isAuthenticated());
    }
}
