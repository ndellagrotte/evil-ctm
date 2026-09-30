/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import com.evilctm.api.client.LayerTargetingProcessor;
import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.model.QuadProcessors;
import net.minecraft.block.state.IBlockState;

/**
 * Extra layers for {@link LayerTargetingProcessor}s (overlays) that may apply to a sprite of any of the block's models
 * ({@link ModelProbe#blockSprites}), granted only where the model returns that sprite in the target layer.
 */
public final class ProcessorLayerSource implements ExtraLayerSource {
	public static final ProcessorLayerSource INSTANCE = new ProcessorLayerSource();

	private ProcessorLayerSource() {
	}

	@Override
	public boolean active() {
		return QuadProcessors.current().anyLayerTargeting;
	}

	@Override
	public int extraLayerMask(IBlockState rawState, int nativeMask) {
		try {
			QuadProcessors.Tables tables = QuadProcessors.current();
			if (!tables.anyLayerTargeting) {
				return 0;
			}
			int mask = 0;
			for (ModelProbe.ProbedSprite probed : ModelProbe.blockSprites(rawState)) {
				for (QuadProcessor processor : tables.slice(probed.state(), probed.sprite()).processors()) {
					if (processor instanceof LayerTargetingProcessor targeting) {
						// The overlay runs on base quads in the target pass, so the model must return the sprite there.
						mask |= LayerRouter.bit(targeting.getTargetLayer()) & probed.layers();
					}
				}
			}
			return mask;
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return 0;
		}
	}
}
