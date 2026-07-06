package com.example.kametud_catalog.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GigResponseTest {

    @Test
    void shouldInstantiateGigResponse() {
        GigResponse response = new GigResponse();

        assertNotNull(response);
    }
}
