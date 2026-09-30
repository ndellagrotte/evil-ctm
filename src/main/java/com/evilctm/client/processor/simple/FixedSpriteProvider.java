/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import javax.annotation.Nullable;

import com.evilctm.api.client.ProcessingDataProvider;
import com.evilctm.client.properties.BaseCtmProperties;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class FixedSpriteProvider implements SpriteProvider {
	protected TextureAtlasSprite sprite;

	public FixedSpriteProvider(TextureAtlasSprite sprite) {
		this.sprite = sprite;
	}

	@Override
	@Nullable
	public TextureAtlasSprite getSprite(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, ProcessingDataProvider dataProvider) {
		return this.sprite;
	}

	public static class Factory implements SpriteProvider.Factory<BaseCtmProperties> {
		@Override
		public SpriteProvider createSpriteProvider(TextureAtlasSprite[] sprites, BaseCtmProperties properties) {
			return new FixedSpriteProvider(sprites[0]);
		}

		@Override
		public int getSpriteAmount(BaseCtmProperties properties) {
			return 1;
		}
	}
}
