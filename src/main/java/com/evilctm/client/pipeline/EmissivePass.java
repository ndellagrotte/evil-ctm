/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import java.util.List;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.evilctm.client.layer.LayerRouter;
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
 *     <li>Overlay quads get companions in their own layer (SOLID overlays follow the SOLID rule).</li>
 * </ul>
 */
public final class EmissivePass {
	private static final int SOLID_BIT = LayerRouter.bit(BlockRenderLayer.SOLID);

	private EmissivePass() {
	}

	/**
	 * Transitional entry point for callers that do not know the native mask or the base outputs. A non-native
	 * CUTOUT_MIPPED pass is assumed to be the SOLID block's emissive layer and uses the original quads as base outputs.
	 */
	@Nullable
	public static List<BakedQuad> apply(@Nullable List<BakedQuad> out, List<BakedQuad> original, BlockRenderLayer layer, boolean nativeLayer, ProcessingContextImpl ctx) {
		if (nativeLayer) {
			return apply(out, original, null, layer, true, LayerRouter.bit(layer), ctx);
		}
		return apply(out, original, layer == BlockRenderLayer.CUTOUT_MIPPED ? original : null, layer, false, SOLID_BIT, ctx);
	}

	/**
	 * @param out           the output so far, or {@code null} while it is still identical to {@code original}
	 * @param baseOutputs   in a non-native pass, the processed base quads that the layer filter dropped from this pass
	 *                      (their companions are the emissive layer's content for a SOLID block); otherwise ignored
	 * @param nativeMask    the block's native layer mask
	 * @return the (possibly newly allocated) output, or {@code null} when nothing changed
	 */
	@Nullable
	public static List<BakedQuad> apply(@Nullable List<BakedQuad> out, List<BakedQuad> original, @Nullable List<BakedQuad> baseOutputs, BlockRenderLayer layer, boolean nativeLayer, int nativeMask, ProcessingContextImpl ctx) {
		if (layer == BlockRenderLayer.SOLID) {
			return out;
		}
		List<BakedQuad> seed = nativeLayer ? original : java.util.Collections.emptyList();
		List<BakedQuad> source = out != null ? out : (nativeLayer ? original : null);
		if (source != null) {
			out = appendCompanions(out, seed, source, source.size());
		}
		if (!nativeLayer && baseOutputs != null && layer == BlockRenderLayer.CUTOUT_MIPPED && (nativeMask & SOLID_BIT) != 0) {
			out = appendCompanions(out, seed, baseOutputs, baseOutputs.size());
		}
		if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
			for (int i = 0, m = ctx.overlayCount(); i < m; i++) {
				if (ctx.overlayLayer(i) == BlockRenderLayer.SOLID) {
					List<BakedQuad> one = java.util.Collections.singletonList(ctx.overlayQuad(i));
					out = appendCompanions(out, seed, one, 1);
				}
			}
		}
		return out;
	}

	@Nullable
	private static List<BakedQuad> appendCompanions(@Nullable List<BakedQuad> out, List<BakedQuad> seed, List<BakedQuad> source, int size) {
		for (int i = 0; i < size; i++) {
			BakedQuad quad = source.get(i);
			TextureAtlasSprite sprite = quad.getSprite();
			if (sprite == null) {
				continue;
			}
			TextureAtlasSprite emissive = EmissiveSpriteApiImpl.INSTANCE.getEmissiveSprite(sprite);
			if (emissive != null && CtmModLayerFilter.allowSuffixEmissive(emissive)) {
				if (out == null) {
					out = new ObjectArrayList<>(seed.size() + 4);
					out.addAll(seed);
				}
				out.add(EmissiveBakedQuad.create(quad, emissive));
			}
		}
		return out;
	}
}
