package ie.budgetTracker.api.identity;

import ie.budgetTracker.application.identity.UserService;
import ie.budgetTracker.application.identity.dto.UpdateUserRequest;
import ie.budgetTracker.application.identity.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

	private final UserService users;

	UserController(UserService users) {
		this.users = users;
	}

	@GetMapping("/me")
	UserResponse me() {
		return users.profile();
	}

	@PatchMapping("/me")
	UserResponse update(@Valid @RequestBody UpdateUserRequest request) {
		return users.update(request);
	}
}
