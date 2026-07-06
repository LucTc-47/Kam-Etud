package com.darwin.authservice.dto;

import com.darwin.authservice.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email obligatoire")
    @Email(message = "Format email invalide")
    private String email;

    @NotBlank(message = "Mot de passe obligatoire")
    @Size(min = 6, message = "Minimum 6 caracteres")
    private String password;

    private Role role = Role.USER;

    private String firstName;
    private String lastName;
    private String phone;
    private String city;
    private String university;
    private String faculty;
    private String level;
    private String bio;
    private List<String> skills;
}
