/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import com.evilctm.api.client.ProcessingDataProvider;
import com.evilctm.client.processor.ConnectionPredicate;
import com.evilctm.client.processor.DirectionMaps;
import com.evilctm.client.processor.OrientationMode;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HvProvidersTest {
	private static final EnumFacing FACE = EnumFacing.NORTH;
	private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
	private static final EnumFacing[] DIRS = DirectionMaps.getMap(FACE)[0];
	private static final EnumFacing RIGHT = DIRS[2];
	private static final EnumFacing UP = DIRS[3];

	private static TextureAtlasSprite[] sprites;
	private static BakedQuad quad;

	@BeforeAll
	static void init() {
		McBootstrap.ensure();
		sprites = new TextureAtlasSprite[7];
		for (int i = 0; i < 7; i++) {
			sprites[i] = TestSprites.create("hv_" + i);
		}
		quad = TestQuads.fullFace(FACE, sprites[3], -1);
	}

	/** Cell in texture space: x to the right, y up, relative to the origin. */
	private static BlockPos cell(int x, int y) {
		return ORIGIN.offset(RIGHT, x).offset(UP, y);
	}

	private static int index(SpriteProvider provider, BlockPos at) {
		TextureAtlasSprite result = provider.getSprite(quad, sprites[3], null, at, Blocks.STONE.getDefaultState(), Blocks.STONE.getDefaultState(), 0L,
				new ProcessingDataProvider() {
					@SuppressWarnings("unchecked")
					@Override
					public <T> T getData(com.evilctm.api.client.ProcessingDataKey<T> key) {
						return (T) new BlockPos.MutableBlockPos();
					}
				});
		for (int i = 0; i < sprites.length; i++) {
			if (sprites[i] == result) {
				return i;
			}
		}
		return -1;
	}

	private static ConnectionPredicate solid(Set<BlockPos> cells) {
		return new ConnectionPredicate() {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				return cells.contains(otherPos);
			}

			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, EnumFacing face, TextureAtlasSprite quadSprite) {
				return cells.contains(otherPos);
			}
		};
	}

	private static Set<BlockPos> shape(int[]... xy) {
		Set<BlockPos> set = new HashSet<>();
		for (int[] c : xy) {
			set.add(cell(c[0], c[1]));
		}
		return set;
	}

	private static SpriteProvider hv(Set<BlockPos> cells) {
		return new HorizontalVerticalSpriteProvider(sprites, solid(cells), false, OrientationMode.NONE);
	}

	private static SpriteProvider vh(Set<BlockPos> cells) {
		return new VerticalHorizontalSpriteProvider(sprites, solid(cells), false, OrientationMode.NONE);
	}

	@Test
	void rowOfThreeGivesPrimaryTilesInHv() {
		Set<BlockPos> row = shape(new int[] { 0, 0 }, new int[] { 1, 0 }, new int[] { 2, 0 });
		SpriteProvider p = hv(row);
		assertEquals(0, index(p, cell(0, 0)));
		assertEquals(1, index(p, cell(1, 0)));
		assertEquals(2, index(p, cell(2, 0)));
	}

	@Test
	void columnOfThreeGivesSecondaryTilesInHv() {
		Set<BlockPos> col = shape(new int[] { 0, 0 }, new int[] { 0, 1 }, new int[] { 0, 2 });
		SpriteProvider p = hv(col);
		assertEquals(4, index(p, cell(0, 0)));
		assertEquals(5, index(p, cell(0, 1)));
		assertEquals(6, index(p, cell(0, 2)));
	}

	@Test
	void columnOfThreeGivesPrimaryTilesInVh() {
		Set<BlockPos> col = shape(new int[] { 0, 0 }, new int[] { 0, 1 }, new int[] { 0, 2 });
		SpriteProvider p = vh(col);
		assertEquals(0, index(p, cell(0, 0)));
		assertEquals(1, index(p, cell(0, 1)));
		assertEquals(2, index(p, cell(0, 2)));
	}

	@Test
	void rowInVhUsesSecondaryTiles() {
		Set<BlockPos> row = shape(new int[] { 0, 0 }, new int[] { 1, 0 }, new int[] { 2, 0 });
		SpriteProvider p = vh(row);
		assertEquals(4, index(p, cell(0, 0)));
		assertEquals(5, index(p, cell(1, 0)));
		assertEquals(6, index(p, cell(2, 0)));
	}

	@Test
	void lShapeCornerTakesPrimaryDirection() {
		// corner at (0,0) with a neighbour to the right and one above
		Set<BlockPos> l = shape(new int[] { 0, 0 }, new int[] { 1, 0 }, new int[] { 0, 1 });
		SpriteProvider h = hv(l);
		assertEquals(0, index(h, cell(0, 0)));
		assertEquals(6, index(h, cell(0, 1)));
		SpriteProvider v = vh(l);
		assertEquals(0, index(v, cell(0, 0)));
		assertEquals(2, index(v, cell(0, 1)));
		assertEquals(6, index(v, cell(1, 0)));
	}

	@Test
	void isolatedBlockGivesTileThree() {
		Set<BlockPos> one = shape(new int[] { 0, 0 });
		assertEquals(3, index(hv(one), cell(0, 0)));
		assertEquals(3, index(vh(one), cell(0, 0)));
	}

	@Test
	void selectIndexTables() {
		// bits: bit 0 = left/down, bit 1 = right/up
		assertEquals(0, HorizontalVerticalSpriteProvider.selectIndex(2, 3));
		assertEquals(2, HorizontalVerticalSpriteProvider.selectIndex(1, 3));
		assertEquals(5, HorizontalVerticalSpriteProvider.selectIndex(0, 3));
		assertEquals(3, HorizontalVerticalSpriteProvider.selectIndex(0, 0));
		assertEquals(0, VerticalHorizontalSpriteProvider.selectIndex(3, 2));
		assertEquals(4, VerticalHorizontalSpriteProvider.selectIndex(2, 0));
	}
}
