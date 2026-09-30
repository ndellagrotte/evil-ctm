/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.ctm.CtmRenderLayerRouter;
import net.minecraft.block.state.IBlockState;

/** Extra layers requested by CTM-mod {@code layer} metadata; delegates to {@link CtmRenderLayerRouter}; guards live in {@link LayerRouter}. */
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
			return CtmRenderLayerRouter.extraLayerMask(rawState, nativeMask);
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return 0;
		}
	}
}
