/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import java.util.List;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.evilctm.client.model.EmissiveBakedQuad;
import com.evilctm.impl.client.EmissiveSpriteApiImpl;
import com.evilctm.impl.client.ProcessingContextImpl;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;

/**
 * Appends emissive ({@code _e}) companions. Wave 1 keeps CleanContinuity's behaviour: in a native layer, every output
 * quad with an emissive pair gets a full-bright companion in the same layer. Wave 2 (U8) owns the layer routing.
 */
public final class EmissivePass {
	private EmissivePass() {
	}

	/**
	 * @param out the output so far, or {@code null} while it is still identical to {@code original}
	 * @return the (possibly newly allocated) output, or {@code null} when nothing changed
	 */
	@Nullable
	public static List<BakedQuad> apply(@Nullable List<BakedQuad> out, List<BakedQuad> original, BlockRenderLayer layer, boolean nativeLayer, ProcessingContextImpl ctx) {
		if (!nativeLayer) {
			return out;
		}
		List<BakedQuad> source = out != null ? out : original;
		int size = source.size();
		for (int i = 0; i < size; i++) {
			BakedQuad quad = source.get(i);
			TextureAtlasSprite sprite = quad.getSprite();
			if (sprite == null) {
				continue;
			}
			TextureAtlasSprite emissive = EmissiveSpriteApiImpl.INSTANCE.getEmissiveSprite(sprite);
			if (emissive != null && CtmModLayerFilter.allowSuffixEmissive(emissive)) {
				if (out == null) {
					out = new ObjectArrayList<>(original.size() + 4);
					out.addAll(original);
					source = out;
				}
				out.add(EmissiveBakedQuad.create(quad, emissive));
			}
		}
		return out;
	}
}
