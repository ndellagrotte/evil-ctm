/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.model;

/**
 * A counter bumped whenever model-derived data may have changed: after {@link QuadProcessors#reload} publishes new
 * tables, and after the model manager reloads models. Every cache derived from model data keys on {@link #current()}.
 */
public final class ReloadEpoch {
	private static volatile long epoch;

	private ReloadEpoch() {
	}

	public static long current() {
		return epoch;
	}

	public static synchronized long bump() {
		return ++epoch;
	}
}
