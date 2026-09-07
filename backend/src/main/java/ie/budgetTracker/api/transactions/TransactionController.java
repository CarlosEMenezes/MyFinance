package ie.budgetTracker.api.transactions;

import ie.budgetTracker.application.transactions.TransactionService;
import ie.budgetTracker.application.transactions.dto.CreateTransactionRequest;
import ie.budgetTracker.application.transactions.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP and nothing else.
 *
 * Neither BR-8 nor BR-4 appears here: the rate, the converted amount and the
 * bill date arrive on the response already worked out, because a controller
 * that converted a currency would be a second place for the rule to live.
 */
@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

	private final TransactionService transactions;

	TransactionController(TransactionService transactions) {
		this.transactions = transactions;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TransactionResponse log(@Valid @RequestBody CreateTransactionRequest request) {
		return transactions.log(request);
	}
}
