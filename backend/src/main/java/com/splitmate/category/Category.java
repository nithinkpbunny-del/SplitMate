package com.splitmate.category;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Expense category. {@code splitId = null} marks a global default seeded at startup.
 * Collection {@code categories}.
 */
@Document("categories")
@CompoundIndex(name = "split_key", def = "{'splitId': 1, 'key': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
public class Category {

	@Id
	private ObjectId id;

	private String key;

	private String name;

	/** lucide icon name, e.g. "shopping-cart". */
	private String icon;

	private String color;

	private ObjectId splitId;

	@Field("isDefault")
	private boolean defaultCategory;

	private int sortOrder;

	public Category(String key, String name, String icon, String color, int sortOrder) {
		this.key = key;
		this.name = name;
		this.icon = icon;
		this.color = color;
		this.defaultCategory = true;
		this.sortOrder = sortOrder;
	}

}
