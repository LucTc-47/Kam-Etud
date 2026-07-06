package com.example.kametud_catalog.service;

import com.example.kametud_catalog.dto.CatalogOptionResponse;
import com.example.kametud_catalog.entity.Category;
import com.example.kametud_catalog.entity.City;
import com.example.kametud_catalog.exception.DuplicateReferenceException;
import com.example.kametud_catalog.repository.CategoryRepository;
import com.example.kametud_catalog.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferenceDataService {
    private final CategoryRepository categoryRepository;
    private final CityRepository cityRepository;

    @Transactional(readOnly = true)
    public List<CatalogOptionResponse> categories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(value -> new CatalogOptionResponse(value.getId(), value.getName(), value.isActive()))
                .toList();
    }

    @Transactional
    public CatalogOptionResponse createCategory(String rawName) {
        String name = rawName.trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateReferenceException("Cette categorie existe deja");
        }
        Category saved = categoryRepository.save(Category.builder().name(name).active(true).build());
        return new CatalogOptionResponse(saved.getId(), saved.getName(), saved.isActive());
    }

    @Transactional
    public CatalogOptionResponse updateCategory(UUID id, boolean active) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categorie introuvable"));
        category.setActive(active);
        Category saved = categoryRepository.save(category);
        return new CatalogOptionResponse(saved.getId(), saved.getName(), saved.isActive());
    }

    @Transactional
    public void deleteCategory(UUID id) {
        if (!categoryRepository.existsById(id)) throw new IllegalArgumentException("Categorie introuvable");
        categoryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CatalogOptionResponse> cities(boolean includeInactive) {
        List<City> values = includeInactive
                ? cityRepository.findAllByOrderByNameAsc()
                : cityRepository.findByActiveTrueOrderByNameAsc();
        return values.stream()
                .map(value -> new CatalogOptionResponse(value.getId(), value.getName(), value.isActive()))
                .toList();
    }

    @Transactional
    public CatalogOptionResponse createCity(String rawName) {
        String name = rawName.trim();
        if (cityRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateReferenceException("Cette ville existe deja");
        }
        City saved = cityRepository.save(City.builder().name(name).active(true).build());
        return new CatalogOptionResponse(saved.getId(), saved.getName(), saved.isActive());
    }

    @Transactional
    public void deleteCity(UUID id) {
        if (!cityRepository.existsById(id)) throw new IllegalArgumentException("Ville introuvable");
        cityRepository.deleteById(id);
    }
}
