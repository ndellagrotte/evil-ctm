/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import java.util.List;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.impl.client.ProcessingContextImpl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Runs one quad through the processors, with upstream Continuity's multipass semantics: pass 0 uses the slice's
 * processors, later passes its multipass processors, for at most {@link #PASSES} passes.
 *
 * <ul>
 *     <li>{@code NEXT_PROCESSOR}: try the next processor (its replacement, if any, is dropped).</li>
 *     <li>{@code NEXT_PASS}: take the replacement (if any) and start another pass on it.</li>
 *     <li>{@code STOP}: take the replacement (if any) and finish. Also the result when every processor declines.</li>
 *     <li>{@code DISCARD}: drop the quad; its extra quads are still emitted.</li>
 * </ul>
 * The output is the current quad (unless discarded) followed by the extra quads, which are final.
 */
public final class ProcessingChain {
	public static final int PASSES = 4;

	private ProcessingChain() {
	}

	/**
	 * @param state the clean state, used as both appearance state and state
	 * @return the chain output, a list owned by {@code ctx} that is valid until the next call
	 */
	public static List<BakedQuad> run(BakedQuad quad, IBlockState state, BlockPos pos, IBlockAccess access, QuadProcessors.Tables tables, ProcessingContextImpl ctx, long rand) {
		List<BakedQuad> output = ctx.chainOutput();
		List<BakedQuad> extras = ctx.getExtraQuads();
		output.clear();
		extras.clear();
		ctx.clearReplacement();

		BakedQuad current = quad;
		for (int pass = 0; pass < PASSES; pass++) {
			TextureAtlasSprite sprite = current.getSprite();
			if (sprite == null) {
				break;
			}
			QuadProcessors.Slice slice = tables.slice(state, sprite);
			QuadProcessor[] processors = pass == 0 ? slice.processors() : slice.multipassProcessors();
			if (processors.length == 0) {
				break;
			}

			QuadProcessor.ProcessingResult result = QuadProcessor.ProcessingResult.STOP;
			for (QuadProcessor processor : processors) {
				ctx.clearReplacement();
				QuadProcessor.ProcessingResult r = processor.processQuad(current, sprite, access, pos, state, state, rand, pass, ctx);
				if (r == QuadProcessor.ProcessingResult.NEXT_PROCESSOR) {
					ctx.clearReplacement();
					continue;
				}
				result = r;
				break;
			}

			BakedQuad replacement = ctx.takeReplacement();
			if (replacement != null) {
				current = replacement;
			}
			if (result == QuadProcessor.ProcessingResult.NEXT_PASS) {
				continue;
			}
			if (result == QuadProcessor.ProcessingResult.DISCARD) {
				current = null;
			}
			break;
		}

		if (current != null) {
			output.add(current);
		}
		output.addAll(extras);
		extras.clear();
		return output;
	}
}
