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
 * Appends emissive ({@code _e}) companions, routed so that nothing emissive is built in SOLID when that can be avoided:
 * <ul>
 *     <li>A SOLID-only block granted the CUTOUT_MIPPED extra layer gets its companions there, from the processed base
 *         quads that pass drops; its SOLID pass emits none.</li>
 *     <li>Otherwise companions are emitted in the native layer itself (SOLID included).</li>
 *     <li>Overlay quads get companions in their own layer.</li>
 * </ul>
 */
public final class EmissivePass {
	private static final int SOLID_BIT = LayerRouter.bit(BlockRenderLayer.SOLID);

	private EmissivePass() {
	}

	/**
	 * @param out         the output so far, or {@code null} while it is still identical to {@code original}
	 * @param baseOutputs in the CUTOUT_MIPPED pass of a SOLID-only block with {@code deferSolid}, the processed base quads
	 *                    that the pass drops (their companions are the emissive layer's content); otherwise ignored
	 * @param nativeMask  the block's native layer mask
	 * @param deferSolid  whether this SOLID-only block is really granted the CUTOUT_MIPPED pass (as decided by
	 *                    {@code LayerRouter.grantedExtraMask}); when {@code false}, SOLID companions are emitted in SOLID
	 * @return the (possibly newly allocated) output, or {@code null} when nothing changed
	 */
	@Nullable
	public static List<BakedQuad> apply(@Nullable List<BakedQuad> out, List<BakedQuad> original, @Nullable List<BakedQuad> baseOutputs, BlockRenderLayer layer, boolean nativeLayer, int nativeMask, boolean deferSolid, ProcessingContextImpl ctx) {
		boolean deferred = deferSolid && nativeMask == SOLID_BIT;
		if (layer == BlockRenderLayer.SOLID && nativeLayer && deferred) {
			return out;
		}
		if (nativeLayer) {
			List<BakedQuad> source = out != null ? out : original;
			return appendCompanions(out, original, source, source.size());
		}
		// out == null still means "identical to original"
		if (baseOutputs != null && layer == BlockRenderLayer.CUTOUT_MIPPED && deferred) {
			out = appendCompanions(out, original, baseOutputs, baseOutputs.size());
		}
		for (int i = 0, m = ctx.overlayCount(); i < m; i++) {
			if (ctx.overlayLayer(i) == layer) {
				out = appendCompanion(out, original, ctx.overlayQuad(i));
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
