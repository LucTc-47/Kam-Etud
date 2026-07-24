package com.darwin.authservice.service;

import com.darwin.authservice.dto.UpdateVerificationRequest;
import com.darwin.authservice.entity.Profile;
import com.darwin.authservice.entity.StudentVerification;
import com.darwin.authservice.entity.VerificationStatus;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.StudentVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private StudentVerificationRepository verificationRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PublicationRightsService publicationRights;

    @InjectMocks
    private VerificationService verificationService;

    @Test
    void approvingVerificationAlsoMarksStudentProfileAsVerified() {
        UUID verificationId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentVerification verification = StudentVerification.builder()
                .id(verificationId)
                .studentId(studentId)
                .studentName("Ada Etudiante")
                .email("ada@example.com")
                .idType("cni")
                .idFileUrl("http://localhost:8080/api/storage/files/id.jpg")
                .selfieUrl("http://localhost:8080/api/storage/files/selfie.jpg")
                .status(VerificationStatus.PENDING)
                .build();
        Profile profile = Profile.builder()
                .userId(studentId)
                .verified(false)
                .banned(false)
                .build();
        UpdateVerificationRequest request = new UpdateVerificationRequest();
        request.setStatus("approved");

        when(verificationRepository.findById(verificationId)).thenReturn(Optional.of(verification));
        when(profileRepository.findByUserId(studentId)).thenReturn(Optional.of(profile));
        when(verificationRepository.save(verification)).thenReturn(verification);

        var response = verificationService.review(verificationId, request);

        assertThat(response.getStatus()).isEqualTo("approved");
        assertThat(profile.getVerified()).isTrue();
        verify(profileRepository).save(profile);
        verify(verificationRepository).save(verification);
        verifyNoInteractions(publicationRights);
    }

    @Test
    void rejectingVerificationRemovesGigsAlreadyOnline() {
        // Le rejet retire le droit de publier : les prestations deja en ligne
        // doivent quitter le catalogue, comme lors d'un bannissement.
        UUID verificationId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentVerification verification = pendingVerification(verificationId, studentId);
        Profile profile = Profile.builder().userId(studentId).verified(true).banned(false).build();
        UpdateVerificationRequest request = new UpdateVerificationRequest();
        request.setStatus("rejected");

        when(verificationRepository.findById(verificationId)).thenReturn(Optional.of(verification));
        when(profileRepository.findByUserId(studentId)).thenReturn(Optional.of(profile));
        when(verificationRepository.save(verification)).thenReturn(verification);
        when(publicationRights.revokeFor(studentId)).thenReturn(2);

        var response = verificationService.review(verificationId, request);

        assertThat(response.getStatus()).isEqualTo("rejected");
        assertThat(profile.getVerified()).isFalse();
        verify(publicationRights).revokeFor(studentId);
    }

    @Test
    void rejectionIsNotRecordedWhenCatalogueIsUnreachable() {
        // Fail-closed : mieux vaut un rejet en echec qu'un rejet annonce dont les
        // prestations seraient restees visibles.
        UUID verificationId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentVerification verification = pendingVerification(verificationId, studentId);
        UpdateVerificationRequest request = new UpdateVerificationRequest();
        request.setStatus("rejected");

        when(verificationRepository.findById(verificationId)).thenReturn(Optional.of(verification));
        when(publicationRights.revokeFor(studentId))
                .thenThrow(new IllegalStateException("Catalog Service indisponible"));

        assertThatThrownBy(() -> verificationService.review(verificationId, request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(verification.getStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(verificationRepository, never()).save(any(StudentVerification.class));
        verify(profileRepository, never()).save(any(Profile.class));
    }

    private StudentVerification pendingVerification(UUID verificationId, UUID studentId) {
        return StudentVerification.builder()
                .id(verificationId)
                .studentId(studentId)
                .studentName("Ada Etudiante")
                .email("ada@example.com")
                .idType("cni")
                .idFileUrl("http://localhost:8080/api/storage/files/id.jpg")
                .selfieUrl("http://localhost:8080/api/storage/files/selfie.jpg")
                .status(VerificationStatus.PENDING)
                .build();
    }
}
