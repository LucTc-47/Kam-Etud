package com.darwin.authservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProfileUpdateRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String avatarUrl;
    private Boolean removeAvatar;
    private String bio;
    private String city;
    private String university;
    private String faculty;
    private String level;
    private List<String> skills;
}
