package com.splitmate.category;

public record CategoryResponse(String id, String key, String name, String icon, String color, String splitId,
		boolean isDefault, int sortOrder) {

	public static CategoryResponse from(Category c) {
		return new CategoryResponse(c.getId().toHexString(), c.getKey(), c.getName(), c.getIcon(), c.getColor(),
				c.getSplitId() == null ? null : c.getSplitId().toHexString(), c.isDefaultCategory(), c.getSortOrder());
	}

}
