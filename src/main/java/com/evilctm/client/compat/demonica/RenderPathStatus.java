/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.compat.demonica;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.layer.LayerRouter;

/**
 * Whether Evil CTM can actually render. Never references Demonica classes directly: everything goes through
 * {@link LayerRouter#gate()}.
 */
public final class RenderPathStatus {
	public enum Problem {
		OK,
		API_MISSING,
		CELERITAS_REJECTED,
		FAST_RENDERER_OFF,
		BRIDGE_BROKEN,
		NOT_INVOKED;

		public String translationKey() {
			return "evilctm.warning." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static volatile boolean invoked;
	private static volatile boolean apiMissing;

	private RenderPathStatus() {
	}

	/** Called by the S20 transformer on every invocation; writes only the first time. */
	public static void markInvoked() {
		if (!invoked) {
			invoked = true;
		}
	}

	public static boolean invoked() {
		return invoked;
	}

	/** Recorded when registering with Demonica's S20 API failed with a {@link LinkageError}. */
	public static void recordApiMissing() {
		apiMissing = true;
	}

	/** The current problem, excluding {@link Problem#NOT_INVOKED} (which needs time in-world to decide). */
	public static Problem evaluate() {
		if (apiMissing) {
			return Problem.API_MISSING;
		}
		try {
			return LayerRouter.gate().probe();
		} catch (RuntimeException | LinkageError e) {
			return Problem.BRIDGE_BROKEN;
		}
	}

	public static void logStartup() {
		Problem problem = evaluate();
		if (problem == Problem.OK) {
			EvilCtmClient.LOGGER.info("Render path OK: Demonica S20 transformer registered, fast block renderer on");
			return;
		}
		EvilCtmClient.LOGGER.warn("==============================================================");
		EvilCtmClient.LOGGER.warn("Evil CTM will not render connected textures: {}", problem);
		EvilCtmClient.LOGGER.warn("Evil CTM renders only through Demonica's fast block renderer. Enable Video Settings ->");
		EvilCtmClient.LOGGER.warn("Use Fast Block Renderer (performance.use_fast_block_renderer in config/demonica-options.json)");
		EvilCtmClient.LOGGER.warn("==============================================================");
	}
}
