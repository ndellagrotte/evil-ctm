/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.api.client;

import net.minecraft.util.BlockRenderLayer;

/**
 * A processor that emits quads into one specific render layer (overlays). The layer router adds that layer to every
 * block state the processor may apply to.
 *
 * <p>Contract: when {@code ctx.currentLayer() != getTargetLayer()}, {@code processQuad} returns
 * {@link QuadProcessor.ProcessingResult#NEXT_PROCESSOR} immediately, without neighbour lookups.</p>
 */
public interface LayerTargetingProcessor extends QuadProcessor {
	BlockRenderLayer getTargetLayer();
}
