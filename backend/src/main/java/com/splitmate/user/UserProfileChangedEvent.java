package com.splitmate.user;

import org.bson.types.ObjectId;

/** Published after a user changes their name or avatar colour. */
public record UserProfileChangedEvent(ObjectId userId, String name, String avatarColor) {
}
