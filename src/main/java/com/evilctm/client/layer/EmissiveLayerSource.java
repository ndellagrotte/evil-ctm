/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.impl.client.EmissiveSpriteApiImpl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;

/**
 * Extra CUTOUT_MIPPED layer for emissive companions of blocks that are only SOLID: the companions are translucent-edged
 * cutout geometry and must not render in the SOLID pass.
 */
public final class EmissiveLayerSource implements ExtraLayerSource {
	public static final EmissiveLayerSource INSTANCE = new EmissiveLayerSource();

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
			int cutoutMipped = LayerRouter.bit(BlockRenderLayer.CUTOUT_MIPPED);
			EmissiveSpriteApiImpl api = EmissiveSpriteApiImpl.INSTANCE;
			boolean ctmPairs = EmissiveSpriteApiImpl.hasCtmTilePairs();
			QuadProcessors.Tables tables = ctmPairs ? QuadProcessors.current() : null;
			for (ModelProbe.ProbedSprite probed : ModelProbe.blockSprites(rawState)) {
				if ((probed.layers() & cutoutMipped) == 0) {
					continue;
				}
				TextureAtlasSprite sprite = probed.sprite();
				if (api.getEmissiveSprite(sprite) != null) {
					return cutoutMipped;
				}
				if (tables != null && tables.slice(probed.state(), sprite).processors().length > 0) {
					return cutoutMipped;
				}
			}
			return 0;
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return 0;
		}
	}
}
