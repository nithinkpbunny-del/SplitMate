package com.splitmate.category;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the default categories at startup if they are missing. Idempotent: matched by key, so running
 * it any number of times leaves exactly one of each.
 */
@Component
@Order(0)
public class CategorySeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

	static final List<Category> DEFAULTS = List.of(
			new Category("rent", "Rent", "home", "#F59E0B", 1),
			new Category("groceries", "Groceries", "shopping-cart", "#22C55E", 2),
			new Category("utilities", "Utilities", "zap", "#38BDF8", 3),
			new Category("dining", "Dining Out", "utensils", "#FB7185", 4),
			new Category("transport", "Transport", "train-front", "#818CF8", 5),
			new Category("household", "Household", "spray-can", "#2DD4BF", 6),
			new Category("entertainment", "Entertainment", "clapperboard", "#E879F9", 7),
			new Category("travel", "Travel", "plane", "#FB923C", 8),
			new Category("shopping", "Shopping", "shopping-bag", "#F472B6", 9),
			new Category("health", "Health", "heart-pulse", "#F87171", 10),
			new Category("subscriptions", "Subscriptions", "repeat", "#A78BFA", 11),
			new Category("other", "Other", "circle-ellipsis", "#94A3B8", 12));

	private final CategoryRepository categories;

	public CategorySeeder(CategoryRepository categories) {
		this.categories = categories;
	}

	@Override
	public void run(ApplicationArguments args) {
		int created = 0;
		for (Category template : DEFAULTS) {
			if (categories.findBySplitIdIsNullAndKey(template.getKey()).isEmpty()) {
				categories.save(new Category(template.getKey(), template.getName(), template.getIcon(),
						template.getColor(), template.getSortOrder()));
				created++;
			}
		}
		if (created > 0) {
			log.info("Seeded {} default categories", created);
		}
	}

}
