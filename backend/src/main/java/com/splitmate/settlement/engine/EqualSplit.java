package com.splitmate.settlement.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Splits an amount equally, exactly to the cent. Pure Java, no framework dependencies.
 *
 * <pre>
 * base      = amount / n
 * remainder = amount % n
 * participants sorted by id; the first `remainder` get base + 1, the rest get base
 * </pre>
 *
 * Example: 1000 among 3 → 334, 333, 333.
 */
public final class EqualSplit {

	private EqualSplit() {
	}

	/**
	 * @param amountMinor positive amount in minor units
	 * @param participantIds at least one id; duplicates are ignored
	 * @return shares keyed by participant id, in id order, summing exactly to {@code amountMinor}
	 */
	public static Map<String, Long> shares(long amountMinor, Collection<String> participantIds) {
		if (amountMinor <= 0) {
			throw new IllegalArgumentException("amountMinor must be positive");
		}
		List<String> sorted = List.copyOf(new TreeSet<>(participantIds));
		if (sorted.isEmpty()) {
			throw new IllegalArgumentException("at least one participant is required");
		}
		int n = sorted.size();
		long base = amountMinor / n;
		long remainder = amountMinor % n;

		Map<String, Long> shares = new LinkedHashMap<>();
		long sum = 0;
		for (int i = 0; i < n; i++) {
			long share = base + (i < remainder ? 1 : 0);
			shares.put(sorted.get(i), share);
			sum += share;
		}
		if (sum != amountMinor) {
			throw new IllegalStateException("Shares " + sum + " do not add up to " + amountMinor);
		}
		return shares;
	}

}
