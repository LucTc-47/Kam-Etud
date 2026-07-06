package cm.kametud.requestservice.controller;

import cm.kametud.requestservice.RequestServiceApplication;
import cm.kametud.requestservice.client.IdentityClient;
import cm.kametud.requestservice.client.BusinessClient;
import cm.kametud.requestservice.client.SupportClient;
import cm.kametud.requestservice.client.ProfileSummary;
import cm.kametud.requestservice.client.StudentStatusResponse;
import cm.kametud.requestservice.repository.GigRequestRepository;
import cm.kametud.requestservice.repository.RequestProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Ancien import Boot 3 :
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
// Ancien import Jackson 2 / Boot 3 :
// import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RequestServiceApplication.class)
@AutoConfigureMockMvc
class RequestWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GigRequestRepository requestRepository;

    @Autowired
    private RequestProposalRepository proposalRepository;

    @MockitoBean
    private IdentityClient identityClient;

    @MockitoBean
    private BusinessClient businessClient;

    @MockitoBean
    private SupportClient supportClient;

    @BeforeEach
    void setUp() {
        proposalRepository.deleteAll();
        requestRepository.deleteAll();
        reset(identityClient);
        reset(businessClient);
        reset(supportClient);
    }

    @Test
    void clientCanCreateRequestAndAcceptOnlyOneVerifiedStudentProposal() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID otherClientId = UUID.randomUUID();
        UUID firstStudentId = UUID.randomUUID();
        UUID secondStudentId = UUID.randomUUID();
        when(identityClient.getProfile(clientId)).thenReturn(new ProfileSummary("Grace", "Cliente"));
        when(identityClient.getStudentStatus(firstStudentId)).thenReturn(new StudentStatusResponse(true, false));
        when(identityClient.getStudentStatus(secondStudentId)).thenReturn(new StudentStatusResponse(true, false));
        when(identityClient.getProfile(firstStudentId)).thenReturn(new ProfileSummary("Ada", "Etudiante"));
        when(identityClient.getProfile(secondStudentId)).thenReturn(new ProfileSummary("Linus", "Etudiant"));

        MvcResult createdRequest = mockMvc.perform(post("/api/v1/requests")
                        .header("X-User-Id", clientId)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        // Les anciennes valeurs d'identite sont volontairement fausses : le JWT doit gagner.
                        .content("""
                                {
                                  "title": "Application mobile",
                                  "description": "Construire le MVP",
                                  "budget": 250000,
                                  "category": "Developpement",
                                  "location": "Douala",
                                  "deadline": "2026-08-10",
                                  "clientId": "%s",
                                  "clientName": "Utilisateur usurpe"
                                }
                                """.formatted(otherClientId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.clientName").value("Grace Cliente"))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.proposalsCount").value(0))
                .andReturn();
        UUID requestId = responseId(createdRequest);

        UUID firstProposalId = createProposal(requestId, firstStudentId, "Ada Etudiante", 230000);
        UUID secondProposalId = createProposal(requestId, secondStudentId, "Linus Etudiant", 210000);

        mockMvc.perform(put("/api/v1/proposals/{id}/accept", firstProposalId)
                        .header("X-User-Id", otherClientId)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/proposals/{id}/accept", firstProposalId)
                        .header("X-User-Id", clientId)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.requestStatus").value("assigned"));

        mockMvc.perform(get("/api/v1/requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("assigned"))
                .andExpect(jsonPath("$.acceptedProposalId").value(firstProposalId.toString()))
                .andExpect(jsonPath("$.proposalsCount").value(2));

        mockMvc.perform(get("/api/v1/proposals/request/{requestId}", requestId)
                        .header("X-User-Id", clientId)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(firstProposalId)).value("accepted"))
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(secondProposalId)).value("rejected"));

        mockMvc.perform(get("/api/v1/proposals/request/{requestId}", requestId)
                        .header("X-User-Id", firstStudentId)
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentId").value(firstStudentId.toString()));
    }

    @Test
    void cancellingARequestRejectsItsPendingProposals() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(identityClient.getProfile(clientId)).thenReturn(new ProfileSummary("Marie", "Client"));
        when(identityClient.getStudentStatus(studentId)).thenReturn(new StudentStatusResponse(true, false));
        when(identityClient.getProfile(studentId)).thenReturn(new ProfileSummary("Paul", "Etudiant"));

        MvcResult createdRequest = mockMvc.perform(post("/api/v1/requests")
                        .header("X-User-Id", clientId)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Logo",
                                  "budget": 50000,
                                  "category": "Design"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID requestId = responseId(createdRequest);
        createProposal(requestId, studentId, "Paul Etudiant", 45000);

        mockMvc.perform(patch("/api/v1/requests/{id}/cancel", requestId)
                        .header("X-User-Id", clientId)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));

        mockMvc.perform(get("/api/v1/proposals/mine")
                        .header("X-User-Id", studentId)
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("rejected"))
                .andExpect(jsonPath("$[0].requestStatus").value("cancelled"));
    }

    @Test
    void unverifiedStudentCannotCreateProposal() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(identityClient.getProfile(clientId)).thenReturn(new ProfileSummary("Client", "Test"));
        when(identityClient.getStudentStatus(studentId)).thenReturn(new StudentStatusResponse(false, false));

        MvcResult createdRequest = mockMvc.perform(post("/api/v1/requests")
                        .header("X-User-Id", clientId)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Besoin urgent\",\"budget\":10000,\"category\":\"Cours\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(post("/api/v1/proposals")
                        .header("X-User-Id", studentId)
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "message": "Je peux aider",
                                  "price": 9000,
                                  "deliveryDays": 2
                                }
                                """.formatted(responseId(createdRequest))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private UUID createProposal(UUID requestId, UUID studentId, String expectedName, double price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/proposals")
                        .header("X-User-Id", studentId)
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "studentId": "%s",
                                  "studentName": "Identite usurpee",
                                  "message": "Je peux realiser cette mission",
                                  "price": %s,
                                  "deliveryDays": 5
                                }
                                """.formatted(requestId, UUID.randomUUID(), price)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.studentName").value(expectedName))
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn();
        return responseId(result);
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }
}
