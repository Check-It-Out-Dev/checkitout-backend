package com.sm.instagram.platform.servicetype;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceType {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "service_type_generator")
    @SequenceGenerator(
        name = "service_type_generator",
        sequenceName = "service_type_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;
    @NotBlank(message = "Name cannot be blank")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Column(nullable = false, unique = true)
    private String name;
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    @Size(max = 100, message = "Category cannot exceed 100 characters")
    private String category;
}

