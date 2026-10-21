package com.splitmate.common;

import org.bson.types.ObjectId;

/**
 * Converts ids coming from URLs and request bodies into {@link ObjectId}s.
 */
public final class Ids {

	private Ids() {
	}

	/** Parses an id from a path variable; malformed ids are reported as "not found" so nothing leaks. */
	public static ObjectId parse(String value, String what) {
		if (value == null || !ObjectId.isValid(value)) {
			throw ApiException.notFound(what + " not found");
		}
		return new ObjectId(value);
	}

	/** Parses an id from a request body; malformed ids are a client error. */
	public static ObjectId parseField(String value, String field) {
		if (value == null || !ObjectId.isValid(value)) {
			throw ApiException.badRequest("'" + field + "' is not a valid id");
		}
		return new ObjectId(value);
	}

	public static String str(ObjectId id) {
		return id == null ? null : id.toHexString();
	}

}
