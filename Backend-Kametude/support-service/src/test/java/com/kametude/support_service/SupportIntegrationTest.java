package com.kametude.support_service;

import com.kametude.support_service.client.*;
import com.kametude.support_service.repository.ChatMessageRepository;
import com.kametude.support_service.repository.NotificationRepository;
import com.kametude.support_service.repository.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupportIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ChatMessageRepository chatRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired StoredFileRepository storedFileRepository;
    @MockitoBean BusinessClient businessClient;
    @MockitoBean IdentityClient identityClient;

    UUID orderId;
    UUID clientId;
    UUID studentId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        chatRepository.deleteAll();
        storedFileRepository.deleteAll();
        reset(businessClient, identityClient);
        orderId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        when(businessClient.getOrder(orderId))
                .thenReturn(new BusinessOrderContext(orderId, clientId, studentId, "API", "in_progress"));
        when(identityClient.getProfile(clientId)).thenReturn(new IdentityProfile("Grace", "Cliente"));
    }

    @Test
    void participantCanChatAndRecipientGetsNotification() throws Exception {
        mockMvc.perform(post("/api/chat/orders/{id}/messages", orderId)
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"content\":\"Bonjour\"}".formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(clientId.toString()))
                .andExpect(jsonPath("$.senderName").value("Grace Cliente"));

        mockMvc.perform(get("/api/chat/orders/{id}/messages", orderId)
                        .header("X-User-Id", studentId).header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/notifications/me")
                        .header("X-User-Id", studentId).header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("NEW_MESSAGE"));

        mockMvc.perform(get("/api/chat/orders/{id}/messages", orderId)
                        .header("X-User-Id", UUID.randomUUID()).header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void privateOrderFileIsAvailableOnlyToParticipantsOrStaff() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "work.txt", "text/plain", "travail".getBytes());
        MvcResult upload = mockMvc.perform(multipart("/api/storage/upload")
                        .file(file).param("visibility", "private").param("category", "deliverables")
                        .param("resourceId", orderId.toString()).header("X-User-Id", studentId))
                .andExpect(status().isOk()).andReturn();
        String filename = objectMapper.readTree(upload.getResponse().getContentAsString()).get("filename").asString();

        mockMvc.perform(get("/api/storage/private/files/{filename}", filename)
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/storage/private/files/{filename}", filename)
                        .header("X-User-Id", UUID.randomUUID()).header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalNotificationEndpointRejectsWrongToken() throws Exception {
        String body = "{\"userId\":\"%s\",\"title\":\"Test\",\"message\":\"Message\",\"type\":\"SYSTEM\"}"
                .formatted(clientId);
        mockMvc.perform(post("/api/notifications/internal")
                        .header("X-Internal-Service-Token", "incorrect")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notifications/internal")
                        .header("X-Internal-Service-Token", "change-this-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
