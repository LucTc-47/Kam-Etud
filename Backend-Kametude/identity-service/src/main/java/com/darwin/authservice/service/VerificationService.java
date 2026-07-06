package com.darwin.authservice.service;

import com.darwin.authservice.dto.CreateVerificationRequest;
import com.darwin.authservice.dto.StudentStatusResponse;
import com.darwin.authservice.dto.UpdateVerificationRequest;
import com.darwin.authservice.dto.VerificationResponse;
import com.darwin.authservice.entity.Profile;
import com.darwin.authservice.entity.Role;
import com.darwin.authservice.entity.StudentVerification;
import com.darwin.authservice.entity.User;
import com.darwin.authservice.entity.VerificationStatus;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.StudentVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final StudentVerificationRepository verificationRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public VerificationResponse create(User user, CreateVerificationRequest request) {
        if (user.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Seul un compte etudiant peut demander une verification");
        }

        Profile profile = findProfile(user.getId());
        StudentVerification verification = StudentVerification.builder()
                .studentId(user.getId())
                .studentName(((profile.getFirstName() == null ? "" : profile.getFirstName()) + " "
                        + (profile.getLastName() == null ? "" : profile.getLastName())).trim())
                .email(user.getEmail())
                .university(request.getUniversity())
                .idType(request.getIdType())
                .idFileUrl(request.getIdFileUrl())
                .selfieUrl(request.getSelfieUrl())
                .studentCardUrl(request.getStudentCardUrl())
                .status(VerificationStatus.PENDING)
                .build();

        return toResponse(verificationRepository.save(verification));
    }

    @Transactional(readOnly = true)
    public List<VerificationResponse> getMine(User user) {
        return verificationRepository.findByStudentIdOrderBySubmittedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VerificationResponse> getAll() {
        return verificationRepository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VerificationResponse review(UUID id, UpdateVerificationRequest request) {
        VerificationStatus status;
        try {
            status = VerificationStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Statut invalide : pending, approved ou rejected attendu");
        }
        if (status == VerificationStatus.PENDING) {
            throw new IllegalArgumentException("Une revue doit etre approved ou rejected");
        }

        StudentVerification verification = verificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande de verification introuvable"));
        verification.setStatus(status);
        verification.setReviewedAt(Instant.now());

        Profile profile = findProfile(verification.getStudentId());
        profile.setVerified(status == VerificationStatus.APPROVED);
        profileRepository.save(profile);

        return toResponse(verificationRepository.save(verification));
    }

    @Transactional(readOnly = true)
    public StudentStatusResponse getStudentStatus(UUID studentId) {
        Profile profile = findProfile(studentId);
        return new StudentStatusResponse(
                Boolean.TRUE.equals(profile.getVerified()),
                Boolean.TRUE.equals(profile.getBanned())
        );
    }

    private Profile findProfile(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profil non trouve"));
    }

    private VerificationResponse toResponse(StudentVerification verification) {
        return VerificationResponse.builder()
                .id(verification.getId())
                .studentId(verification.getStudentId())
                .studentName(verification.getStudentName())
                .email(verification.getEmail())
                .university(verification.getUniversity())
                .idType(verification.getIdType())
                .idFileUrl(verification.getIdFileUrl())
                .selfieUrl(verification.getSelfieUrl())
                .studentCardUrl(verification.getStudentCardUrl())
                .status(verification.getStatus().toLower())
                .submittedAt(verification.getSubmittedAt())
                .reviewedAt(verification.getReviewedAt())
                .build();
    }
}
