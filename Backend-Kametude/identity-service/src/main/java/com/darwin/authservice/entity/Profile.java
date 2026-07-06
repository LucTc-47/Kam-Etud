package com.darwin.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String email;
    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String city;

    private String university;
    private String faculty;
    private String level;

    @ElementCollection
    @CollectionTable(name = "profile_skills",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "skill")
    private List<String> skills;

    private Float rating;
    private String role;

    @Column(name = "verified")
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "banned")
    @Builder.Default
    private Boolean banned = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
