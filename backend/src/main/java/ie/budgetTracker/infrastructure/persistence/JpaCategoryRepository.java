package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.plan.CategoryRepository;
import ie.budgetTracker.domain.plan.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link CategoryRepository}. */
@Repository
class JpaCategoryRepository implements CategoryRepository {

	private final CategoryJpaRepository categories;
	private final UserJpaRepository users;

	JpaCategoryRepository(CategoryJpaRepository categories, UserJpaRepository users) {
		this.categories = categories;
		this.users = users;
	}

	@Override
	public List<Category> findAllForUser(UUID userId) {
		return categories.findByUserIdOrderByNameAsc(userId).stream()
				.map(CategoryEntity::toDomain)
				.toList();
	}

	@Override
	public Optional<Category> findForUser(UUID userId, UUID categoryId) {
		return categories.findByUserIdAndId(userId, categoryId).map(CategoryEntity::toDomain);
	}

	@Override
	public Category create(UUID userId, Category category) {
		UserEntity owner = users.findById(userId)
				.orElseThrow(() -> AppException.notFound("No user with id " + userId));

		return categories.save(new CategoryEntity(owner, category)).toDomain();
	}

	@Override
	public Category update(UUID userId, Category category) {
		// Matched on owner AND id, so another user's category is not found here
		// rather than found and then refused (ADR-11).
		CategoryEntity stored = categories.findByUserIdAndId(userId, category.id())
				.orElseThrow(() -> AppException.notFound("No category with id " + category.id()));

		stored.apply(category);
		return categories.save(stored).toDomain();
	}
}
