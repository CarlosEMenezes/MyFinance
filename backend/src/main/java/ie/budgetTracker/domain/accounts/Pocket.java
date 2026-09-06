package ie.budgetTracker.domain.accounts;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A named sub-balance inside an account (BR-13).
 *
 * Its balance is ALREADY part of the parent account's. Nothing may add the two
 * together, which is why a pocket is only ever reachable through the account
 * that holds it and never appears in a list of its own.
 */
public record Pocket(UUID id, String name, BigDecimal balance) {
}
