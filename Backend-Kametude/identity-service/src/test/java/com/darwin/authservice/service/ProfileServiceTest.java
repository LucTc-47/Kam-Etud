package com.darwin.authservice.service;

import com.darwin.authservice.dto.AdminProfileUpdateRequest;
import com.darwin.authservice.entity.Profile;
import com.darwin.authservice.entity.Role;
import com.darwin.authservice.entity.User;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock ProfileRepository profileRepository;
    @Mock UserRepository userRepository;
    @Mock PublicationRightsService publicationRights;
    @Spy ProfileMapper profileMapper = new ProfileMapper();
    @InjectMocks ProfileService profileService;

    @Test
    void banningStudentDisablesAccountAndAllGigs() {
        UUID studentId = UUID.randomUUID();
        User user = User.builder().id(studentId).email("student@kametud.com")
                .password("hash").role(Role.STUDENT).enabled(true).build();
        Profile profile = Profile.builder().id(UUID.randomUUID()).userId(studentId)
                .role("student").verified(true).banned(false).build();
        AdminProfileUpdateRequest request = new AdminProfileUpdateRequest();
        request.setBanned(true);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(studentId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);
        when(publicationRights.revokeFor(studentId)).thenReturn(2);

        var response = profileService.updateByAdmin(studentId, request);

        assertThat(response.getBanned()).isTrue();
        assertThat(user.isEnabled()).isFalse();
        verify(publicationRights).revokeFor(studentId);
        verify(userRepository).save(user);
    }

    @Test
    void removingVerificationAlsoRemovesGigsFromCatalogue() {
        // Sans ce retrait, l'etudiant perdait le droit de publier mais restait
        // affiche au catalogue avec ses prestations en ligne.
        UUID studentId = UUID.randomUUID();
        User user = User.builder().id(studentId).email("student@kametud.com")
                .password("hash").role(Role.STUDENT).enabled(true).build();
        Profile profile = Profile.builder().id(UUID.randomUUID()).userId(studentId)
                .role("student").verified(true).banned(false).build();
        AdminProfileUpdateRequest request = new AdminProfileUpdateRequest();
        request.setVerified(false);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(studentId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);
        when(publicationRights.revokeFor(studentId)).thenReturn(1);

        var response = profileService.updateByAdmin(studentId, request);

        assertThat(response.getVerified()).isFalse();
        verify(publicationRights).revokeFor(studentId);
    }

    @Test
    void grantingVerificationDoesNotTouchTheCatalogue() {
        UUID studentId = UUID.randomUUID();
        User user = User.builder().id(studentId).email("student@kametud.com")
                .password("hash").role(Role.STUDENT).enabled(true).build();
        Profile profile = Profile.builder().id(UUID.randomUUID()).userId(studentId)
                .role("student").verified(false).banned(false).build();
        AdminProfileUpdateRequest request = new AdminProfileUpdateRequest();
        request.setVerified(true);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(studentId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);

        profileService.updateByAdmin(studentId, request);

        verifyNoInteractions(publicationRights);
    }

    @Test
    void reactivationDoesNotRepublishGigsAutomatically() {
        UUID studentId = UUID.randomUUID();
        User user = User.builder().id(studentId).email("student@kametud.com")
                .password("hash").role(Role.STUDENT).enabled(false).build();
        Profile profile = Profile.builder().id(UUID.randomUUID()).userId(studentId)
                .role("student").verified(true).banned(true).build();
        AdminProfileUpdateRequest request = new AdminProfileUpdateRequest();
        request.setBanned(false);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(studentId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);

        profileService.updateByAdmin(studentId, request);

        assertThat(user.isEnabled()).isTrue();
        verifyNoInteractions(publicationRights);
    }
}
