package com.example.kametud_catalog.repository;

import com.example.kametud_catalog.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CityRepository extends JpaRepository<City, UUID> {
    List<City> findByActiveTrueOrderByNameAsc();
    List<City> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
