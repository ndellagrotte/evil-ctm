/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import net.minecraft.block.state.IBlockState;

/**
 * A reason for a block state to render in a layer it does not natively use. Masks use one bit per
 * {@code BlockRenderLayer.ordinal()} over all {@code BlockRenderLayer.values()}.
 */
public interface ExtraLayerSource {
	/** Cheap check; inactive sources are not consulted. */
	boolean active();

	/** The layers {@code rawState} needs beyond {@code nativeMask}. Native bits in the result are ignored. */
	int extraLayerMask(IBlockState rawState, int nativeMask);
}
