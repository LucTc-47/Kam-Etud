package com.example.kametud_catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GigCreateRequest {

    /* Ancien contrat : @NotNull et valeur fournie par le navigateur.
       Ce champ reste lisible pour compatibilite mais le service l'ignore :
       le proprietaire vient maintenant de l'en-tete JWT X-User-Id. */
    private UUID studentId;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 5000)
    private String description;

    @NotBlank
    @Size(max = 100)
    private String category;

    @NotBlank
    @Size(max = 100)
    private String location;

    @Valid
    @NotNull
    private GigTierDto tierBasique;

    @Valid
    @NotNull
    private GigTierDto tierStandard;

    @Valid
    @NotNull
    private GigTierDto tierPremium;

    private boolean published;

    @Builder.Default
    private List<String> images = new ArrayList<>();

    private Double gpsLat;
    private Double gpsLng;
}
