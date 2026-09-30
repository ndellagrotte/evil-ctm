/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.compat.demonica;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;

/**
 * What Evil CTM needs to know about the host renderer. Production uses {@link DemonicaBridge}; tests inject a fake
 * through {@code LayerRouter.setGateForTests}.
 */
public interface RenderGate {
	/** True when Demonica's S20 hook can run: Celeritas accepted and the fast block renderer switched on. */
	boolean fastPathActive();

	/** True when the block is always sent to the vanilla renderer (so it never reaches S20). */
	boolean blockForcedVanilla(Block block);

	/**
	 * True when the block at {@code pos} is sent to the vanilla renderer. The decision is per position (e.g. next to a
	 * block Component Model Hider hides), a superset of {@link #blockForcedVanilla}.
	 */
	default boolean forcedVanillaAt(Block block, BlockPos pos) {
		return blockForcedVanilla(block);
	}

	/** The shader pack's {@code layer.*} override for the block, or {@code null}. */
	@Nullable
	BlockRenderLayer layerOverride(Block block);

	/** False when {@link #layerOverride} cannot be read, so its {@code null} does not mean "no override". */
	default boolean layerOverrideReliable() {
		return true;
	}

	/** The gate's view of the render path (never {@link RenderPathStatus.Problem#NOT_INVOKED}). */
	RenderPathStatus.Problem probe();
}
