/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.ctm.CtmRenderLayerRouter;
import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.model.BakedQuadLightmap;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.impl.client.EmissiveSpriteApiImpl;
import com.evilctm.impl.client.ProcessingContextImpl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

/**
 * The per-(block, layer, side) quad transform behind the S20 hook.
 *
 * <ul>
 *     <li>Never mutates {@code quads}, never returns {@code null}, and returns {@code quads} itself when nothing changed.</li>
 *     <li>Reads the published processor tables once per call and never keeps {@code pos} or {@code access}.</li>
 *     <li>Processors see the clean actual state ({@link QuadProcessors#cacheKey}) as both state and appearance state.</li>
 *     <li>Layer nativeness is keyed on the raw world state, which is what Celeritas passes to {@code canRenderInLayer}.</li>
 * </ul>
 */
public final class QuadPipeline {
	private static final ThreadLocal<ProcessingContextImpl> CTX = ThreadLocal.withInitial(ProcessingContextImpl::new);

	private QuadPipeline() {
	}

	/** {@link #transform}, but never throws: on failure the input is kept in native layers and dropped elsewhere. */
	public static List<BakedQuad> transformSafely(IBlockState state, BlockPos pos, IBlockAccess access, BlockRenderLayer layer, @Nullable EnumFacing side, List<BakedQuad> quads) {
		try {
			return transform(state, pos, access, layer, side, quads);
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			ErrorReporter.reportOnce("QuadPipeline.transform", e, state);
			return nativeLayerOrTrue(state, pos, access, layer) ? quads : Collections.emptyList();
		}
	}

	public static List<BakedQuad> transform(IBlockState state, BlockPos pos, IBlockAccess access, BlockRenderLayer layer, @Nullable EnumFacing side, List<BakedQuad> quads) {
		if (quads.isEmpty()) {
			return quads;
		}
		EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
		QuadProcessors.Tables tables = QuadProcessors.current();
		IBlockState s = QuadProcessors.cacheKey(state);
		boolean connected = cfg.connectedTextures.get();
		boolean process = connected && !tables.isEmpty();
		boolean ctm = process || connected && QuadCullers.mayCull(s);
		boolean emissive = cfg.emissiveTextures.get() && EmissiveSpriteApiImpl.hasAny();
		boolean ctmMod = connected && cfg.ctmModTextures.get() && CtmRenderLayerRouter.active();
		if (!ctm && !emissive && !ctmMod) {
			return quads;
		}

		boolean nativeLayer = LayerRouter.isNativeLayer(rawState(access, pos, s), layer);
		ProcessingContextImpl ctx = CTX.get().begin(tables, layer);
		try {
			List<BakedQuad> out = null;
			long rand = process ? MathHelper.getPositionRandom(pos) : 0L;
			int n = quads.size();
			for (int i = 0; i < n; i++) {
				BakedQuad quad = quads.get(i);
				if (connected && QuadCullers.shouldCull(quad, s, pos, access, side)) {
					out = copyPrefixIfNull(out, quads, i);
					continue;
				}
				boolean include = CtmModLayerFilter.shouldRender(quad.getSprite(), layer, nativeLayer);
				if (!process) {
					if (!include) {
						out = copyPrefixIfNull(out, quads, i);
					} else if (CtmModLayerFilter.fullbrightFallback(quad.getSprite(), nativeLayer)) {
						out = copyPrefixIfNull(out, quads, i);
						out.add(BakedQuadLightmap.withMinimum(quad, 15, 15));
					} else if (out != null) {
						out.add(quad);
					}
					continue;
				}

				List<BakedQuad> produced = ProcessingChain.run(quad, s, pos, access, tables, ctx, rand);
				if (!include) {
					out = copyPrefixIfNull(out, quads, i);
					continue;
				}
				boolean fullbright = CtmModLayerFilter.fullbrightFallback(quad.getSprite(), nativeLayer);
				if (!fullbright && produced.size() == 1 && produced.get(0) == quad) {
					if (out != null) {
						out.add(quad);
					}
					continue;
				}
				out = copyPrefixIfNull(out, quads, i);
				for (int j = 0, m = produced.size(); j < m; j++) {
					BakedQuad p = produced.get(j);
					out.add(fullbright ? BakedQuadLightmap.withMinimum(p, 15, 15) : p);
				}
			}

			for (int i = 0, m = ctx.overlayCount(); i < m; i++) {
				if (ctx.overlayLayer(i) == layer) {
					out = copyPrefixIfNull(out, quads, n);
					out.add(ctx.overlayQuad(i));
				}
			}

			if (emissive) {
				out = EmissivePass.apply(out, quads, layer, nativeLayer, ctx);
			}
			return out != null ? out : quads;
		} finally {
			ctx.end();
		}
	}

	/** The raw world state at {@code pos}; falls back to {@code fallback} when unreadable or of another block. */
	static IBlockState rawState(IBlockAccess access, BlockPos pos, IBlockState fallback) {
		try {
			IBlockState raw = access.getBlockState(pos);
			return raw != null && raw.getBlock() == fallback.getBlock() ? raw : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	/** Whether {@code layer} is native for the block; {@code true} when that cannot be determined. */
	static boolean nativeLayerOrTrue(IBlockState state, BlockPos pos, IBlockAccess access, BlockRenderLayer layer) {
		try {
			return LayerRouter.isNativeLayer(rawState(access, pos, QuadProcessors.cacheKey(state)), layer);
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return true;
		}
	}

	private static List<BakedQuad> copyPrefixIfNull(@Nullable List<BakedQuad> out, List<BakedQuad> quads, int prefix) {
		if (out != null) {
			return out;
		}
		List<BakedQuad> copy = new ObjectArrayList<>(quads.size() + 4);
		for (int i = 0; i < prefix; i++) {
			copy.add(quads.get(i));
		}
		return copy;
	}

	/** Test hook: the calling thread's pooled context. */
	public static ProcessingContextImpl contextForTests() {
		return CTX.get();
	}
}
