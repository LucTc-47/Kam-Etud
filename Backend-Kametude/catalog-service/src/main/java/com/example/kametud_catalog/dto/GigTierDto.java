package com.example.kametud_catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GigTierDto {

    // Ancien constructeur a quatre arguments conserve pour les tests et appels existants.
    public GigTierDto(String title, String description, BigDecimal price, Integer deliveryDays) {
        this(title, description, price, deliveryDays, new ArrayList<>());
    }

    @NotBlank
    @Size(max = 80)
    @JsonProperty("name")
    private String title;

    @NotBlank
    @Size(max = 500)
    private String description;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    @NotNull
    @Positive
    private Integer deliveryDays;

    @Builder.Default
    private List<String> features = new ArrayList<>();
}
