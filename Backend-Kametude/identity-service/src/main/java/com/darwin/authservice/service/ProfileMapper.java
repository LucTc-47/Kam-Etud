package com.darwin.authservice.service;

import com.darwin.authservice.dto.ProfileResponse;
import com.darwin.authservice.entity.Profile;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {

    public ProfileResponse toResponse(Profile profile) {
        return ProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .avatarUrl(profile.getAvatarUrl())
                .bio(profile.getBio())
                .city(profile.getCity())
                .university(profile.getUniversity())
                .faculty(profile.getFaculty())
                .level(profile.getLevel())
                .skills(profile.getSkills())
                .rating(profile.getRating())
                .role(profile.getRole())
                .verified(Boolean.TRUE.equals(profile.getVerified()))
                .banned(Boolean.TRUE.equals(profile.getBanned()))
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
