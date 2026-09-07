package ie.budgetTracker.application.notifications.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * `PATCH /notifications/read` — BR-12 read state, one item or many.
 *
 * Marking one row read and marking the whole queue read are the same write, so
 * they are one endpoint: two would need two optimistic updates that had to
 * agree with each other.
 */
public record MarkNotificationsReadRequest(@NotEmpty List<String> keys, boolean read) {
}
