/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import java.util.List;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.model.EmissiveBakedQuad;
import com.evilctm.impl.client.EmissiveSpriteApiImpl;
import com.evilctm.impl.client.ProcessingContextImpl;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;

/**
 * Appends emissive ({@code _e}) companions, routed so that nothing emissive is built in SOLID:
 * <ul>
 *     <li>A block whose native layers include SOLID gets its emissive quads in CUTOUT_MIPPED, where they are emitted
 *         alongside the native quads (when CUTOUT_MIPPED is native) or on their own (when it is an extra layer).</li>
 *     <li>For any other native layer, companions are emitted in that layer.</li>
 *     <li>Overlay quads get companions in their own layer (SOLID companions are deferred only when the extra layer is granted).</li>
 * </ul>
 */
public final class EmissivePass {
	private static final int SOLID_BIT = LayerRouter.bit(BlockRenderLayer.SOLID);

	private EmissivePass() {
	}

	/**
	 * Entry point for callers that do not know the native mask or the base outputs: keeps the original behaviour of
	 * emitting companions in the native layer itself (SOLID included) and nothing in extra layers. It never depends on
	 * the CUTOUT_MIPPED extra layer being granted.
	 */
	@Nullable
	public static List<BakedQuad> apply(@Nullable List<BakedQuad> out, List<BakedQuad> original, BlockRenderLayer layer, boolean nativeLayer, ProcessingContextImpl ctx) {
		if (!nativeLayer) {
			return out;
		}
		return appendCompanions(out, original, out != null ? out : original, (out != null ? out : original).size());
	}

	/** Same as the 8-argument form, deferring SOLID companions only when the config allows the extra layer at all. */
	@Nullable
	public static List<BakedQuad> apply(@Nullable List<BakedQuad> out, List<BakedQuad> original, @Nullable List<BakedQuad> baseOutputs, BlockRenderLayer layer, boolean nativeLayer, int nativeMask, ProcessingContextImpl ctx) {
		return apply(out, original, baseOutputs, layer, nativeLayer, nativeMask, configAllowsExtraLayer(), ctx);
	}

	private static boolean configAllowsExtraLayer() {
		try {
			EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
			return cfg.extraLayers.get() && cfg.connectedTextures.get();
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/**
	 * @param out             the output so far, or {@code null} while it is still identical to {@code original}
	 * @param baseOutputs     in a non-native pass, the processed base quads that the layer filter dropped from this pass
	 *                        (their companions are the emissive layer's content for a SOLID block); otherwise ignored
	 * @param nativeMask      the block's native layer mask
	 * @param deferSolid      whether the CUTOUT_MIPPED extra layer will be granted to this block; when {@code false},
	 *                        SOLID companions are emitted in SOLID itself
	 * @return the (possibly newly allocated) output, or {@code null} when nothing changed
	 */
	@Nullable
	public static List<BakedQuad> apply(@Nullable List<BakedQuad> out, List<BakedQuad> original, @Nullable List<BakedQuad> baseOutputs, BlockRenderLayer layer, boolean nativeLayer, int nativeMask, boolean deferSolid, ProcessingContextImpl ctx) {
		if (layer == BlockRenderLayer.SOLID && nativeLayer && deferSolid) {
			return out;
		}
		if (nativeLayer) {
			List<BakedQuad> source = out != null ? out : original;
			return appendCompanions(out, original, source, source.size());
		}
		List<BakedQuad> seed = java.util.Collections.emptyList();
		if (baseOutputs != null && layer == BlockRenderLayer.CUTOUT_MIPPED && (nativeMask & SOLID_BIT) != 0) {
			out = appendCompanions(out, seed, baseOutputs, baseOutputs.size());
		}
		for (int i = 0, m = ctx.overlayCount(); i < m; i++) {
			if (ctx.overlayLayer(i) == layer) {
				out = appendCompanion(out, seed, ctx.overlayQuad(i));
			}
		}
		return out;
	}

	@Nullable
	private static List<BakedQuad> appendCompanions(@Nullable List<BakedQuad> out, List<BakedQuad> seed, List<BakedQuad> source, int size) {
		for (int i = 0; i < size; i++) {
			out = appendCompanion(out, seed, source.get(i));
		}
		return out;
	}

	@Nullable
	private static List<BakedQuad> appendCompanion(@Nullable List<BakedQuad> out, List<BakedQuad> seed, BakedQuad quad) {
		TextureAtlasSprite sprite = quad.getSprite();
		if (sprite == null) {
			return out;
		}
		TextureAtlasSprite emissive = EmissiveSpriteApiImpl.INSTANCE.getEmissiveSprite(sprite);
		if (emissive != null && CtmModLayerFilter.allowSuffixEmissive(emissive)) {
			if (out == null) {
				out = new ObjectArrayList<>(seed.size() + 4);
				out.addAll(seed);
			}
			out.add(EmissiveBakedQuad.create(quad, emissive));
		}
		return out;
	}
}
