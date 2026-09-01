/**
 * JPA adapters, the FX client, schedulers and security.
 *
 * <p>Implements the ports declared in {@code application}. JPA entities live
 * here, never in {@code domain} — that separation is enforced by
 * {@code HexagonalArchitectureTest} (spec §4).
 */
package ie.budgetTracker.infrastructure;
