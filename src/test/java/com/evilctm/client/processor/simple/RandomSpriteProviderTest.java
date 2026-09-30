/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import static com.evilctm.client.processor.simple.SpriteProviderTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evilctm.client.processor.Symmetry;
import com.evilctm.client.util.RandomIndexProvider;
import com.evilctm.testutil.FakeBlockAccess;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class RandomSpriteProviderTest {
	private final TextureAtlasSprite[] sprites = sprites("rand", 4);
	private final FakeBlockAccess access = new FakeBlockAccess();

	private RandomSpriteProvider provider(RandomIndexProvider index, int loops, Symmetry symmetry, boolean linked) {
		return new RandomSpriteProvider(sprites, index, loops, symmetry, linked);
	}

	private int pick(RandomSpriteProvider provider, EnumFacing face, BlockPos pos) {
		return indexOf(sprites, run(provider, quad(face), access, pos));
	}

	@Test
	void deterministicForAFixedPosition() {
		RandomSpriteProvider provider = provider(new RandomIndexProvider.Unweighted(4), 0, Symmetry.NONE, false);
		BlockPos pos = new BlockPos(17, 70, -33);
		int first = pick(provider, EnumFacing.NORTH, pos);
		for (int i = 0; i < 50; i++) {
			assertEquals(first, pick(provider, EnumFacing.NORTH, pos));
		}
	}

	@Test
	void weightsHistogramStaysWithinFivePercent() {
		int[] weights = {1, 2, 7};
		TextureAtlasSprite[] three = java.util.Arrays.copyOf(sprites, 3);
		RandomSpriteProvider provider = new RandomSpriteProvider(three, new RandomIndexProvider.WeightedFactory(weights).createIndexProvider(3), 0, Symmetry.NONE, false);
		int[] counts = new int[3];
		int total = 0;
		for (int x = 0; x < 100; x++) {
			for (int z = 0; z < 100; z++) {
				counts[indexOf(three, run(provider, quad(EnumFacing.UP), access, new BlockPos(x, 64, z)))]++;
				total++;
			}
		}
		assertEquals(10000, total);
		double[] expected = {0.1, 0.2, 0.7};
		for (int i = 0; i < 3; i++) {
			assertEquals(expected[i], counts[i] / (double) total, 0.05, "tile " + i);
		}
	}

	private static RandomIndexProvider.Factory parsedWeights(String weights) {
		java.util.Properties properties = new java.util.Properties();
		properties.setProperty("method", "random");
		properties.setProperty("matchTiles", "stone");
		properties.setProperty("tiles", "a b c");
		properties.setProperty("weights", weights);
		com.evilctm.testutil.McBootstrap.ensure();
		com.evilctm.client.properties.RandomCtmProperties props = new com.evilctm.client.properties.RandomCtmProperties(properties,
				new net.minecraft.util.ResourceLocation("minecraft:optifine/ctm/w/w.properties"), new com.evilctm.testutil.TestPack("p"), 0, null, "random");
		props.init();
		return props.getIndexProviderFactory();
	}

	@Test
	void zeroWeightKeepsItsPositionAndIsNeverPicked() {
		RandomIndexProvider index = parsedWeights("10 0 5").createIndexProvider(3);
		int[] counts = new int[3];
		java.util.Random random = new java.util.Random(7);
		for (int i = 0; i < 30000; i++) {
			counts[index.getRandomIndex(random.nextInt())]++;
		}
		assertEquals(0, counts[1]);
		assertEquals(2.0 / 3, counts[0] / 30000.0, 0.02);
		assertEquals(1.0 / 3, counts[2] / 30000.0, 0.02);
	}

	@Test
	void allZeroWeightsDoNotThrow() {
		assertTrue(parsedWeights("0 0") instanceof RandomIndexProvider.UnweightedFactory);
		RandomIndexProvider truncated = new RandomIndexProvider.WeightedFactory(new int[] {0, 0, 5}).createIndexProvider(2);
		for (int i = -5; i < 5; i++) {
			int picked = truncated.getRandomIndex(i);
			assertTrue(picked >= 0 && picked < 2);
		}
	}

	@Test
	void unweightedHistogramIsRoughlyUniform() {
		RandomSpriteProvider provider = provider(new RandomIndexProvider.Unweighted(4), 0, Symmetry.NONE, false);
		int[] counts = new int[4];
		for (int x = 0; x < 100; x++) {
			for (int z = 0; z < 100; z++) {
				counts[pick(provider, EnumFacing.UP, new BlockPos(x, 64, z))]++;
			}
		}
		for (int count : counts) {
			assertEquals(0.25, count / 10000.0, 0.05);
		}
	}

	@Test
	void oppositeSymmetryGivesTheSameTileOnNorthAndSouth() {
		RandomSpriteProvider provider = provider(new RandomIndexProvider.Unweighted(4), 0, Symmetry.OPPOSITE, false);
		boolean differsWithoutSymmetry = false;
		RandomSpriteProvider plain = provider(new RandomIndexProvider.Unweighted(4), 0, Symmetry.NONE, false);
		for (int x = 0; x < 40; x++) {
			for (int z = 0; z < 40; z++) {
				BlockPos pos = new BlockPos(x, 64, z);
				assertEquals(pick(provider, EnumFacing.NORTH, pos), pick(provider, EnumFacing.SOUTH, pos));
				assertEquals(pick(provider, EnumFacing.UP, pos), pick(provider, EnumFacing.DOWN, pos));
				assertEquals(pick(provider, EnumFacing.EAST, pos), pick(provider, EnumFacing.WEST, pos));
				differsWithoutSymmetry |= pick(plain, EnumFacing.NORTH, pos) != pick(plain, EnumFacing.SOUTH, pos);
			}
		}
		assertTrue(differsWithoutSymmetry, "control: faces differ when symmetry is none");
	}

	@Test
	void linkedStackSharesOneTile() {
		RandomSpriteProvider provider = provider(new RandomIndexProvider.Unweighted(4), 0, Symmetry.NONE, true);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				BlockPos low = new BlockPos(x, 64, z);
				FakeBlockAccess world = new FakeBlockAccess().set(low, STONE).set(low.up(), STONE);
				int a = indexOf(sprites, run(provider, quad(EnumFacing.NORTH), world, low));
				int b = indexOf(sprites, run(provider, quad(EnumFacing.NORTH), world, low.up()));
				assertEquals(a, b, "at " + x + "," + z);
			}
		}
	}

	@Test
	void unlinkedStackUsuallyDiffers() {
		RandomSpriteProvider provider = provider(new RandomIndexProvider.Unweighted(4), 0, Symmetry.NONE, false);
		int different = 0;
		for (int x = 0; x < 30; x++) {
			BlockPos low = new BlockPos(x, 64, 0);
			if (pick(provider, EnumFacing.NORTH, low) != pick(provider, EnumFacing.NORTH, low.up())) {
				different++;
			}
		}
		assertTrue(different > 0);
	}

	@Test
	void randomLoopsChangesTheChoice() {
		RandomSpriteProvider zero = provider(new RandomIndexProvider.Unweighted(4), 0, Symmetry.NONE, false);
		RandomSpriteProvider three = provider(new RandomIndexProvider.Unweighted(4), 3, Symmetry.NONE, false);
		int changed = 0;
		for (int x = 0; x < 50; x++) {
			BlockPos pos = new BlockPos(x, 64, 5);
			if (pick(zero, EnumFacing.NORTH, pos) != pick(three, EnumFacing.NORTH, pos)) {
				changed++;
			}
		}
		assertNotEquals(0, changed);
	}
}
