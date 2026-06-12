package com.sm.instagram.platform.city;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import org.modelmapper.Converter;
import org.springframework.stereotype.Component;

@Component
public class CityConverter {

    private final CityRepository cityRepository;

    public CityConverter(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    public Converter<String, City> toCityConverter() {
        return ctx -> {
            String cityName = ctx.getSource();
            if (cityName == null || cityName.trim().isEmpty()) {
                throw new IllegalArgumentException("City must not be null or empty.");
            }
            return cityRepository.findByName(cityName)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", cityName));
        };
    }


    public Converter<City, String> toCityNameConverter() {
        return ctx -> ctx.getSource() != null ? ctx.getSource().getName() : null;
    }
}
