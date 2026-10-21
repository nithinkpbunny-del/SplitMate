package com.splitmate.split;

import jakarta.validation.constraints.NotNull;

public record InviteSettingsRequest(@NotNull(message = "enabled is required") Boolean enabled) {
}
