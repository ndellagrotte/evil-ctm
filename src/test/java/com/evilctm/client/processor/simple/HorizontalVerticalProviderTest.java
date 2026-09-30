/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import static com.evilctm.client.processor.simple.SpriteProviderTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.evilctm.client.processor.OrientationMode;
import com.evilctm.testutil.FakeBlockAccess;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class HorizontalVerticalProviderTest {
	private static final EnumFacing FACE = EnumFacing.NORTH;
	/** Looking at a NORTH face, texture right is WEST (x decreases) and texture left is EAST. */
	private static final EnumFacing LEFT = EnumFacing.EAST;
	private static final EnumFacing RIGHT = EnumFacing.WEST;
	private final TextureAtlasSprite[] sprites = sprites("hv", 4);

	private int horizontal(FakeBlockAccess access, BlockPos pos) {
		return indexOf(sprites, run(new HorizontalSpriteProvider(sprites, connectTo(Blocks.STONE), false, OrientationMode.NONE), quad(FACE), access, pos));
	}

	private int vertical(FakeBlockAccess access, BlockPos pos) {
		return indexOf(sprites, run(new VerticalSpriteProvider(sprites, connectTo(Blocks.STONE), false, OrientationMode.NONE), quad(FACE), access, pos));
	}

	@Test
	void horizontalRowHasLeftMiddleAndRightTiles() {
		BlockPos left = new BlockPos(0, 64, 0);
		BlockPos middle = left.offset(RIGHT);
		BlockPos right = middle.offset(RIGHT);
		FakeBlockAccess access = new FakeBlockAccess().set(left, STONE).set(middle, STONE).set(right, STONE);
		assertEquals(0, horizontal(access, left));
		assertEquals(1, horizontal(access, middle));
		assertEquals(2, horizontal(access, right));
		assertEquals(3, horizontal(new FakeBlockAccess().set(left, STONE), left));
	}

	@Test
	void horizontalIgnoresVerticalNeighbours() {
		BlockPos pos = new BlockPos(0, 64, 0);
		FakeBlockAccess access = new FakeBlockAccess().set(pos, STONE).set(pos.up(), STONE).set(pos.down(), STONE);
		assertEquals(3, horizontal(access, pos));
	}

	@Test
	void verticalColumnHasBottomMiddleAndTopTiles() {
		BlockPos bottom = new BlockPos(0, 64, 0);
		BlockPos middle = bottom.up();
		BlockPos top = middle.up();
		FakeBlockAccess access = new FakeBlockAccess().set(bottom, STONE).set(middle, STONE).set(top, STONE);
		assertEquals(0, vertical(access, bottom));
		assertEquals(1, vertical(access, middle));
		assertEquals(2, vertical(access, top));
		assertEquals(3, vertical(new FakeBlockAccess().set(bottom, STONE), bottom));
	}

	@Test
	void verticalIgnoresHorizontalNeighbours() {
		BlockPos pos = new BlockPos(0, 64, 0);
		FakeBlockAccess access = new FakeBlockAccess().set(pos, STONE).set(pos.offset(LEFT), STONE).set(pos.offset(RIGHT), STONE);
		assertEquals(3, vertical(access, pos));
	}
}
