/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.ctm.CtmRenderLayerRouter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;

/** Extra layers requested by CTM-mod {@code layer} metadata; wraps {@link CtmRenderLayerRouter#allowAdditionalLayer}. */
public final class CtmModLayerSource implements ExtraLayerSource {
	public static final CtmModLayerSource INSTANCE = new CtmModLayerSource();

	private CtmModLayerSource() {
	}

	@Override
	public boolean active() {
		return EvilCtmConfig.INSTANCE.ctmModTextures.get() && CtmRenderLayerRouter.active();
	}

	@Override
	public int extraLayerMask(IBlockState rawState, int nativeMask) {
		try {
			int mask = 0;
			for (BlockRenderLayer layer : LayerRouter.LAYERS) {
				if ((nativeMask & LayerRouter.bit(layer)) == 0 && CtmRenderLayerRouter.allowAdditionalLayer(rawState, layer)) {
					mask |= LayerRouter.bit(layer);
				}
			}
			return mask;
		} catch (RuntimeException e) {
			return 0;
		}
	}
}
