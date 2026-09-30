/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.evilctm.client.compat.demonica.DemonicaBridge;
import com.evilctm.client.compat.demonica.RenderGate;
import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.model.ReloadEpoch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;

/**
 * Decides which render layers a block state is built in. The native mask is the block's own
 * {@code canRenderInLayer} answer (computed with our mixin bypassed); extra layers come from the
 * {@link ExtraLayerSource}s and are granted only on Demonica's fast path, where S20 will see the quads.
 * Masks are keyed on the raw state (what Celeritas passes to {@code canRenderInLayer}) and on {@link ReloadEpoch}.
 */
public final class LayerRouter {
	public static final BlockRenderLayer[] LAYERS = BlockRenderLayer.values();

	private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final ExtraLayerSource[] SOURCES = {
			ProcessorLayerSource.INSTANCE,
			CtmModLayerSource.INSTANCE,
			EmissiveLayerSource.INSTANCE
	};

	/** Read by the {@code canRenderInLayer} mixin before anything else; false keeps that mixin free. */
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
			if (cfg.extraLayers.get() && cfg.connectedTextures.get()) {
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

	/** True when {@code layer} is one the block builds in by itself, or the shader pack's override layer. */
	public static boolean isNativeLayer(IBlockState rawState, BlockRenderLayer layer) {
		if ((nativeMask(rawState) & bit(layer)) != 0) {
			return true;
		}
		return gate().layerOverride(rawState.getBlock()) == layer;
	}

	public static int nativeMask(IBlockState rawState) {
		ConcurrentHashMap<IBlockState, Integer> cache = snapshot().nativeMask;
		Integer cached = cache.get(rawState);
		if (cached != null) {
			return cached;
		}
		int mask = computeNativeMask(rawState);
		cache.putIfAbsent(rawState, mask);
		return mask;
	}

	private static int computeNativeMask(IBlockState rawState) {
		Boolean previous = BYPASS.get();
		BYPASS.set(Boolean.TRUE);
		try {
			int mask = 0;
			for (BlockRenderLayer layer : LAYERS) {
				if (rawState.getBlock().canRenderInLayer(rawState, layer)) {
					mask |= bit(layer);
				}
			}
			return mask;
		} finally {
			BYPASS.set(previous);
		}
	}

	/** The {@code canRenderInLayer} mixin's question: should {@code rawState} also build in {@code layer}? Never throws. */
	public static boolean allowExtraLayer(IBlockState rawState, BlockRenderLayer layer) {
		try {
			if (BYPASS.get()) {
				return false;
			}
			EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
			if (!cfg.extraLayers.get() || !cfg.connectedTextures.get()) {
				return false;
			}
			if (rawState.getRenderType() != EnumBlockRenderType.MODEL) {
				return false;
			}
			RenderGate g = gate();
			if (!g.fastPathActive() || g.blockForcedVanilla(rawState.getBlock())) {
				return false;
			}
			return (extraMask(rawState) & bit(layer)) != 0;
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	public static int extraMask(IBlockState rawState) {
		ConcurrentHashMap<IBlockState, Integer> cache = snapshot().extraMask;
		Integer cached = cache.get(rawState);
		if (cached != null) {
			return cached;
		}
		int nativeMask = nativeMask(rawState);
		int mask = 0;
		for (ExtraLayerSource source : SOURCES) {
			if (source.active()) {
				mask |= source.extraLayerMask(rawState, nativeMask);
			}
		}
		mask &= ~nativeMask;
		cache.putIfAbsent(rawState, mask);
		return mask;
	}

	private static final class Snapshot {
		final long epoch;
		final ConcurrentHashMap<IBlockState, Integer> nativeMask = new ConcurrentHashMap<>();
		final ConcurrentHashMap<IBlockState, Integer> extraMask = new ConcurrentHashMap<>();

		Snapshot(long epoch) {
			this.epoch = epoch;
		}
	}
}
