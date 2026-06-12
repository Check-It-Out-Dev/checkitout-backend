package com.sm.instagram.platform.e2e.multiuser.actor;

import io.cucumber.spring.ScenarioScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing multiple actors in a scenario.
 * Each actor has their own isolated session with real cookies.
 *
 * <p>This is {@code @ScenarioScoped} - automatically reset between scenarios.
 * Multiple actors can be logged in simultaneously, each with their own
 * session cookies, enabling complex multi-user business flow testing.
 *
 * <p>Example usage:
 * <pre>
 * // In step definitions
 * UserSession session = authService.login("FashionCo", uid, email, pass, "COMPANY");
 * actorRegistry.register("FashionCo", session);
 *
 * // Later in the same scenario
 * Actor company = actorRegistry.get("FashionCo");
 * company.post(restTemplate, url("/campaigns"), request);
 * </pre>
 */
@Component
@ScenarioScope
@Slf4j
public class ActorRegistry {

    private final Map<String, Actor> actors = new ConcurrentHashMap<>();
    private String currentActorName;

    /**
     * Registers a new actor with their authenticated session.
     * Automatically becomes the current actor.
     *
     * @param name the actor's alias (e.g., "FashionCo", "StyleGuru")
     * @param session the authenticated session
     * @return the created Actor
     */
    public Actor register(String name, UserSession session) {
        if (actors.containsKey(name)) {
            log.warn("[E2E] Actor '{}' already registered, replacing session", name);
        }
        Actor actor = new Actor(name, session);
        actors.put(name, actor);
        currentActorName = name;
        log.info("[E2E] Registered actor '{}' as {} with {} session",
            name,
            session.getRole(),
            session.isAuthenticated() ? "full" : "partial");
        return actor;
    }

    /**
     * Gets the current active actor.
     *
     * @return the current actor
     * @throws IllegalStateException if no actor is registered
     */
    public Actor current() {
        if (currentActorName == null) {
            throw new IllegalStateException(
                "No current actor. Register an actor first using login step.");
        }
        return actors.get(currentActorName);
    }

    /**
     * Gets the current actor if one exists.
     *
     * @return Optional containing current actor or empty
     */
    public Optional<Actor> currentOptional() {
        if (currentActorName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(actors.get(currentActorName));
    }

    /**
     * Gets actor by name.
     *
     * @param name the actor's alias
     * @return the actor
     * @throws IllegalStateException if actor not found
     */
    public Actor get(String name) {
        Actor actor = actors.get(name);
        if (actor == null) {
            throw new IllegalStateException(
                String.format("Actor '%s' not registered. Available actors: %s", name, actors.keySet()));
        }
        return actor;
    }

    /**
     * Gets actor by name if registered.
     *
     * @param name the actor's alias
     * @return Optional containing actor or empty
     */
    public Optional<Actor> getOptional(String name) {
        return Optional.ofNullable(actors.get(name));
    }

    /**
     * Switches to a different actor.
     * Their session cookies will be used for subsequent requests.
     *
     * @param name the actor's alias
     * @throws IllegalStateException if actor not registered
     */
    public void switchTo(String name) {
        if (!actors.containsKey(name)) {
            throw new IllegalStateException(
                String.format("Cannot switch to '%s'. Not registered. Available actors: %s", name, actors.keySet()));
        }
        String previousActor = currentActorName;
        currentActorName = name;
        log.info("[E2E] Switched from '{}' to '{}'", previousActor, name);
    }

    /**
     * Checks if an actor is registered.
     *
     * @param name the actor's alias
     * @return true if registered
     */
    public boolean isRegistered(String name) {
        return actors.containsKey(name);
    }

    /**
     * Returns all registered actor names.
     *
     * @return set of actor names
     */
    public Set<String> registeredActors() {
        return actors.keySet();
    }

    /**
     * Returns all registered actors.
     *
     * @return collection of actors
     */
    public Collection<Actor> allActors() {
        return actors.values();
    }

    /**
     * Returns number of registered actors.
     *
     * @return actor count
     */
    public int size() {
        return actors.size();
    }

    /**
     * Checks if any actors are registered.
     *
     * @return true if at least one actor registered
     */
    public boolean hasActors() {
        return !actors.isEmpty();
    }

    /**
     * Gets the name of the current actor.
     *
     * @return current actor name or null
     */
    public String getCurrentActorName() {
        return currentActorName;
    }

    /**
     * Finds actors by role.
     *
     * @param role the role to search for (COMPANY, INFLUENCER, ADMIN)
     * @return list of actors with that role
     */
    public java.util.List<Actor> findByRole(String role) {
        return actors.values().stream()
            .filter(a -> role.equals(a.getSession().getRole()))
            .collect(Collectors.toList());
    }

    /**
     * Gets a summary of all registered actors for debugging.
     *
     * @return formatted summary string
     */
    public String summary() {
        if (actors.isEmpty()) {
            return "No actors registered";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Registered actors (").append(actors.size()).append("):\n");
        for (Actor actor : actors.values()) {
            boolean isCurrent = actor.getName().equals(currentActorName);
            sb.append(String.format("  %s %s (%s) - %s%n",
                isCurrent ? ">" : " ",
                actor.getName(),
                actor.getSession().getRole(),
                actor.getSession().isAuthenticated() ? "authenticated" : "partial session"));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("ActorRegistry[actors=%s, current=%s]", actors.keySet(), currentActorName);
    }
}
