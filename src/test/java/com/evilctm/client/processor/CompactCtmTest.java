/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.client.util.QuadUtil;
import com.evilctm.impl.client.ProcessingContextImpl;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompactCtmTest {
	private static final BlockPos POS = new BlockPos(5, 64, 5);
	private static final EnumFacing FACE = EnumFacing.NORTH;
	private static final float EPS = 1e-4f;

	private final TextureAtlasSprite base = TestSprites.create("test:compact_base");
	private final TextureAtlasSprite[] tiles = new TextureAtlasSprite[5];

	CompactCtmTest() {
		for (int i = 0; i < tiles.length; i++) {
			tiles[i] = TestSprites.create("test:compact_tile" + i);
		}
	}

	@AfterEach
	void reset() {
		PipelineHarness.reset();
	}

	private record Outcome(QuadProcessor.ProcessingResult result, List<BakedQuad> pieces, BakedQuad replacement) {
	}

	/** Connections are given as indices into the texture-space directions {0 left, 1 down, 2 right, 3 up}; corners as offsets. */
	private Outcome run(TextureAtlasSprite[] replacements, int[] sides, int[][] corners) {
		EnumFacing[] dirs = DirectionMaps.getMap(FACE)[0];
		Set<BlockPos> connected = new HashSet<>();
		for (int side : sides) {
			connected.add(BlockPos.ORIGIN.offset(dirs[side]));
		}
		for (int[] corner : corners) {
			connected.add(BlockPos.ORIGIN.offset(dirs[corner[0]]).offset(dirs[corner[1]]));
		}
		ConnectionPredicate predicate = (level, pos, appearance, state, other, otherAppearance, otherState, face, sprite) ->
				connected.contains(new BlockPos(other.getX() - pos.getX(), other.getY() - pos.getY(), other.getZ() - pos.getZ()));
		CompactCtmQuadProcessor processor = new CompactCtmQuadProcessor(tiles, (quad, sprite, level, pos, a, s, data) -> true,
				predicate, false, OrientationMode.NONE, replacements);
		ProcessingContextImpl ctx = new ProcessingContextImpl();
		ctx.begin(null, BlockRenderLayer.SOLID);
		try {
			IBlockState stone = Blocks.STONE.getDefaultState();
			BakedQuad quad = TestQuads.fullFace(FACE, base, -1);
			QuadProcessor.ProcessingResult result = processor.processQuad(quad, base, new FakeBlockAccess(), POS, stone, stone, 0L, 0, ctx);
			return new Outcome(result, new ArrayList<>(ctx.getExtraQuads()), ctx.takeReplacement());
		} finally {
			ctx.end();
		}
	}

	private static float minMax(BakedQuad q, boolean u, boolean max) {
		float m = max ? -Float.MAX_VALUE : Float.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			float v = u ? QuadUtil.getU(q, i) : QuadUtil.getV(q, i);
			m = max ? Math.max(m, v) : Math.min(m, v);
		}
		return m;
	}

	private static float pos(BakedQuad q, int vertex, int axis) {
		return QuadUtil.positionComponent(q, vertex, axis);
	}

	private static float area(BakedQuad q) {
		// NORTH faces lie in the XY plane
		float sum = 0;
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) % 4;
			sum += pos(q, i, 0) * pos(q, j, 1) - pos(q, j, 0) * pos(q, i, 1);
		}
		return Math.abs(sum) / 2;
	}

	private static void assertUvInside(BakedQuad q, TextureAtlasSprite tile) {
		assertTrue(minMax(q, true, false) >= tile.getMinU() - EPS && minMax(q, true, true) <= tile.getMaxU() + EPS);
		assertTrue(minMax(q, false, false) >= tile.getMinV() - EPS && minMax(q, false, true) <= tile.getMaxV() + EPS);
	}

	private static void assertAreasSumToOne(List<BakedQuad> pieces) {
		float total = 0;
		for (BakedQuad p : pieces) {
			total += area(p);
		}
		assertEquals(1f, total, EPS);
	}

	@Test
	void isolatedUsesTile0() {
		Outcome o = run(null, new int[0], new int[0][]);
		assertEquals(QuadProcessor.ProcessingResult.STOP, o.result);
		assertSame(tiles[0], o.replacement.getSprite());
		assertUvInside(o.replacement, tiles[0]);
		assertEquals(tiles[0].getMinU(), minMax(o.replacement, true, false), EPS);
		assertEquals(tiles[0].getMaxU(), minMax(o.replacement, true, true), EPS);
	}

	@Test
	void fullyConnectedWithDiagonalsUsesTile1() {
		Outcome o = run(null, new int[]{0, 1, 2, 3}, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}});
		assertEquals(QuadProcessor.ProcessingResult.STOP, o.result);
		assertSame(tiles[1], o.replacement.getSprite());
	}

	@Test
	void leftAndRightUsesTile3() {
		Outcome o = run(null, new int[]{0, 2}, new int[0][]);
		assertEquals(QuadProcessor.ProcessingResult.STOP, o.result);
		assertSame(tiles[3], o.replacement.getSprite());
	}

	@Test
	void upOnlySplitsIntoTile2AndTile0Halves() {
		Outcome o = run(null, new int[]{3}, new int[0][]);
		assertEquals(QuadProcessor.ProcessingResult.DISCARD, o.result);
		assertEquals(2, o.pieces.size());
		assertAreasSumToOne(o.pieces);
		BakedQuad top = null;
		BakedQuad bottom = null;
		for (BakedQuad p : o.pieces) {
			float minY = Math.min(Math.min(pos(p, 0, 1), pos(p, 1, 1)), Math.min(pos(p, 2, 1), pos(p, 3, 1)));
			if (minY > 0.25f) {
				top = p;
			} else {
				bottom = p;
			}
		}
		assertNotNull(top);
		assertNotNull(bottom);
		assertSame(tiles[2], top.getSprite());
		assertSame(tiles[0], bottom.getSprite());
		assertEquals(0.5f, area(top), EPS);
		// the half shows the matching half of its tile
		float midV = (tiles[2].getMinV() + tiles[2].getMaxV()) / 2;
		assertEquals(tiles[2].getMinV(), minMax(top, false, false), EPS);
		assertEquals(midV, minMax(top, false, true), EPS);
		assertEquals(tiles[2].getMinU(), minMax(top, true, false), EPS);
		assertEquals(tiles[2].getMaxU(), minMax(top, true, true), EPS);
		float midV0 = (tiles[0].getMinV() + tiles[0].getMaxV()) / 2;
		assertEquals(midV0, minMax(bottom, false, false), EPS);
		assertEquals(tiles[0].getMaxV(), minMax(bottom, false, true), EPS);
	}

	@Test
	void allSidesWithoutDiagonalsUsesTile4Everywhere() {
		Outcome o = run(null, new int[]{0, 1, 2, 3}, new int[0][]);
		// every quadrant picks tile 4, so the quad is retextured whole instead of split
		assertEquals(QuadProcessor.ProcessingResult.STOP, o.result);
		assertTrue(o.pieces.isEmpty());
		assertSame(tiles[4], o.replacement.getSprite());
		assertUvInside(o.replacement, tiles[4]);
	}

	@Test
	void alternatingCornersGiveFourQuadrants() {
		Outcome o = run(null, new int[]{0, 1, 2, 3}, new int[][]{{0, 1}, {2, 3}});
		assertEquals(QuadProcessor.ProcessingResult.DISCARD, o.result);
		assertEquals(4, o.pieces.size());
		assertAreasSumToOne(o.pieces);
		int tile1 = 0;
		int tile4 = 0;
		for (BakedQuad p : o.pieces) {
			assertEquals(0.25f, area(p), EPS);
			assertTrue(p.getSprite() == tiles[1] || p.getSprite() == tiles[4]);
			assertUvInside(p, p.getSprite());
			if (p.getSprite() == tiles[1]) {
				tile1++;
			} else {
				tile4++;
			}
		}
		assertEquals(2, tile1);
		assertEquals(2, tile4);
	}

	@Test
	void everyPieceCarriesItsTileAndQuadAttributes() {
		Outcome o = run(null, new int[]{3}, new int[0][]);
		for (BakedQuad p : o.pieces) {
			assertTrue(p.getSprite() == tiles[0] || p.getSprite() == tiles[2]);
			assertEquals(FACE, p.getFace());
			assertEquals(-1, p.getTintIndex());
			assertTrue(p.shouldApplyDiffuseLighting());
			assertEquals(4 * TestQuads.STRIDE, p.getVertexData().length);
			// colour lerp of two white vertices stays white
			assertEquals(0xFFFFFFFF, p.getVertexData()[3]);
		}
	}

	@Test
	void ctmReplacementRetexturesWholeQuad() {
		TextureAtlasSprite[] replacements = new TextureAtlasSprite[47];
		TextureAtlasSprite five = TestSprites.create("test:compact_replacement");
		replacements[0] = five;
		Outcome o = run(replacements, new int[0], new int[0][]);
		assertEquals(QuadProcessor.ProcessingResult.NEXT_PASS, o.result);
		assertTrue(o.pieces.isEmpty());
		assertSame(five, o.replacement.getSprite());
		assertEquals(five.getMinU(), minMax(o.replacement, true, false), EPS);
		assertEquals(five.getMaxV(), minMax(o.replacement, false, true), EPS);
	}

	@Test
	void unreplacedIndexFallsBackToCompactTiles() {
		TextureAtlasSprite[] replacements = new TextureAtlasSprite[47];
		replacements[7] = TestSprites.create("test:compact_unused");
		Outcome o = run(replacements, new int[0], new int[0][]);
		assertEquals(QuadProcessor.ProcessingResult.STOP, o.result);
		assertSame(tiles[0], o.replacement.getSprite());
	}

	@Test
	void loadedThroughTheRealLoader() {
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		assertFalse(harness.addRule("minecraft:optifine/ctm/stone/short.properties",
				"method=ctm_compact\nmatchBlocks=stone\nconnect=block\ntiles=0-3\n"));
		assertTrue(harness.addRule("minecraft:optifine/ctm/stone/stone.properties",
				"method=ctm_compact\nmatchBlocks=stone\nconnect=block\ntiles=minecraft:blocks/c0 minecraft:blocks/c1 minecraft:blocks/c2 minecraft:blocks/c3 minecraft:blocks/c4\nctm.0=3\n"));
		harness.publish();
		assertEquals(1, QuadProcessors.current().size());

		IBlockState stone = Blocks.STONE.getDefaultState();
		FakeBlockAccess access = new FakeBlockAccess().set(POS, stone);
		BakedQuad quad = TestQuads.fullFace(EnumFacing.NORTH, harness.sprite("minecraft:blocks/stone"), -1);

		// isolated: connection index 0 is replaced by tile 3 through ctm.0=3
		List<BakedQuad> isolated = harness.run(stone, POS, access, BlockRenderLayer.SOLID, EnumFacing.NORTH, List.of(quad));
		assertEquals(1, isolated.size());
		assertSame(harness.sprite("minecraft:blocks/c3"), isolated.get(0).getSprite());

		// fully surrounded: tile 1
		FakeBlockAccess surrounded = new FakeBlockAccess().set(POS, stone);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				surrounded.set(POS.add(dx, dy, 0), stone);
			}
		}
		List<BakedQuad> full = harness.run(stone, POS, surrounded, BlockRenderLayer.SOLID, EnumFacing.NORTH, List.of(quad));
		assertEquals(1, full.size());
		assertSame(harness.sprite("minecraft:blocks/c1"), full.get(0).getSprite());
	}
}
