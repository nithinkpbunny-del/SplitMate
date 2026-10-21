package com.splitmate.category;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.splitmate.common.ApiException;

import org.springframework.stereotype.Service;

@Service
public class CategoryService {

	private final CategoryRepository categories;

	public CategoryService(CategoryRepository categories) {
		this.categories = categories;
	}

	public List<Category> availableFor(ObjectId splitId) {
		return splitId == null ? categories.findDefaults() : categories.findAvailableFor(splitId);
	}

	public Map<ObjectId, Category> byIdFor(ObjectId splitId) {
		return availableFor(splitId).stream().collect(Collectors.toMap(Category::getId, Function.identity()));
	}

	/** The category must be a global default or belong to this split. */
	public Category requireUsable(ObjectId categoryId, ObjectId splitId) {
		return categories.findById(categoryId)
			.filter(c -> c.getSplitId() == null || c.getSplitId().equals(splitId))
			.orElseThrow(() -> ApiException.badRequest("Pick a category"));
	}

}
