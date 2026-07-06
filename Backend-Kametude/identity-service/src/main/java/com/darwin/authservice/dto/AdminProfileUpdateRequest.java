package com.darwin.authservice.dto;

import lombok.Data;

@Data
public class AdminProfileUpdateRequest {
    private Boolean verified;
    private Boolean banned;
}
