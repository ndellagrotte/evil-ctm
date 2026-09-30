/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.impl.client.EmissiveSpriteApiImpl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;

/**
 * Extra CUTOUT_MIPPED layer for emissive companions of blocks that are only SOLID: the companions are translucent-edged
 * cutout geometry and must not render in the SOLID pass.
 */
public final class EmissiveLayerSource implements ExtraLayerSource {
	public static final EmissiveLayerSource INSTANCE = new EmissiveLayerSource();

	private static final EnumFacing[] FACES_AND_NULL = {EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, null};

	private EmissiveLayerSource() {
	}

	@Override
	public boolean active() {
		try {
			return EvilCtmConfig.INSTANCE.emissiveTextures.get() && EmissiveSpriteApiImpl.hasAny();
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	@Override
	public int extraLayerMask(IBlockState rawState, int nativeMask) {
		if (nativeMask != LayerRouter.bit(BlockRenderLayer.SOLID)) {
			return 0;
		}
		try {
			IBakedModel model = ModelProbe.model(rawState);
			if (model == null) {
				return 0;
			}
			EmissiveSpriteApiImpl api = EmissiveSpriteApiImpl.INSTANCE;
			boolean ctmPairs = EmissiveSpriteApiImpl.hasCtmTilePairs();
			QuadProcessors.Tables tables = ctmPairs ? QuadProcessors.current() : null;
			for (EnumFacing face : FACES_AND_NULL) {
				for (BakedQuad quad : ModelProbe.quads(model, rawState, face)) {
					TextureAtlasSprite sprite = quad.getSprite();
					if (sprite == null) {
						continue;
					}
					if (api.getEmissiveSprite(sprite) != null) {
						return LayerRouter.bit(BlockRenderLayer.CUTOUT_MIPPED);
					}
					if (tables != null && tables.slice(rawState, sprite).processors().length > 0) {
						return LayerRouter.bit(BlockRenderLayer.CUTOUT_MIPPED);
					}
				}
			}
			return 0;
		} catch (RuntimeException e) {
			return 0;
		}
	}
}
