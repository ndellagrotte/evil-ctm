/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import javax.annotation.Nullable;

import com.evilctm.api.client.ProcessingDataProvider;
import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.processor.ProcessingDataKeys;
import com.evilctm.client.processor.Symmetry;
import com.evilctm.client.properties.RandomCtmProperties;
import com.evilctm.client.util.MathUtil;
import com.evilctm.client.util.RandomIndexProvider;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class RandomSpriteProvider implements SpriteProvider {
	protected TextureAtlasSprite[] sprites;
	protected RandomIndexProvider indexProvider;
	protected int randomLoops;
	protected Symmetry symmetry;
	protected boolean linked;

	public RandomSpriteProvider(TextureAtlasSprite[] sprites, RandomIndexProvider indexProvider, int randomLoops, Symmetry symmetry, boolean linked) {
		this.sprites = sprites;
		this.indexProvider = indexProvider;
		this.randomLoops = randomLoops;
		this.symmetry = symmetry;
		this.linked = linked;
	}

	@Override
	@Nullable
	public TextureAtlasSprite getSprite(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, ProcessingDataProvider dataProvider) {
		EnumFacing face = quad.getFace();
		if (face == null) {
			face = EnumFacing.DOWN;
		}

		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		if (linked) {
			Block block = appearanceState.getBlock();
			BlockPos.MutableBlockPos mutablePos = dataProvider.getData(ProcessingDataKeys.MUTABLE_POS).setPos(pos);

			int i = 0;
			do {
				mutablePos.setY(mutablePos.getY() - 1);
				i++;
			} while (i < 3 && block == level.getBlockState(mutablePos).getBlock());
			y = mutablePos.getY() + 1;
		}

		int seed = MathUtil.mix(x, y, z, symmetry.apply(face).ordinal(), randomLoops);
		return sprites[indexProvider.getRandomIndex(seed)];
	}

	public static class Factory implements SpriteProvider.Factory<RandomCtmProperties> {
		@Override
		public SpriteProvider createSpriteProvider(TextureAtlasSprite[] sprites, RandomCtmProperties properties) {
			if (sprites.length <= 1) {
				if (sprites.length == 0) {
					EvilCtmClient.LOGGER.error("Random texture '{}' resolved to no sprites (tiles='{}')", properties.getResourceId(), properties.getSpriteIds());
				}
				// No sprites to pick from (or a single one): fall back to the fixed provider so the
				// random index math never divides by zero.
				return new FixedSpriteProvider(sprites.length == 0 ? null : sprites[0]);
			}
			return new RandomSpriteProvider(sprites, properties.getIndexProviderFactory().createIndexProvider(sprites.length), properties.getRandomLoops(), properties.getSymmetry(), properties.getLinked());
		}

		@Override
		public int getSpriteAmount(RandomCtmProperties properties) {
			return properties.getSpriteIds().size();
		}
	}
}
