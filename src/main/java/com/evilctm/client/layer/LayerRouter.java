/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.evilctm.client.compat.demonica.DemonicaBridge;
import com.evilctm.client.compat.demonica.RenderGate;
import com.evilctm.client.compat.demonica.RenderPathStatus;
import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.model.ReloadEpoch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;

/**
 * Decides which render layers a block state is built in. The native mask is the block's own
 * {@code canRenderInLayer} answer, asked live on every call (it changes at runtime, e.g. leaves with the graphics
 * setting); extra layers come from the {@link ExtraLayerSource}s and are granted only where S20 will see the quads:
 * on Demonica's fast path, at a position Demonica does not send to vanilla's renderer, and outside shader builds that
 * move the block to a pack layer. The grant is applied at Celeritas' {@code canRenderInLayer} call site
 * ({@code ChunkBuilderMeshingTaskMixin}), so blocks that override {@code canRenderInLayer} are covered too.
 * Extra masks are cached per raw state (what Celeritas passes to {@code canRenderInLayer}) and {@link ReloadEpoch},
 * together with the native mask they were computed against.
 */
public final class LayerRouter {
	public static final BlockRenderLayer[] LAYERS = BlockRenderLayer.values();

	/** Set while the sources probe models, so a model or block that asks the router again gets no extra layer. */
	private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final ExtraLayerSource[] SOURCES = {
			ProcessorLayerSource.INSTANCE,
			CtmModLayerSource.INSTANCE,
			EmissiveLayerSource.INSTANCE
	};
	private static final int MASK_BITS = 8;
	private static final int EXTRA_BITS = (1 << MASK_BITS) - 1;

	/** Read by the meshing mixin before anything else; false keeps that mixin free. */
	public static volatile boolean extraPossible;

	@Nullable
	private static volatile RenderGate gate;
	private static volatile Snapshot snapshot = new Snapshot(Long.MIN_VALUE);

	private LayerRouter() {
	}

	public static RenderGate gate() {
		RenderGate g = gate;
		if (g == null) {
			synchronized (LayerRouter.class) {
				g = gate;
				if (g == null) {
					g = new DemonicaBridge();
					gate = g;
				}
			}
		}
		return g;
	}

	/** Test hook: injects a gate; {@code null} returns to the lazily created Demonica bridge. */
	public static void setGateForTests(@Nullable RenderGate testGate) {
		gate = testGate;
		invalidate();
	}

	public static int bit(BlockRenderLayer layer) {
		return 1 << layer.ordinal();
	}

	/** Recomputes {@link #extraPossible} and drops cached masks. Called after reloads and config saves. */
	public static void refreshActive() {
		boolean possible = false;
		try {
			EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
			if (cfg.extraLayers.get() && cfg.connectedTextures.get() && !RenderPathStatus.apiMissing()) {
				for (ExtraLayerSource source : SOURCES) {
					if (source.active()) {
						possible = true;
						break;
					}
				}
			}
		} catch (RuntimeException | LinkageError e) {
			possible = false;
		}
		invalidate();
		extraPossible = possible;
	}

	private static void invalidate() {
		snapshot = new Snapshot(ReloadEpoch.current());
	}

	private static Snapshot snapshot() {
		Snapshot s = snapshot;
		long epoch = ReloadEpoch.current();
		if (s.epoch != epoch) {
			s = new Snapshot(epoch);
			snapshot = s;
		}
		return s;
	}

	/**
	 * True when {@code layer} is one the block builds in by itself, or the shader pack's override layer. When the
	 * override cannot be read (a broken bridge), every layer counts as native: no extra layer is granted then, so any
	 * layer the block is meshed in is a native one or the pack's.
	 */
	public static boolean isNativeLayer(IBlockState rawState, BlockRenderLayer layer) {
		if (nativeIn(rawState, layer)) {
			return true;
		}
		RenderGate g = gate();
		BlockRenderLayer override = g.layerOverride(rawState.getBlock());
		return override == layer || !g.layerOverrideReliable();
	}

	/** The block's own {@code canRenderInLayer} mask, asked live. */
	public static int nativeMask(IBlockState rawState) {
		int mask = 0;
		for (BlockRenderLayer layer : LAYERS) {
			if (nativeIn(rawState, layer)) {
				mask |= bit(layer);
			}
		}
		return mask;
	}

	private static boolean nativeIn(IBlockState rawState, BlockRenderLayer layer) {
		Boolean previous = BYPASS.get();
		BYPASS.set(Boolean.TRUE);
		try {
			return rawState.getBlock().canRenderInLayer(rawState, layer);
		} finally {
			BYPASS.set(previous);
		}
	}

	/** {@link #allowExtraLayer(IBlockState, BlockRenderLayer, BlockPos)} without a position. */
	public static boolean allowExtraLayer(IBlockState rawState, BlockRenderLayer layer) {
		return allowExtraLayer(rawState, layer, null);
	}

	/** The meshing mixin's question: should {@code rawState} at {@code pos} also build in {@code layer}? Never throws. */
	public static boolean allowExtraLayer(IBlockState rawState, BlockRenderLayer layer, @Nullable BlockPos pos) {
		return (grantedExtraMask(rawState, pos) & bit(layer)) != 0;
	}

	/**
	 * The extra layers the meshing mixin grants {@code rawState} at {@code pos} (never native ones): the single
	 * predicate behind both the grant and the pipeline's routing, so the two cannot disagree. Never throws.
	 */
	public static int grantedExtraMask(IBlockState rawState, @Nullable BlockPos pos) {
		try {
			if (BYPASS.get() || !extraPossible) {
				return 0;
			}
			EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
			if (!cfg.extraLayers.get() || !cfg.connectedTextures.get() || RenderPathStatus.apiMissing()) {
				return 0;
			}
			if (rawState.getRenderType() != EnumBlockRenderType.MODEL) {
				return 0;
			}
			int extra = extraMask(rawState);
			if (extra == 0) {
				return 0;
			}
			RenderGate g = gate();
			if (!g.fastPathActive() || g.blockForcedVanilla(rawState.getBlock())) {
				return 0;
			}
			// A shader build meshes the block in the pack layer only; never widen what Demonica narrowed.
			if (g.layerOverride(rawState.getBlock()) != null || !g.layerOverrideReliable()) {
				return 0;
			}
			if (pos != null && g.forcedVanillaAt(rawState.getBlock(), pos)) {
				return 0;
			}
			return extra;
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return 0;
		}
	}

	/**
	 * The layers the block at {@code pos} is really meshed in: the pack layer under a shader override, otherwise its
	 * native layers plus the granted extra ones. Never throws; on failure the native mask.
	 */
	public static int builtMask(IBlockState rawState, @Nullable BlockPos pos) {
		return builtMask(rawState, nativeMask(rawState), grantedExtraMask(rawState, pos));
	}

	/** {@link #builtMask(IBlockState, BlockPos)} from an already computed native mask and {@link #grantedExtraMask}. */
	public static int builtMask(IBlockState rawState, int nativeMask, int grantedExtraMask) {
		try {
			RenderGate g = gate();
			if (!g.layerOverrideReliable()) {
				return nativeMask;
			}
			BlockRenderLayer override = g.layerOverride(rawState.getBlock());
			if (override != null) {
				return bit(override);
			}
			return nativeMask | grantedExtraMask;
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return nativeMask;
		}
	}

	/** The layers the sources want for {@code rawState} beyond its (current) native mask; not gated on the render path. */
	public static int extraMask(IBlockState rawState) {
		ConcurrentHashMap<IBlockState, Integer> cache = snapshot().extraMask;
		int nativeMask = nativeMask(rawState);
		Integer cached = cache.get(rawState);
		if (cached != null && cached >>> MASK_BITS == nativeMask) {
			return cached & EXTRA_BITS;
		}
		int mask = 0;
		// Sources probe models; a getQuads that calls canRenderInLayer must not re-enter this computation.
		Boolean previous = BYPASS.get();
		BYPASS.set(Boolean.TRUE);
		try {
			for (ExtraLayerSource source : SOURCES) {
				if (source.active()) {
					mask |= source.extraLayerMask(rawState, nativeMask);
				}
			}
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			mask = 0;
		} finally {
			BYPASS.set(previous);
		}
		mask &= ~nativeMask & EXTRA_BITS;
		cache.put(rawState, nativeMask << MASK_BITS | mask);
		return mask;
	}

	private static final class Snapshot {
		final long epoch;
		/** Per raw state: {@code nativeMask << MASK_BITS | extraMask}, recomputed when the live native mask differs. */
		final ConcurrentHashMap<IBlockState, Integer> extraMask = new ConcurrentHashMap<>();

		Snapshot(long epoch) {
			this.epoch = epoch;
		}
	}
}
