package com.sm.instagram.platform.city;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CityRepository extends BaseRepository<City, Long> {
    @Cacheable(value = "citiesCache", key = "#name")
    Optional<City> findByName(String name);
    
    // New methods for state and country filtering
    List<City> findByState(String state);
    
    List<City> findByCountry(String country);
    
    List<City> findByStateAndCountry(String state, String country);
    
    List<City> findByCountryOrderByStateAscNameAsc(String country);
    
    Optional<City> findByNameAndState(String name, String state);
    
    // Get all unique states for a country
    @Query("SELECT DISTINCT c.state FROM City c WHERE c.country = :country AND c.state IS NOT NULL ORDER BY c.state ASC")
    List<String> findDistinctStatesByCountry(@Param("country") String country);
}
