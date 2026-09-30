/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import static com.evilctm.client.processor.simple.SpriteProviderTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.evilctm.client.processor.DirectionMaps;
import com.evilctm.client.processor.OrientationMode;
import com.evilctm.testutil.FakeBlockAccess;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class CtmSpriteProviderTest {
	private static final BlockPos ORIGIN = new BlockPos(10, 64, 10);
	private static final EnumFacing FACE = EnumFacing.NORTH;
	private final TextureAtlasSprite[] sprites = sprites("ctm", 47);

	private CtmSpriteProvider provider(boolean innerSeams) {
		return new CtmSpriteProvider(sprites, connectTo(Blocks.STONE), innerSeams, OrientationMode.NONE);
	}

	private FakeBlockAccess world() {
		return new FakeBlockAccess().set(ORIGIN, STONE);
	}

	private int tile(FakeBlockAccess access, boolean innerSeams) {
		BakedQuad quad = quad(FACE);
		return indexOf(sprites, run(provider(innerSeams), quad, access, ORIGIN));
	}

	/** Texture-space neighbour: 0 left, 1 down, 2 right, 3 up for {@link #FACE}. */
	private static BlockPos neighbour(int direction) {
		return ORIGIN.offset(DirectionMaps.getMap(FACE)[0][direction]);
	}

	private static BlockPos corner(int direction) {
		EnumFacing[] d = DirectionMaps.getMap(FACE)[0];
		return ORIGIN.offset(d[direction]).offset(d[(direction + 1) % 4]);
	}

	@Test
	void indexTableIsTheFull47TileTable() {
		assertEquals(256, CtmSpriteProvider.SPRITE_INDEX_MAP.length);
		boolean[] seen = new boolean[47];
		for (int tile : CtmSpriteProvider.SPRITE_INDEX_MAP) {
			seen[tile] = true;
		}
		for (int i = 0; i < 47; i++) {
			assertEquals(true, seen[i], "tile " + i + " unreachable");
		}
	}

	@Test
	void isolatedBlockUsesTileZero() {
		assertEquals(0, tile(world(), false));
	}

	@Test
	void fullSurroundingUsesTheInteriorTile() {
		FakeBlockAccess access = world();
		for (int i = 0; i < 4; i++) {
			access.set(neighbour(i), STONE).set(corner(i), STONE);
		}
		assertEquals(CtmSpriteProvider.SPRITE_INDEX_MAP[0xFF], tile(access, false));
		assertEquals(26, tile(access, false));
	}

	@Test
	void fourSidesWithoutCornersUsesTileFortySix() {
		FakeBlockAccess access = world();
		for (int i = 0; i < 4; i++) {
			access.set(neighbour(i), STONE);
		}
		// bits 0, 2, 4, 6 set, no diagonal bits
		assertEquals(CtmSpriteProvider.SPRITE_INDEX_MAP[0x55], tile(access, false));
		assertEquals(46, tile(access, false));
	}

	@Test
	void rowEnds() {
		// connection to the right only (texture space): left end of a row
		FakeBlockAccess right = world().set(neighbour(2), STONE);
		assertEquals(CtmSpriteProvider.SPRITE_INDEX_MAP[1 << 4], tile(right, false));
		assertEquals(1, tile(right, false));
		// connection to the left only
		FakeBlockAccess left = world().set(neighbour(0), STONE);
		assertEquals(CtmSpriteProvider.SPRITE_INDEX_MAP[1], tile(left, false));
		assertEquals(3, tile(left, false));
		// both: middle of a row
		FakeBlockAccess both = world().set(neighbour(0), STONE).set(neighbour(2), STONE);
		assertEquals(CtmSpriteProvider.SPRITE_INDEX_MAP[0x11], tile(both, false));
		assertEquals(2, tile(both, false));
	}

	@Test
	void columnEnds() {
		FakeBlockAccess up = world().set(neighbour(3), STONE);
		assertEquals(CtmSpriteProvider.SPRITE_INDEX_MAP[1 << 6], tile(up, false));
		assertEquals(36, tile(up, false));
		FakeBlockAccess down = world().set(neighbour(1), STONE);
		assertEquals(CtmSpriteProvider.SPRITE_INDEX_MAP[1 << 2], tile(down, false));
		assertEquals(12, tile(down, false));
	}

	@Test
	void cornerCountsOnlyWhenBothSidesConnect() {
		FakeBlockAccess lonelyCorner = world().set(corner(0), STONE);
		assertEquals(0, tile(lonelyCorner, false));
		FakeBlockAccess withSides = world().set(neighbour(0), STONE).set(neighbour(1), STONE).set(corner(0), STONE);
		FakeBlockAccess withoutCorner = world().set(neighbour(0), STONE).set(neighbour(1), STONE);
		assertEquals(false, tile(withSides, false) == tile(withoutCorner, false));
	}

	@Test
	void innerSeamsBreaksTheConnectionWhenABlockIsInFront() {
		FakeBlockAccess plain = world().set(neighbour(2), STONE);
		assertEquals(1, tile(plain, true));
		// a block in front of the neighbour (towards the viewer) makes the seam visible: no connection
		FakeBlockAccess covered = world().set(neighbour(2), STONE).set(neighbour(2).offset(FACE), STONE);
		assertEquals(0, tile(covered, true));
		assertEquals(1, tile(covered, false));
	}
}
