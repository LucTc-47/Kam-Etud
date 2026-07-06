package com.example.kametud_catalog.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GigCreateRequestTest {

    @Test
    void shouldInstantiateGigCreateRequest() {
        GigCreateRequest request = new GigCreateRequest();

        assertNotNull(request);
    }
}
