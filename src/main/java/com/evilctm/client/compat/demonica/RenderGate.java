/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.compat.demonica;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.util.BlockRenderLayer;

/**
 * What Evil CTM needs to know about the host renderer. Production uses {@link DemonicaBridge}; tests inject a fake
 * through {@code LayerRouter.setGateForTests}.
 */
public interface RenderGate {
	/** True when Demonica's S20 hook can run: Celeritas accepted and the fast block renderer switched on. */
	boolean fastPathActive();

	/** True when the block is always sent to the vanilla renderer (so it never reaches S20). */
	boolean blockForcedVanilla(Block block);

	/** The shader pack's {@code layer.*} override for the block, or {@code null}. */
	@Nullable
	BlockRenderLayer layerOverride(Block block);

	/** The gate's view of the render path (never {@link RenderPathStatus.Problem#NOT_INVOKED}). */
	RenderPathStatus.Problem probe();
}
