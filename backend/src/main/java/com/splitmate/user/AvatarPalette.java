package com.splitmate.user;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fixed set of avatar colours. Each user gets one at registration and can change it in their profile.
 */
public final class AvatarPalette {

	public static final List<String> COLORS = List.of(
			"#F59E0B", // amber
			"#34D399", // emerald
			"#60A5FA", // blue
			"#F472B6", // pink
			"#A78BFA", // violet
			"#FB923C", // orange
			"#2DD4BF", // teal
			"#FACC15", // yellow
			"#F87171", // red
			"#4ADE80", // green
			"#38BDF8", // sky
			"#E879F9"); // fuchsia

	private AvatarPalette() {
	}

	public static String random() {
		return COLORS.get(ThreadLocalRandom.current().nextInt(COLORS.size()));
	}

	public static boolean isValid(String color) {
		return color != null && COLORS.contains(color.toUpperCase());
	}

}
