package com.example.kametud_catalog.entity;

import com.example.kametud_catalog.dto.GigTierDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GigTest {

    @Test
    void shouldCreateGigWithFields() {
        UUID gigId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        GigTierDto basicTier = new GigTierDto("Basic", "Starter package", BigDecimal.TEN, 7);
        GigTierDto standardTier = new GigTierDto("Standard", "Balanced package", BigDecimal.valueOf(20), 5);
        GigTierDto premiumTier = new GigTierDto("Premium", "Fast package", BigDecimal.valueOf(30), 2);

        Gig gig = Gig.builder()
                .id(gigId)
                .studentId(studentId)
                .title("Developpeur Java")
                .description("Service de developpement")
                .category("Developpement")
                .location("Paris")
                .tierBasique(basicTier)
                .tierStandard(standardTier)
                .tierPremium(premiumTier)
                .published(true)
                .build();

        assertNotNull(gig);
        assertEquals(gigId, gig.getId());
        assertEquals(studentId, gig.getStudentId());
        assertEquals("Developpeur Java", gig.getTitle());
        assertEquals("Service de developpement", gig.getDescription());
        assertEquals("Developpement", gig.getCategory());
        assertEquals("Paris", gig.getLocation());
        assertEquals(basicTier, gig.getTierBasique());
        assertEquals(standardTier, gig.getTierStandard());
        assertEquals(premiumTier, gig.getTierPremium());
        assertEquals(true, gig.isPublished());
    }
}
