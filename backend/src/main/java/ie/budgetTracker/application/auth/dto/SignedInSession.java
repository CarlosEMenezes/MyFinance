package ie.budgetTracker.application.auth.dto;

import ie.budgetTracker.application.identity.dto.UserResponse;
import java.time.Instant;

/**
 * What a successful sign-in produces.
 *
 * The token is here and NOT in `UserResponse`, because this record never
 * reaches a response body: the controller takes the token out to build a
 * cookie and returns only the profile. Keeping them in one type would make it
 * easy to serialise the token by accident, which is exactly what ADR-11's
 * HttpOnly cookie exists to prevent.
 */
public record SignedInSession(String token, Instant expiresAt, UserResponse user) {
}
