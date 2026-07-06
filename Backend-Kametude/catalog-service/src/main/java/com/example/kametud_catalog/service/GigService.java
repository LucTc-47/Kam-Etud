package com.example.kametud_catalog.service;

import com.example.kametud_catalog.client.IdentityClient;
import com.example.kametud_catalog.client.StudentStatusResponse;
import com.example.kametud_catalog.client.StudentProfileSummary;
import com.example.kametud_catalog.dto.GigCreateRequest;
import com.example.kametud_catalog.dto.GigResponse;
import com.example.kametud_catalog.entity.Gig;
import com.example.kametud_catalog.exception.GigNotFoundException;
import com.example.kametud_catalog.exception.StudentPublicationForbiddenException;
import com.example.kametud_catalog.exception.CatalogAccessDeniedException;
import com.example.kametud_catalog.repository.GigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GigService {

    private final GigRepository gigRepository;
    private final IdentityClient identityClient;

    @Transactional
    public GigResponse createGig(UUID authenticatedStudentId, GigCreateRequest request) {
        if (request.isPublished()) {
            ensureStudentCanPublish(authenticatedStudentId);
        }
        StudentProfileSummary profile = identityClient.getStudentProfile(authenticatedStudentId);

        Gig gig = Gig.builder()
                // Ancien code : .studentId(request.getStudentId())
                .studentId(authenticatedStudentId)
                .studentName(profile.displayName())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .category(request.getCategory().trim())
                .location(request.getLocation().trim())
                .rating(BigDecimal.ZERO)
                .reviewCount(0)
                .orderCount(0)
                .badge("Nouveau")
                .images(request.getImages() == null ? List.of() : request.getImages())
                .active(true)
                .gpsLat(request.getGpsLat())
                .gpsLng(request.getGpsLng())
                .tierBasique(request.getTierBasique())
                .tierStandard(request.getTierStandard())
                .tierPremium(request.getTierPremium())
                .published(request.isPublished())
                .build();

        return GigResponse.fromEntity(gigRepository.save(gig));
    }

    @Transactional(readOnly = true)
    public List<GigResponse> searchGigs(String query, String category, String location) {
        return gigRepository.searchPublished(normalize(query), normalize(category), normalize(location))
                .stream()
                .map(GigResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public GigResponse getGig(UUID gigId) {
        Gig gig = gigRepository.findByIdAndPublishedTrueAndActiveTrue(gigId)
                .orElseThrow(() -> new GigNotFoundException(gigId));
        return GigResponse.fromEntity(gig);
    }

    @Transactional(readOnly = true)
    public List<GigResponse> getMyGigs(UUID studentId) {
        return gigRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(GigResponse::fromEntity)
                .toList();
    }

    @Transactional
    public GigResponse updateGig(UUID gigId, UUID studentId, GigCreateRequest request) {
        Gig gig = getGigOrThrow(gigId);
        ensureOwner(gig, studentId);
        if (request.isPublished() && !gig.isPublished()) {
            ensureStudentCanPublish(studentId);
        }

        gig.setTitle(request.getTitle().trim());
        gig.setDescription(request.getDescription());
        gig.setCategory(request.getCategory().trim());
        gig.setLocation(request.getLocation().trim());
        gig.setTierBasique(request.getTierBasique());
        gig.setTierStandard(request.getTierStandard());
        gig.setTierPremium(request.getTierPremium());
        gig.setImages(request.getImages() == null ? List.of() : request.getImages());
        gig.setGpsLat(request.getGpsLat());
        gig.setGpsLng(request.getGpsLng());
        gig.setPublished(request.isPublished());

        return GigResponse.fromEntity(gigRepository.save(gig));
    }

    @Transactional
    public GigResponse publishGig(UUID gigId, UUID studentId, boolean published) {
        Gig gig = getGigOrThrow(gigId);
        ensureOwner(gig, studentId);
        if (published) ensureStudentCanPublish(studentId);

        gig.setPublished(published);

        return GigResponse.fromEntity(gigRepository.save(gig));
    }

    @Transactional
    public GigResponse setActive(UUID gigId, UUID studentId, boolean active) {
        Gig gig = getGigOrThrow(gigId);
        ensureOwner(gig, studentId);
        gig.setActive(active);
        return GigResponse.fromEntity(gigRepository.save(gig));
    }

    @Transactional
    public void deleteGig(UUID gigId, UUID studentId) {
        Gig gig = getGigOrThrow(gigId);
        ensureOwner(gig, studentId);
        gigRepository.delete(gig);
    }

    @Transactional
    public int deactivateAllForStudent(UUID studentId) {
        return gigRepository.deactivateAllByStudentId(studentId);
    }

    @Transactional
    public GigResponse updateRating(UUID gigId, BigDecimal rating, int reviewCount) {
        Gig gig = getGigOrThrow(gigId);
        gig.setRating(rating.setScale(2, RoundingMode.HALF_UP));
        gig.setReviewCount(reviewCount);
        return GigResponse.fromEntity(gigRepository.save(gig));
    }

    private Gig getGigOrThrow(UUID gigId) {
        return gigRepository.findById(gigId)
                .orElseThrow(() -> new GigNotFoundException(gigId));
    }

    private void ensureStudentCanPublish(UUID studentId) {
        StudentStatusResponse status = identityClient.getStudentStatus(studentId);
        if (!status.canPublish()) {
            throw new StudentPublicationForbiddenException(studentId);
        }
    }

    private void ensureOwner(Gig gig, UUID studentId) {
        if (!gig.getStudentId().equals(studentId)) {
            throw new CatalogAccessDeniedException("Ce gig appartient a un autre etudiant");
        }
    }

    private String normalize(String value) {
        // Les chaines vides gardent un type SQL texte avec Hibernate 7.
        // Ancien retour pour un filtre absent : null.
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
