package com.example.kametud_catalog.controller;

import com.example.kametud_catalog.client.IdentityClient;
import com.example.kametud_catalog.client.StudentStatusResponse;
import com.example.kametud_catalog.client.StudentProfileSummary;
import com.example.kametud_catalog.dto.GigCreateRequest;
import com.example.kametud_catalog.dto.GigTierDto;
import com.example.kametud_catalog.entity.Gig;
import com.example.kametud_catalog.entity.City;
import com.example.kametud_catalog.repository.GigRepository;
import com.example.kametud_catalog.repository.CategoryRepository;
import com.example.kametud_catalog.repository.CityRepository;
// Ancien import Jackson 2 / Boot 3 :
// import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Ancien import Boot 3 :
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GigControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GigRepository gigRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CityRepository cityRepository;

    @MockitoBean
    private IdentityClient identityClient;

    @BeforeEach
    void setUp() {
        gigRepository.deleteAll();
        categoryRepository.deleteAll();
        cityRepository.deleteAll();
        reset(identityClient);
    }

    @Test
    void createPublishedGigShouldPersistJsonTiersAndReturnCreated() throws Exception {
        UUID studentId = UUID.randomUUID();
        GigCreateRequest request = createRequest(studentId, true);
        when(identityClient.getStudentStatus(studentId)).thenReturn(new StudentStatusResponse(true, false));
        when(identityClient.getStudentProfile(studentId)).thenReturn(new StudentProfileSummary("Ada", "Etudiante"));

        mockMvc.perform(post("/api/gigs")
                        .header("X-User-Id", studentId)
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/gigs/")))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.tierBasique.name").value("Basic"))
                .andExpect(jsonPath("$.studentName").value("Ada Etudiante"))
                .andExpect(jsonPath("$.tierPremium.deliveryDays").value(2));

        List<Gig> persistedGigs = gigRepository.findAll();
        assertThat(persistedGigs).hasSize(1);
        Gig persistedGig = persistedGigs.get(0);
        assertThat(persistedGig.getStudentId()).isEqualTo(studentId);
        assertThat(persistedGig.getTierBasique().getPrice()).isEqualByComparingTo("10.00");
        assertThat(persistedGig.getTierStandard().getDescription()).isEqualTo("CRUD complet");
        assertThat(persistedGig.getTierPremium().getDeliveryDays()).isEqualTo(2);
        assertThat(persistedGig.isPublished()).isTrue();
        verify(identityClient).getStudentStatus(studentId);
    }

    @Test
    void createGigShouldRejectInvalidPayload() throws Exception {
        GigCreateRequest request = createRequest(UUID.randomUUID(), false);
        request.setTitle(" ");
        request.getTierBasique().setPrice(BigDecimal.ZERO);

        mockMvc.perform(post("/api/gigs")
                        .header("X-User-Id", request.getStudentId())
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").exists())
                .andExpect(jsonPath("$.details['tierBasique.price']").exists());

        assertThat(gigRepository.findAll()).isEmpty();
        verifyNoInteractions(identityClient);
    }

    @Test
    void createPublishedGigShouldRejectUnverifiedStudent() throws Exception {
        UUID studentId = UUID.randomUUID();
        GigCreateRequest request = createRequest(studentId, true);
        when(identityClient.getStudentStatus(studentId)).thenReturn(new StudentStatusResponse(false, false));

        mockMvc.perform(post("/api/gigs")
                        .header("X-User-Id", studentId)
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(gigRepository.findAll()).isEmpty();
        verify(identityClient).getStudentStatus(studentId);
    }

    @Test
    void searchGigsShouldApplyCategoryAndLocationFiltersToPublishedGigs() throws Exception {
        UUID studentId = UUID.randomUUID();
        saveGig(studentId, "Logo express", "Design", "Paris", true);
        saveGig(studentId, "Logo lyonnais", "Design", "Lyon", true);
        saveGig(studentId, "Cours de maths", "Cours", "Paris", true);
        saveGig(studentId, "Draft logo", "Design", "Paris", false);

        mockMvc.perform(get("/api/gigs")
                        .param("category", "design")
                        .param("location", "par"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Logo express"))
                .andExpect(jsonPath("$[0].published").value(true));
    }

    @Test
    void searchGigsWithoutFiltersShouldReturnOnlyPublishedActiveGigs() throws Exception {
        UUID studentId = UUID.randomUUID();
        saveGig(studentId, "Gig public", "Design", "Douala", true);
        saveGig(studentId, "Brouillon", "Design", "Douala", false);

        mockMvc.perform(get("/api/gigs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Gig public"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].published").value(true));
    }

    @Test
    void getGigShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/gigs/{gigId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void publishGigShouldVerifyStudentBeforePublishing() throws Exception {
        UUID studentId = UUID.randomUUID();
        Gig draft = saveGig(studentId, "API draft", "Developpement", "Paris", false);
        when(identityClient.getStudentStatus(studentId)).thenReturn(new StudentStatusResponse(true, false));

        mockMvc.perform(patch("/api/gigs/{gigId}/publish", draft.getId()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/gigs/{gigId}/publish", draft.getId())
                        .header("X-User-Id", studentId)
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));

        assertThat(gigRepository.findById(draft.getId())).get().extracting(Gig::isPublished).isEqualTo(true);
        verify(identityClient).getStudentStatus(studentId);
    }

    @Test
    void ownerShouldUpdateExistingGigWhileAnotherStudentIsRejected() throws Exception {
        UUID studentId = UUID.randomUUID();
        Gig gig = saveGig(studentId, "Ancien titre", "Design", "Douala", false);
        GigCreateRequest request = createRequest(UUID.randomUUID(), false);
        request.setTitle("Nouveau titre");
        request.setLocation("Yaounde");
        request.getTierBasique().setPrice(new BigDecimal("15000"));

        mockMvc.perform(put("/api/gigs/{gigId}", gig.getId())
                        .header("X-User-Id", UUID.randomUUID())
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/gigs/{gigId}", gig.getId())
                        .header("X-User-Id", studentId)
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Nouveau titre"))
                .andExpect(jsonPath("$.location").value("Yaounde"))
                .andExpect(jsonPath("$.tierBasique.price").value(15000));

        Gig updated = gigRepository.findById(gig.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Nouveau titre");
        assertThat(updated.getStudentId()).isEqualTo(studentId);
        assertThat(updated.getOrderCount()).isEqualTo(gig.getOrderCount());
    }

    @Test
    void internalRatingUpdateShouldRequireServiceTokenAndPersistAggregate() throws Exception {
        Gig gig = saveGig(UUID.randomUUID(), "Service note", "Design", "Douala", true);
        String body = "{\"rating\":4.25,\"reviewCount\":3}";

        mockMvc.perform(patch("/api/gigs/internal/{gigId}/rating", gig.getId())
                        .header("X-Internal-Service-Token", "incorrect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/gigs/internal/{gigId}/rating", gig.getId())
                        .header("X-Internal-Service-Token", "change-this-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4.25))
                .andExpect(jsonPath("$.reviewCount").value(3));

        Gig updated = gigRepository.findById(gig.getId()).orElseThrow();
        assertThat(updated.getRating()).isEqualByComparingTo("4.25");
        assertThat(updated.getReviewCount()).isEqualTo(3);
    }

    @Test
    void referenceEndpointsFilterCitiesAndRequireAdminForMutations() throws Exception {
        cityRepository.save(City.builder().name("Douala").active(true).build());
        cityRepository.save(City.builder().name("Ville masquee").active(false).build());

        mockMvc.perform(get("/api/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Douala"));

        mockMvc.perform(get("/api/cities").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(post("/api/categories")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Design\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/categories")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Design\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Design"))
                .andExpect(jsonPath("$.active").value(true));
    }

    private Gig saveGig(UUID studentId, String title, String category, String location, boolean published) {
        return gigRepository.save(Gig.builder()
                .studentId(studentId)
                .title(title)
                .description("Description " + title)
                .category(category)
                .location(location)
                .rating(BigDecimal.ZERO)
                .tierBasique(tier("Basic", "Simple endpoint", "10.00", 7))
                .tierStandard(tier("Standard", "CRUD complet", "25.00", 5))
                .tierPremium(tier("Premium", "API documentee", "45.00", 2))
                .published(published)
                .build());
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
