package com.darwin.authservice.service;

import com.darwin.authservice.dto.AuthResponse;
import com.darwin.authservice.dto.LoginRequest;
import com.darwin.authservice.dto.RegisterRequest;
import com.darwin.authservice.entity.Profile;
import com.darwin.authservice.entity.Role;
import com.darwin.authservice.entity.User;
import com.darwin.authservice.exception.EmailAlreadyUsedException;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.UserRepository;
import com.darwin.authservice.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final ProfileMapper profileMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(normalizedEmail)) {
            // Ancien comportement : RuntimeException transformee en erreur HTTP 500 generique.
            throw new EmailAlreadyUsedException(normalizedEmail);
        }

        Role requestedRole = request.getRole() == null ? Role.CLIENT : request.getRole();
        if (requestedRole == Role.ADMIN || requestedRole == Role.MODERATOR) {
            throw new IllegalArgumentException("Les roles admin et moderator ne peuvent pas etre crees publiquement");
        }

        // 1. Créer le user
        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(requestedRole)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        // 2. Créer le profil automatiquement
        Profile profile = Profile.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole().toLower())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .city(request.getCity())
                .university(request.getUniversity())
                .faculty(request.getFaculty())
                .level(request.getLevel())
                .bio(request.getBio())
                .skills(request.getSkills())
                .verified(false)
                .banned(false)
                .createdAt(Instant.now())
                .build();

        Profile savedProfile = profileRepository.save(profile);

        return AuthResponse.builder()
                .token(jwtUtils.generateToken(savedUser))
                .refreshToken(jwtUtils.generateRefreshToken(savedUser))
                .email(savedUser.getEmail())
                .role(savedUser.getRole().toLower())
                .profile(profileMapper.toResponse(savedProfile))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouve"));

        Profile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Profil non trouve"));

        return AuthResponse.builder()
                .token(jwtUtils.generateToken(user))
                .refreshToken(jwtUtils.generateRefreshToken(user))
                .email(user.getEmail())
                .role(user.getRole().toLower())
                .profile(profileMapper.toResponse(profile))
                .build();
    }

    public AuthResponse refreshToken(String refreshToken) {
        String email = jwtUtils.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouve"));

        if (!jwtUtils.isTokenValid(refreshToken, user) || !jwtUtils.isRefreshToken(refreshToken)) {
            throw new RuntimeException("Refresh token invalide");
        }

        Profile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Profil non trouve"));

        return AuthResponse.builder()
                .token(jwtUtils.generateToken(user))
                .refreshToken(jwtUtils.generateRefreshToken(user))
                .email(user.getEmail())
                .role(user.getRole().toLower())
                .profile(profileMapper.toResponse(profile))
                .build();
    }

    public boolean validateToken(String token) {
        String email = jwtUtils.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouve"));
        return jwtUtils.isTokenValid(token, user);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /* Ancienne conversion locale du profil :
       elle est remplacee par ProfileMapper afin que l'authentification,
       les profils et la verification KYC renvoient le meme contrat. */
}
