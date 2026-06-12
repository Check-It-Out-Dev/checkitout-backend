package com.sm.instagram.platform.city;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "city_generator")
    @SequenceGenerator(
        name = "city_generator",
        sequenceName = "city_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;
    
    @Column(nullable = false, unique = true)
    @NotBlank(message = "Name cannot be blank")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;
    
    @Size(max = 255, message = "State cannot exceed 255 characters")
    @Column(name = "state")
    private String state;
    
    @Size(max = 255, message = "Country cannot exceed 255 characters")
    @Column(name = "country", nullable = false)
    private String country = "Polska";
    
    @Override
    public String toString() {
        return name;
    }
}