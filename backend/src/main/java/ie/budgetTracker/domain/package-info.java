/**
 * Entities, value objects and domain services.
 *
 * <p>Depends on nothing: no Spring, no JPA, no other layer of this application.
 * Everything here is a plain class or {@code record} with pure methods, which is
 * why it carries the highest test density in the project (spec §4).
 *
 * <p>The domain services that will live here: {@code MoneyCalculator},
 * {@code StatementCycleCalculator}, {@code InstalmentCalculator},
 * {@code LoanCalculator}, {@code PositionCalculator}, {@code PlanNormaliser},
 * {@code GoalCalculator} and {@code DuePaymentQueue}.
 */
package ie.budgetTracker.domain;
