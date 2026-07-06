package com.darwin.authservice.service;

import com.darwin.authservice.dto.AuthResponse;
import com.darwin.authservice.dto.RegisterRequest;
import com.darwin.authservice.dto.LoginRequest;
import com.darwin.authservice.entity.Profile;
import com.darwin.authservice.entity.Role;
import com.darwin.authservice.entity.User;
import com.darwin.authservice.exception.EmailAlreadyUsedException;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.UserRepository;
import com.darwin.authservice.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private AuthenticationManager authenticationManager;
    @Spy private ProfileMapper profileMapper = new ProfileMapper();

    @InjectMocks
    private AuthService authService;

    private User mockUser;
    private Profile mockProfile;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@darwin.com")
                .password("encoded_password")
                .role(Role.USER)
                .build();

        mockProfile = Profile.builder()
                .id(UUID.randomUUID())
                .userId(mockUser.getId())
                .email(mockUser.getEmail())
                .role("user")
                .verified(false)
                .build();
    }

    // ✅ Test Register OK
    @Test
    void register_shouldCreateUserAndProfile() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@darwin.com");
        request.setPassword("password123");
        request.setRole(Role.USER);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded_password");
        when(userRepository.save(any())).thenReturn(mockUser);
        when(profileRepository.save(any())).thenReturn(mockProfile);
        when(jwtUtils.generateToken(any())).thenReturn("mock_token");
        when(jwtUtils.generateRefreshToken(any())).thenReturn("mock_refresh");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("test@darwin.com", response.getEmail());
        assertEquals("user", response.getRole());
        assertNotNull(response.getToken());
        assertNotNull(response.getProfile());
        verify(userRepository, times(1)).save(any());
        verify(profileRepository, times(1)).save(any());
    }

    // ❌ Test Register email déjà utilisé
    @Test
    void register_shouldThrowException_whenEmailExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@darwin.com");

        when(userRepository.existsByEmail("test@darwin.com")).thenReturn(true);

        EmailAlreadyUsedException exception = assertThrows(EmailAlreadyUsedException.class,
                () -> authService.register(request));

        assertEquals("Cet email est deja utilise : test@darwin.com", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldNormalizeEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("  TEST@DARWIN.COM ");
        request.setPassword("password123");
        request.setRole(Role.CLIENT);

        User savedUser = User.builder().id(UUID.randomUUID()).email("test@darwin.com")
                .password("encoded_password").role(Role.CLIENT).build();
        Profile savedProfile = Profile.builder().id(UUID.randomUUID()).userId(savedUser.getId())
                .email(savedUser.getEmail()).role("client").verified(false).banned(false).build();
        when(userRepository.existsByEmail("test@darwin.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded_password");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(profileRepository.save(any())).thenReturn(savedProfile);

        authService.register(request);

        verify(userRepository).existsByEmail("test@darwin.com");
        verify(userRepository).save(argThat(user -> "test@darwin.com".equals(user.getEmail())));
    }

    @Test
    void register_shouldRejectPublicAdminCreation() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("fake-admin@darwin.com");
        request.setPassword("password123");
        request.setRole(Role.ADMIN);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    // ✅ Test Login OK
    @Test
    void login_shouldReturnToken_whenCredentialsValid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@darwin.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("test@darwin.com")).thenReturn(Optional.of(mockUser));
        when(profileRepository.findByUserId(mockUser.getId())).thenReturn(Optional.of(mockProfile));
        when(jwtUtils.generateToken(any())).thenReturn("mock_token");
        when(jwtUtils.generateRefreshToken(any())).thenReturn("mock_refresh");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock_token", response.getToken());
        assertEquals("user", response.getRole());
    }

    // ❌ Test Login utilisateur non trouvé
    @Test
    void login_shouldThrowException_whenUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setEmail("inconnu@darwin.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("inconnu@darwin.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.login(request));
    }

    // ✅ Test validateToken OK
    @Test
    void validateToken_shouldReturnTrue_whenTokenValid() {
        when(jwtUtils.extractUsername("valid_token")).thenReturn("test@darwin.com");
        when(userRepository.findByEmail("test@darwin.com")).thenReturn(Optional.of(mockUser));
        when(jwtUtils.isTokenValid("valid_token", mockUser)).thenReturn(true);

        boolean result = authService.validateToken("valid_token");

        assertTrue(result);
    }

    // ❌ Test validateToken invalide
    @Test
    void validateToken_shouldReturnFalse_whenTokenInvalid() {
        when(jwtUtils.extractUsername("bad_token")).thenReturn("test@darwin.com");
        when(userRepository.findByEmail("test@darwin.com")).thenReturn(Optional.of(mockUser));
        when(jwtUtils.isTokenValid("bad_token", mockUser)).thenReturn(false);

        boolean result = authService.validateToken("bad_token");

        assertFalse(result);
    }
}
