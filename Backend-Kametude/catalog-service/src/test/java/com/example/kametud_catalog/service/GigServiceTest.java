package com.example.kametud_catalog.service;

import com.example.kametud_catalog.client.IdentityClient;
import com.example.kametud_catalog.client.StudentStatusResponse;
import com.example.kametud_catalog.client.StudentProfileSummary;
import com.example.kametud_catalog.dto.GigCreateRequest;
import com.example.kametud_catalog.dto.GigTierDto;
import com.example.kametud_catalog.entity.Gig;
import com.example.kametud_catalog.exception.StudentPublicationForbiddenException;
import com.example.kametud_catalog.repository.GigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class GigServiceTest {

    @Mock
    private GigRepository gigRepository;

    @Mock
    private IdentityClient identityClient;

    @InjectMocks
    private GigService gigService;

    @Test
    void createPublishedGigShouldRejectStudentWhoCannotPublish() {
        UUID studentId = UUID.randomUUID();
        GigCreateRequest request = createRequest(studentId, true);
        when(identityClient.getStudentStatus(studentId)).thenReturn(new StudentStatusResponse(false, false));

        assertThrows(StudentPublicationForbiddenException.class, () -> gigService.createGig(studentId, request));

        verify(gigRepository, never()).save(any(Gig.class));
    }

    @Test
    void createDraftShouldNotCallIdentityService() {
        GigCreateRequest request = createRequest(UUID.randomUUID(), false);
        UUID authenticatedStudentId = UUID.randomUUID();
        when(gigRepository.save(any(Gig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(identityClient.getStudentProfile(authenticatedStudentId))
                .thenReturn(new StudentProfileSummary("Ada", "Etudiante"));

        gigService.createGig(authenticatedStudentId, request);

        verify(identityClient, never()).getStudentStatus(any(UUID.class));
        verify(identityClient).getStudentProfile(authenticatedStudentId);
        verify(gigRepository).save(any(Gig.class));
    }

    @Test
    void deactivatesEveryGigWhenIdentityBansStudent() {
        UUID studentId = UUID.randomUUID();
        when(gigRepository.deactivateAllByStudentId(studentId)).thenReturn(3);

        int count = gigService.deactivateAllForStudent(studentId);

        assertEquals(3, count);
        verify(gigRepository).deactivateAllByStudentId(studentId);
    }

    private GigCreateRequest createRequest(UUID studentId, boolean published) {
        return GigCreateRequest.builder()
                .studentId(studentId)
                .title("Developpement API")
                .description("Creation d'une API Spring Boot")
                .category("Developpement")
                .location("Paris")
                .tierBasique(tier("Basic", "Simple endpoint", "10.00", 7))
                .tierStandard(tier("Standard", "CRUD complet", "25.00", 5))
                .tierPremium(tier("Premium", "API documentee", "45.00", 2))
                .published(published)
                .build();
    }

    private GigTierDto tier(String title, String description, String price, int deliveryDays) {
        return GigTierDto.builder()
                .title(title)
                .description(description)
                .price(new BigDecimal(price))
                .deliveryDays(deliveryDays)
                .build();
    }
}
