package com.sm.instagram.platform.city;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CityDto {
    private Long id;
    
    @NotBlank(message = "{validation.city.name.required}")
    @Size(min = 2, max = 50, message = "{validation.city.name.size}")
    private String name;
    
    @Size(max = 255, message = "State cannot exceed 255 characters")
    private String state;
    
    @Size(max = 255, message = "Country cannot exceed 255 characters")
    private String country = "Polska";
}