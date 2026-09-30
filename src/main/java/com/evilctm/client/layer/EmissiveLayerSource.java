/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import net.minecraft.block.state.IBlockState;

/** Extra layer for emissive overlays of SOLID blocks. Wave 2 (U8) implements it; inactive until then. */
public final class EmissiveLayerSource implements ExtraLayerSource {
	public static final EmissiveLayerSource INSTANCE = new EmissiveLayerSource();

	private EmissiveLayerSource() {
	}

	@Override
	public boolean active() {
		return false;
	}

	@Override
	public int extraLayerMask(IBlockState rawState, int nativeMask) {
		return 0;
	}
}
