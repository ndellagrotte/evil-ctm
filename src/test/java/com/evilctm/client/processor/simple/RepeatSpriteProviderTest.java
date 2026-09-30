/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import static com.evilctm.client.processor.simple.SpriteProviderTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evilctm.client.processor.OrientationMode;
import com.evilctm.client.processor.Symmetry;
import com.evilctm.testutil.FakeBlockAccess;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class RepeatSpriteProviderTest {
	private static final int WIDTH = 3;
	private static final int HEIGHT = 2;
	private final TextureAtlasSprite[] sprites = sprites("rep", WIDTH * HEIGHT);
	private final FakeBlockAccess access = new FakeBlockAccess();

	private int pick(RepeatSpriteProvider provider, EnumFacing face, int x, int y, int z) {
		return indexOf(sprites, run(provider, quad(face), access, new BlockPos(x, y, z)));
	}

	private static int wrap(int value, int size) {
		return ((value % size) + size) % size;
	}

	/** Independent statement of the per-face table. */
	private static int expected(EnumFacing face, int x, int y, int z) {
		int sx;
		int sy;
		switch (face) {
			case DOWN: sx = x; sy = -z - 1; break;
			case UP: sx = x; sy = z; break;
			case NORTH: sx = -x - 1; sy = -y; break;
			case SOUTH: sx = x; sy = -y; break;
			case WEST: sx = z; sy = -y; break;
			default: sx = -z - 1; sy = -y; break;
		}
		return WIDTH * wrap(sy, HEIGHT) + wrap(sx, WIDTH);
	}

	private RepeatSpriteProvider provider() {
		return new RepeatSpriteProvider(sprites, WIDTH, HEIGHT, Symmetry.NONE, OrientationMode.NONE);
	}

	@Test
	void everyFaceFollowsTheTable() {
		RepeatSpriteProvider provider = provider();
		for (EnumFacing face : EnumFacing.values()) {
			for (int x = -4; x <= 4; x++) {
				for (int y = 60; y <= 63; y++) {
					for (int z = -4; z <= 4; z++) {
						assertEquals(expected(face, x, y, z), pick(provider, face, x, y, z), face + " " + x + "," + y + "," + z);
					}
				}
			}
		}
	}

	@Test
	void upFaceTilesLeftToRightAndNorthToSouth() {
		RepeatSpriteProvider provider = provider();
		assertEquals(0, pick(provider, EnumFacing.UP, 0, 64, 0));
		assertEquals(1, pick(provider, EnumFacing.UP, 1, 64, 0));
		assertEquals(2, pick(provider, EnumFacing.UP, 2, 64, 0));
		assertEquals(0, pick(provider, EnumFacing.UP, 3, 64, 0));
		assertEquals(3, pick(provider, EnumFacing.UP, 0, 64, 1));
		assertEquals(0, pick(provider, EnumFacing.UP, 0, 64, 2));
	}

	@Test
	void negativeCoordinatesWrap() {
		RepeatSpriteProvider provider = provider();
		assertEquals(5, pick(provider, EnumFacing.UP, -1, 64, -1));
		assertEquals(2, pick(provider, EnumFacing.UP, -1, 64, -2));
		assertEquals(pick(provider, EnumFacing.UP, 2, 64, 1), pick(provider, EnumFacing.UP, -1, 64, -1));
	}

	@Test
	void oppositeSymmetryMapsEveryFaceToItsPositiveTwin() {
		RepeatSpriteProvider none = provider();
		RepeatSpriteProvider opposite = new RepeatSpriteProvider(sprites, WIDTH, HEIGHT, Symmetry.OPPOSITE, OrientationMode.NONE);
		boolean differsWithoutSymmetry = false;
		for (EnumFacing face : EnumFacing.values()) {
			EnumFacing twin = Symmetry.OPPOSITE.apply(face);
			for (int x = -4; x <= 4; x++) {
				for (int y = 60; y <= 63; y++) {
					for (int z = -4; z <= 4; z++) {
						assertEquals(pick(none, twin, x, y, z), pick(opposite, face, x, y, z), face + " " + x + "," + y + "," + z);
						differsWithoutSymmetry |= pick(none, face, x, y, z) != pick(none, twin, x, y, z);
					}
				}
			}
		}
		assertTrue(differsWithoutSymmetry, "control: opposite faces differ when symmetry is none");
	}
}
