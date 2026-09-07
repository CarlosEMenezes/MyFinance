package ie.budgetTracker.api.cards;

import ie.budgetTracker.application.cards.CardService;
import ie.budgetTracker.application.cards.dto.CardResponse;
import ie.budgetTracker.application.cards.dto.CreateCardRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP and nothing else.
 *
 * No domain type appears in this file, and neither does BR-4: the three cycle
 * dates arrive on the DTO already computed, because the controller has no
 * business knowing when a statement closes.
 */
@RestController
@RequestMapping("/api/v1/cards")
class CardController {

	private final CardService cards;

	CardController(CardService cards) {
		this.cards = cards;
	}

	@GetMapping
	List<CardResponse> list() {
		return cards.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CardResponse create(@Valid @RequestBody CreateCardRequest request) {
		return cards.create(request);
	}
}
