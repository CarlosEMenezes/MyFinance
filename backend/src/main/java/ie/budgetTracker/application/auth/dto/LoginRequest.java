package ie.budgetTracker.application.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** `POST /auth/login`. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
