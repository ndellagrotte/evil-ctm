/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import javax.annotation.Nullable;

import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.ctm.CtmRenderLayerRouter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;

/**
 * Per-quad layer decisions for CTM-mod {@code layer} metadata (CleanContinuity semantics, with "routed layer" meaning
 * "not a native layer"). When CTM-mod routing is off, a quad renders exactly in native layers.
 */
public final class CtmModLayerFilter {
	private CtmModLayerFilter() {
	}

	public static boolean routingActive() {
		EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
		return cfg.connectedTextures.get() && cfg.ctmModTextures.get() && CtmRenderLayerRouter.active();
	}

	/** Whether a base quad with {@code sprite} is kept in {@code layer}. */
	public static boolean shouldRender(@Nullable TextureAtlasSprite sprite, BlockRenderLayer layer, boolean nativeLayer) {
		if (!routingActive()) {
			return nativeLayer;
		}
		return CtmRenderLayerRouter.shouldRender(sprite, layer, !nativeLayer);
	}

	/** Whether the outputs of a quad with {@code sprite} are forced full bright (CTM-mod emissive fallback). */
	public static boolean fullbrightFallback(@Nullable TextureAtlasSprite sprite, boolean nativeLayer) {
		return routingActive() && CtmRenderLayerRouter.shouldFullbrightEmissiveFallback(sprite, !nativeLayer);
	}

	/** Whether an OptiFine {@code _e} companion may be generated for {@code emissiveSprite}. */
	public static boolean allowSuffixEmissive(@Nullable TextureAtlasSprite emissiveSprite) {
		return !routingActive() || CtmRenderLayerRouter.shouldGenerateSuffixOverlay(emissiveSprite);
	}
}
