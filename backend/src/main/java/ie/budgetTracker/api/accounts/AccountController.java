package ie.budgetTracker.api.accounts;

import ie.budgetTracker.application.accounts.AccountService;
import ie.budgetTracker.application.accounts.dto.AccountResponse;
import ie.budgetTracker.application.accounts.dto.CreateAccountRequest;
import ie.budgetTracker.application.accounts.dto.CreatePocketRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP and nothing else.
 *
 * No domain type appears in this file. Spec §4's dependency rule is
 * `api -> application -> domain`, and the DTOs live on the application side of
 * that line so this layer never reaches past it - which is also what keeps a
 * domain rename from rippling into a frozen wire contract.
 */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

	private final AccountService accounts;

	AccountController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	List<AccountResponse> list() {
		return accounts.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
		return accounts.create(request);
	}

	/**
	 * BR-13: the pocket is created against its parent, and the PARENT comes back.
	 *
	 * Returning the account rather than the pocket is the response saying what
	 * actually changed - the account's composition, not its balance.
	 */
	@PostMapping("/{accountId}/pockets")
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse addPocket(@PathVariable UUID accountId,
			@Valid @RequestBody CreatePocketRequest request) {
		return accounts.addPocket(accountId, request);
	}
}
