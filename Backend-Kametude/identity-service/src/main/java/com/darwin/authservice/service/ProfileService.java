package com.darwin.authservice.service;

import com.darwin.authservice.dto.AdminProfileUpdateRequest;
import com.darwin.authservice.dto.ProfileResponse;
import com.darwin.authservice.dto.ProfileUpdateRequest;
import com.darwin.authservice.entity.Profile;
import com.darwin.authservice.entity.Role;
import com.darwin.authservice.entity.User;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ProfileMapper profileMapper;
    private final PublicationRightsService publicationRights;

    @Transactional(readOnly = true)
    public ProfileResponse getByUserId(UUID userId) {
        ProfileResponse response = profileMapper.toResponse(findByUserId(userId));
        response.setEmail(null);
        response.setPhone(null);
        response.setBanned(null);
        return response;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getCurrent(User user) {
        return profileMapper.toResponse(findByUserId(user.getId()));
    }

    @Transactional
    public ProfileResponse updateCurrent(User user, ProfileUpdateRequest request) {
        Profile profile = findByUserId(user.getId());
        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getPhone() != null) profile.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) profile.setAvatarUrl(request.getAvatarUrl());
        if (Boolean.TRUE.equals(request.getRemoveAvatar())) profile.setAvatarUrl(null);
        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getUniversity() != null) profile.setUniversity(request.getUniversity());
        if (request.getFaculty() != null) profile.setFaculty(request.getFaculty());
        if (request.getLevel() != null) profile.setLevel(request.getLevel());
        if (request.getSkills() != null) profile.setSkills(request.getSkills());
        return profileMapper.toResponse(profileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> getAll() {
        return profileRepository.findAll().stream()
                .map(profileMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProfileResponse updateByAdmin(UUID userId, AdminProfileUpdateRequest request) {
        Profile profile = findByUserId(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve"));
        if (request.getVerified() != null) {
            // Retirer la verification retire le droit de publier : les prestations
            // deja en ligne doivent suivre, sinon l'etudiant reste au catalogue.
            if (!request.getVerified() && user.getRole() == Role.STUDENT) {
                publicationRights.revokeFor(userId);
            }
            profile.setVerified(request.getVerified());
        }
        if (request.getBanned() != null) {
            boolean banned = request.getBanned();
            if (banned && user.getRole() == Role.STUDENT) {
                // La desactivation distante est faite avant le verrouillage du compte :
                // en cas de panne Catalog, aucune suspension partielle n'est annoncee.
                publicationRights.revokeFor(userId);
            }
            profile.setBanned(request.getBanned());
            user.setEnabled(!banned);
            userRepository.save(user);
        }
        return profileMapper.toResponse(profileRepository.save(profile));
    }

    private Profile findByUserId(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profil non trouve"));
    }
}
