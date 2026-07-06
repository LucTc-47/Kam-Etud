package net.codejava.business_service;

import net.codejava.business_service.client.*;
import net.codejava.business_service.repository.*;
import net.codejava.business_service.entity.Dispute;
import net.codejava.business_service.entity.Order;
import net.codejava.business_service.enums.DisputeStatus;
import net.codejava.business_service.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BusinessWorkflowIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrderRepository orderRepository;
    @Autowired DeliverableRepository deliverableRepository;
    @Autowired RevisionRepository revisionRepository;
    @Autowired DisputeRepository disputeRepository;
    @Autowired AbuseReportRepository abuseReportRepository;
    @Autowired ReviewRepository reviewRepository;

    @MockitoBean IdentityClient identityClient;
    @MockitoBean CatalogClient catalogClient;
    @MockitoBean SupportClient supportClient;
    @MockitoBean PaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        abuseReportRepository.deleteAll();
        disputeRepository.deleteAll();
        revisionRepository.deleteAll();
        deliverableRepository.deleteAll();
        orderRepository.deleteAll();
        reset(identityClient, catalogClient, supportClient, paymentClient);
    }

    @Test
    void securedOrderWorkflowUsesJwtAndCatalogData() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID otherStudentId = UUID.randomUUID();
        UUID gigId = UUID.randomUUID();
        when(identityClient.getProfile(clientId)).thenReturn(new ProfileSummary("Grace", "Cliente"));
        when(catalogClient.getGig(gigId)).thenReturn(gig(gigId, studentId));

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gigId":"%s","tier":"standard","description":"API metier",
                                 "paymentMethod":"mtn","clientId":"%s","budget":1}
                                """.formatted(gigId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.clientName").value("Grace Cliente"))
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.gigTitle").value("Developpement API"))
                .andExpect(jsonPath("$.budget").value(25000.0))
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn();
        UUID orderId = responseId(created);

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("X-User-Id", otherStudentId).header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"in_progress\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders/internal/{id}/payment-held", orderId)
                        .header("X-Internal-Service-Token", "incorrect"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders/internal/{id}/payment-held", orderId)
                        .header("X-Internal-Service-Token", "change-this-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        update(orderId, studentId, "STUDENT", "{\"status\":\"in_progress\"}", "in_progress");
        update(orderId, studentId, "STUDENT",
                "{\"status\":\"delivered\",\"deliverableUrl\":\"deliverables/work.zip\",\"deliverableNote\":\"Termine\"}",
                "delivered");
        update(orderId, clientId, "CLIENT", "{\"status\":\"completed\"}", "completed");

        MvcResult createdReview = mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"rating\":5,\"text\":\"Excellent\"}".formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.reviewerName").value("Grace Cliente"))
                .andReturn();
        verify(catalogClient).updateRating(gigId, 5.0, 1);

        mockMvc.perform(get("/api/student-stats/{id}", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed_jobs").value(1))
                .andExpect(jsonPath("$.review_count").value(1))
                .andExpect(jsonPath("$.rating").value(5.0))
                .andExpect(jsonPath("$.xp").value(120));

        UUID reviewId = responseId(createdReview);
        mockMvc.perform(patch("/api/reviews/{id}/report", reviewId)
                        .header("X-User-Id", studentId).header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Avis injuste\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reported").value(true));
        mockMvc.perform(get("/api/reviews/reported")
                        .header("X-User-Role", "MODERATOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(patch("/api/reviews/{id}/moderate", reviewId)
                        .header("X-User-Id", UUID.randomUUID()).header("X-User-Role", "MODERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"HIDE\",\"note\":\"Contenu masque\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reported").value(false));
        verify(catalogClient).updateRating(gigId, 0.0, 0);
        mockMvc.perform(get("/api/reviews/student/{id}", studentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/orders/mine")
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void internalProposalCreationIsProtectedAndIdempotent() throws Exception {
        UUID proposalId = UUID.randomUUID();
        String body = """
                {"sourceRequestId":"%s","sourceProposalId":"%s","clientId":"%s","clientName":"Client",
                 "studentId":"%s","studentName":"Etudiant","title":"Logo","description":"Logo rouge",
                 "budget":45000,"deliveryDays":3}
                """.formatted(UUID.randomUUID(), proposalId, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/orders/internal/from-proposal")
                        .header("X-Internal-Service-Token", "incorrect")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        MvcResult first = internalOrder(body);
        MvcResult second = internalOrder(body);
        org.assertj.core.api.Assertions.assertThat(responseId(first)).isEqualTo(responseId(second));
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void clientCanOpenDisputeAndModeratorCanResolveIt() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID gigId = UUID.randomUUID();
        when(identityClient.getProfile(clientId)).thenReturn(new ProfileSummary("Client", "Test"));
        when(catalogClient.getGig(gigId)).thenReturn(gig(gigId, studentId));
        UUID orderId = responseId(mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gigId\":\"%s\",\"tier\":\"basique\",\"description\":\"Logo\",\"paymentMethod\":\"mtn\"}".formatted(gigId)))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/orders/internal/{id}/payment-held", orderId)
                        .header("X-Internal-Service-Token", "change-this-internal-token"))
                .andExpect(status().isOk());
        update(orderId, studentId, "STUDENT", "{\"status\":\"in_progress\"}", "in_progress");

        MvcResult dispute = mockMvc.perform(post("/api/disputes")
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"clientStatement\":\"Travail incomplet\"}".formatted(orderId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("open")).andReturn();

        UUID moderatorId = UUID.randomUUID();
        mockMvc.perform(patch("/api/disputes/{id}", responseId(dispute))
                        .header("X-User-Id", moderatorId).header("X-User-Role", "MODERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved_client\",\"moderatorNote\":\"Remboursement\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("resolved_client"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("refunded"));
    }

    @Test
    void moderatorCanReportAbuseAndOnlyAdminCanDecideWithoutChangingTheOrder() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Order order = orderRepository.saveAndFlush(Order.builder()
                .clientId(clientId).clientName("Client Test")
                .studentId(studentId).studentName("Etudiant Test")
                .gigTitle("Mission litigieuse").tier("standard").description("Travail")
                .budget(20000.0).escrowAmount(20000.0).status(OrderStatus.DISPUTED)
                .build());
        Dispute dispute = disputeRepository.saveAndFlush(Dispute.builder()
                .orderId(order.getId()).gigTitle(order.getGigTitle())
                .clientId(clientId).clientName(order.getClientName()).clientStatement("Travail incomplet")
                .clientEvidenceUrl("disputes/client/preuve.pdf")
                .studentId(studentId).studentName(order.getStudentName()).studentStatement("Preuve falsifiee")
                .studentEvidenceUrl("disputes/student/livrable.zip")
                .status(DisputeStatus.UNDER_REVIEW)
                .build());

        String reportBody = """
                {"disputeId":"%s","targetUserId":"%s","reason":"false_evidence",
                 "note":"Les documents fournis semblent avoir ete modifies."}
                """.formatted(dispute.getId(), studentId);

        mockMvc.perform(post("/api/abuse-reports")
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON).content(reportBody))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/abuse-reports")
                        .header("X-User-Id", moderatorId).header("X-User-Role", "MODERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(reportBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetUserId").value(studentId.toString()))
                .andExpect(jsonPath("$.targetRole").value("student"))
                .andExpect(jsonPath("$.reason").value("false_evidence"))
                .andExpect(jsonPath("$.status").value("open"))
                .andReturn();

        mockMvc.perform(post("/api/abuse-reports")
                        .header("X-User-Id", moderatorId).header("X-User-Role", "MODERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content(reportBody))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/abuse-reports")
                        .header("X-User-Role", "MODERATOR"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/abuse-reports")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].clientEvidenceUrl").value("disputes/client/preuve.pdf"));

        mockMvc.perform(patch("/api/admin/abuse-reports/{id}", responseId(created))
                        .header("X-User-Id", adminId).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"WARN\",\"adminNote\":\"Avertissement officiel apres verification.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("warned"));

        verify(supportClient).notify(studentId, "Avertissement de moderation",
                "Avertissement officiel apres verification.", "MODERATION", "/mes-missions");
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DISPUTED);
        org.assertj.core.api.Assertions.assertThat(disputeRepository.findById(dispute.getId()).orElseThrow().getStatus())
                .isEqualTo(DisputeStatus.UNDER_REVIEW);
    }

    @Test
    void autoValidationCompletesDueDeliveryAndRequestsPayout() throws Exception {
        Order order = Order.builder()
                .clientId(UUID.randomUUID()).clientName("Client")
                .studentId(UUID.randomUUID()).studentName("Etudiant")
                .gigTitle("Mission livree").tier("standard").description("Travail")
                .budget(10000.0).escrowAmount(10000.0).revisionsLeft(2)
                .deliveryDate(LocalDateTime.now().minusDays(1))
                .deliveredAt(LocalDateTime.now().minusHours(25))
                .status(OrderStatus.DELIVERED)
                .build();
        order = orderRepository.saveAndFlush(order);

        mockMvc.perform(post("/api/orders/auto-validation/run")
                        .header("X-User-Id", UUID.randomUUID()).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(1))
                .andExpect(jsonPath("$.validated").value(1))
                .andExpect(jsonPath("$.released").value(1))
                .andExpect(jsonPath("$.pending").value(0));

        verify(paymentClient).releaseAutomatically(order.getId());
        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        org.assertj.core.api.Assertions.assertThat(updated.getAutoValidatedAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(updated.getPayoutReleasedAt()).isNotNull();
    }

    private CatalogGig gig(UUID gigId, UUID studentId) {
        return new CatalogGig(gigId, studentId, "Ada Etudiante", "Developpement API", "API complete",
                true, true, tier("Basique", "10000", 7), tier("Standard", "25000", 5), tier("Premium", "50000", 2));
    }

    private CatalogTier tier(String name, String price, int days) {
        return new CatalogTier(name, name, new BigDecimal(price), days);
    }

    private void update(UUID id, UUID userId, String role, String body, String statusValue) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/status", id)
                        .header("X-User-Id", userId).header("X-User-Role", role)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(statusValue));
    }

    private MvcResult internalOrder(String body) throws Exception {
        return mockMvc.perform(post("/api/orders/internal/from-proposal")
                        .header("X-Internal-Service-Token", "change-this-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }
}
