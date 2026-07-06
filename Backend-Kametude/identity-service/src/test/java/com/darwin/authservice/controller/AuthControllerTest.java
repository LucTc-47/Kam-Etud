package com.darwin.authservice.controller;

import com.darwin.authservice.dto.AuthResponse;
import com.darwin.authservice.dto.RegisterRequest;
import com.darwin.authservice.entity.Role;
import com.darwin.authservice.exception.EmailAlreadyUsedException;
import com.darwin.authservice.security.JwtAuthFilter;
import com.darwin.authservice.security.JwtUtils;
import com.darwin.authservice.service.AuthService;
// Ancien import Jackson 2 / Boot 3 :
// import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Anciens imports Boot 3 :
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void register_shouldReturn201_whenValid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@darwin.com");
        request.setPassword("password123");
        request.setRole(Role.USER);

        AuthResponse response = AuthResponse.builder()
                .token("mock_token")
                .refreshToken("mock_refresh")
                .email("test@darwin.com")
                .role("user")
                .build();

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mock_token"))
                .andExpect(jsonPath("$.email").value("test@darwin.com"))
                .andExpect(jsonPath("$.role").value("user"));
    }

    @Test
    @WithMockUser
    void register_shouldReturn409WithReadableMessage_whenEmailExists() throws Exception {
        doThrow(new EmailAlreadyUsedException("test@darwin.com"))
                .when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@darwin.com\",\"password\":\"password123\",\"role\":\"CLIENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cet email est deja utilise : test@darwin.com"));
    }
}
