package com.sm.instagram.platform.contenttype;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for a content type (e.g. POST, REEL, STORY).
 *
 * <p>The sequence generator is pinned to {@code content_type_seq} on the
 * {@code public} schema with allocation 50 — these values match the Liquibase
 * changelog that creates the sequence. Do not rename them without a paired
 * migration, or Hibernate will silently break ID allocation.
 *
 * <p>The {@code name} column is constrained {@code NOT NULL UNIQUE} at the DB
 * layer so the bean validation here is defence-in-depth, not the source of
 * truth.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ContentType {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "content_type_generator")
    @SequenceGenerator(
            name = "content_type_generator",
            sequenceName = "content_type_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "Name cannot be blank")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;
}
