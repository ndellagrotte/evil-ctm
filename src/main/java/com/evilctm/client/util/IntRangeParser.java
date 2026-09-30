/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util;

import java.util.function.IntPredicate;

import javax.annotation.Nullable;

/** Parses integer lists with ranges such as {@code 1,2,5-7} into an {@link IntPredicate}. */
public final class IntRangeParser {
	private IntRangeParser() {
	}

	/**
	 * Elements are separated by commas and/or spaces; each is {@code n} or {@code a-b} (inclusive, non-negative).
	 *
	 * @return the predicate, or {@code null} if the text is empty or any element is malformed
	 */
	@Nullable
	public static IntPredicate parse(@Nullable String text) {
		if (text == null) {
			return null;
		}
		String[] elements = text.trim().split("[,\\s]+");
		int[] lo = new int[elements.length];
		int[] hi = new int[elements.length];
		int count = 0;
		for (String element : elements) {
			if (element.isEmpty()) {
				continue;
			}
			try {
				int dash = element.indexOf('-');
				if (dash < 0) {
					lo[count] = hi[count] = Integer.parseInt(element);
				} else if (dash == 0 || dash == element.length() - 1) {
					return null;
				} else {
					int a = Integer.parseInt(element.substring(0, dash));
					int b = Integer.parseInt(element.substring(dash + 1));
					if (a > b) {
						return null;
					}
					lo[count] = a;
					hi[count] = b;
				}
			} catch (NumberFormatException e) {
				return null;
			}
			if (lo[count] < 0) {
				return null;
			}
			count++;
		}
		if (count == 0) {
			return null;
		}
		int n = count;
		if (n == 1) {
			int a = lo[0];
			int b = hi[0];
			return a == b ? v -> v == a : v -> v >= a && v <= b;
		}
		return v -> {
			for (int i = 0; i < n; i++) {
				if (v >= lo[i] && v <= hi[i]) {
					return true;
				}
			}
			return false;
		};
	}
}
