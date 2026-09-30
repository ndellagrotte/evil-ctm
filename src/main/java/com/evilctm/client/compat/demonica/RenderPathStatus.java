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

		/** The remedy for this problem: only {@link #FAST_RENDERER_OFF} is fixed by the fast block renderer option. */
		public String hintKey() {
			return "evilctm.warning.hint." + name().toLowerCase(java.util.Locale.ROOT);
		}

		/** The remedy, in English, for the log. */
		public String remedy() {
			return switch (this) {
				case OK -> "";
				case FAST_RENDERER_OFF -> "Enable Video Settings -> Use Fast Block Renderer (performance.use_fast_block_renderer in config/demonica-options.json)";
				case CELERITAS_REJECTED -> "Install the Celeritas build this Demonica version accepts (see Demonica's log for the expected build)";
				case API_MISSING, BRIDGE_BROKEN -> "Update Demonica (or Evil CTM) to versions that support each other";
				case NOT_INVOKED -> "Please report this with your latest.log attached";
			};
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

	/** True when the S20 transformer could not be registered: nothing Evil CTM grants can reach it. */
	public static boolean apiMissing() {
		return apiMissing;
	}

	/** Test hook: forgets a recorded {@link #recordApiMissing}. */
	public static void clearApiMissingForTests() {
		apiMissing = false;
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
		EvilCtmClient.LOGGER.warn("Evil CTM renders only through Demonica's fast block renderer (S20 hook).");
		EvilCtmClient.LOGGER.warn(problem.remedy());
		EvilCtmClient.LOGGER.warn("==============================================================");
	}
}
