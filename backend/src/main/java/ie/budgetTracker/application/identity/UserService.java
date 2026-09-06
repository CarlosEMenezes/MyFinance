package ie.budgetTracker.application.identity;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.dto.UpdateUserRequest;
import ie.budgetTracker.application.identity.dto.UserResponse;
import ie.budgetTracker.domain.identity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading and editing the profile behind every figure. */
@Service
public class UserService {

	private final UserRepository users;
	private final CurrentUser currentUser;

	public UserService(UserRepository users, CurrentUser currentUser) {
		this.users = users;
		this.currentUser = currentUser;
	}

	@Transactional(readOnly = true)
	public UserResponse profile() {
		return UserResponse.from(currentProfile());
	}

	@Transactional
	public UserResponse update(UpdateUserRequest request) {
		return UserResponse.from(update(request.toChanges()));
	}

	User currentProfile() {
		return users.findById(currentUser.id())
				.orElseThrow(() -> AppException.notFound("No profile for the current user"));
	}

	/**
	 * Applies whatever the request actually set.
	 *
	 * Settings saves one field at a time, so a null here means "not mentioned"
	 * rather than "clear it". The one exception is the three optional profile
	 * fields, where clearing is a real thing to want - hence the explicit
	 * `Change` wrapper rather than a bare null.
	 */
	User update(UserChanges changes) {
		return users.save(changes.applyTo(currentProfile()));
	}
}
