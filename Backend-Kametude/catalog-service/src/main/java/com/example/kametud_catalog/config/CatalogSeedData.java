package com.example.kametud_catalog.config;

import com.example.kametud_catalog.entity.Category;
import com.example.kametud_catalog.entity.City;
import com.example.kametud_catalog.repository.CategoryRepository;
import com.example.kametud_catalog.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class CatalogSeedData implements CommandLineRunner {
    private final CategoryRepository categoryRepository;
    private final CityRepository cityRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() == 0) {
            categoryRepository.saveAll(List.of(
                    category("Académique"), category("Numérique"),
                    category("Aide à domicile"), category("Livraison & Courses"),
                    category("Bricolage"), category("Événementiel"),
                    category("Beauté & Bien-être")
            ));
        }
        if (cityRepository.count() == 0) {
            cityRepository.saveAll(List.of(
                    city("Yaoundé"), city("Douala"), city("Bafoussam"),
                    city("Bamenda"), city("Garoua"), city("Maroua"),
                    city("Bertoua"), city("Ebolowa"), city("Ngaoundéré"), city("Buea")
            ));
        }
    }

    private Category category(String name) {
        return Category.builder().name(name).active(true).build();
    }

    private City city(String name) {
        return City.builder().name(name).active(true).build();
    }
}
