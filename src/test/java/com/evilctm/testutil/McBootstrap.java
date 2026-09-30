/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import net.minecraft.init.Bootstrap;

/** Registers vanilla blocks, items and biomes once per test JVM. */
public final class McBootstrap {
	private static boolean done;

	private McBootstrap() {
	}

	public static synchronized void ensure() {
		if (!done) {
			Bootstrap.register();
			done = true;
		}
	}
}
