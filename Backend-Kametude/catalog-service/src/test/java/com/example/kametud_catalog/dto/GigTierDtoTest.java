package com.example.kametud_catalog.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GigTierDtoTest {

    @Test
    void shouldInstantiateGigTierDto() {
        GigTierDto tierDto = new GigTierDto();

        assertNotNull(tierDto);
    }
}
