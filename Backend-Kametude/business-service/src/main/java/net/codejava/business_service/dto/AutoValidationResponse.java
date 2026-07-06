package net.codejava.business_service.dto;

public record AutoValidationResponse(int eligible, int validated, int released, int pending) {
}
