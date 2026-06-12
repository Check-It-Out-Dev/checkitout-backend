package com.sm.instagram.platform.servicetype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceTypeDto {
    private Long id;
    @NotBlank(message = "{validation.serviceType.name.required}")
    @Size(min = 2, max = 100, message = "{validation.serviceType.name.size}")
    private String name;
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    @Size(max = 100, message = "Category cannot exceed 100 characters")
    private String category;
}
