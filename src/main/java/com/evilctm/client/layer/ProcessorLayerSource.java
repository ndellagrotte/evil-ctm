/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import com.evilctm.api.client.LayerTargetingProcessor;
import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.model.QuadProcessors;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Extra layers for {@link LayerTargetingProcessor}s (overlays) that may apply to one of the state's model sprites. */
public final class ProcessorLayerSource implements ExtraLayerSource {
	public static final ProcessorLayerSource INSTANCE = new ProcessorLayerSource();

	private static final EnumFacing[] FACES_AND_NULL = {EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, null};

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
			IBakedModel model = ModelProbe.model(rawState);
			if (model == null) {
				return 0;
			}
			int mask = 0;
			for (EnumFacing face : FACES_AND_NULL) {
				for (BakedQuad quad : ModelProbe.quads(model, rawState, face)) {
					TextureAtlasSprite sprite = quad.getSprite();
					if (sprite == null) {
						continue;
					}
					for (QuadProcessor processor : tables.slice(rawState, sprite).processors()) {
						if (processor instanceof LayerTargetingProcessor targeting) {
							mask |= LayerRouter.bit(targeting.getTargetLayer());
						}
					}
				}
			}
			return mask;
		} catch (RuntimeException e) {
			return 0;
		}
	}
}
