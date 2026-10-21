package com.splitmate.category;

import java.util.List;

import org.bson.types.ObjectId;

import com.splitmate.common.security.CurrentUserId;
import com.splitmate.split.Split;
import com.splitmate.split.SplitAccess;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

	private final CategoryService service;

	private final SplitAccess access;

	public CategoryController(CategoryService service, SplitAccess access) {
		this.service = service;
		this.access = access;
	}

	/** Default categories, plus the split's own when {@code splitId} is given (members only). */
	@GetMapping
	public List<CategoryResponse> list(@RequestParam(required = false) String splitId, @CurrentUserId ObjectId userId) {
		ObjectId id = null;
		if (splitId != null && !splitId.isBlank()) {
			Split split = access.requireMember(splitId, userId);
			id = split.getId();
		}
		return service.availableFor(id).stream().map(CategoryResponse::from).toList();
	}

}
