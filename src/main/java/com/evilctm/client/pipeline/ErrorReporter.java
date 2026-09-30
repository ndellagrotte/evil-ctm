/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.evilctm.client.EvilCtmClient;
import net.minecraft.block.state.IBlockState;

/** Logs each distinct render-path failure once (key: exception class plus first stack frame; at most 64 keys). */
public final class ErrorReporter {
	private static final int MAX_KEYS = 64;
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	private ErrorReporter() {
	}

	/** @return true when this call logged */
	public static boolean reportOnce(String where, Throwable t, @Nullable IBlockState state) {
		try {
			StackTraceElement[] trace = t.getStackTrace();
			String key = t.getClass().getName() + '@' + (trace.length > 0 ? trace[0] : "?");
			if (SEEN.size() >= MAX_KEYS || !SEEN.add(key)) {
				return false;
			}
			EvilCtmClient.LOGGER.error("Evil CTM failed in {} for state {}; the block renders without CTM. Further identical errors are not logged.", where, state, t);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** Test hook. */
	public static void resetForTests() {
		SEEN.clear();
	}

	public static int reportedCount() {
		return SEEN.size();
	}
}
