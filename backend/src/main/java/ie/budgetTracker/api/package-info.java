/**
 * REST controllers, request/response records and exception handling.
 *
 * <p>Nothing depends on this layer. No entity is ever exposed directly — always
 * a DTO record. Errors follow RFC 7807 {@code application/problem+json}: 400
 * with a field-level list for validation, 404 for unknown ids, 409 for domain
 * rule violations (spec §4).
 */
package ie.budgetTracker.api;
