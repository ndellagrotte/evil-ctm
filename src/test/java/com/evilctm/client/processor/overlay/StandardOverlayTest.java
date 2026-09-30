/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.layer.ModelProbe;
import com.evilctm.client.processor.DirectionMaps;
import com.evilctm.client.util.OverlayQuads;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestQuads;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardOverlayTest {
	static {
		McBootstrap.ensure();
	}

	private static final IBlockState STONE = Blocks.STONE.getDefaultState();
	private static final IBlockState DIRT = Blocks.DIRT.getDefaultState();
	private static final BlockPos POS = new BlockPos(5, 64, 5);
	private static final EnumFacing FACE = EnumFacing.NORTH;
	/** {left, down, right, up} of the NORTH face. */
	private static final EnumFacing[] DIRS = DirectionMaps.getMap(FACE)[0];
	private static final String RULE_ID = "minecraft:optifine/ctm/overlay/stone.properties";

	private PipelineHarness harness;

	/** Counts every block lookup that is not the block being rendered. */
	static final class CountingAccess extends FakeBlockAccess {
		final AtomicInteger neighbourLookups = new AtomicInteger();

		@Override
		public IBlockState getBlockState(BlockPos pos) {
			if (!POS.equals(pos)) {
				neighbourLookups.incrementAndGet();
			}
			return super.getBlockState(pos);
		}
	}

	static final class StoneModel implements IBakedModel {
		private final List<BakedQuad> quads;

		StoneModel(BakedQuad quad) {
			quads = List.of(quad);
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
			return side == quads.get(0).getFace() ? quads : Collections.emptyList();
		}

		@Override
		public boolean isAmbientOcclusion() {
			return true;
		}

		@Override
		public boolean isGui3d() {
			return true;
		}

		@Override
		public boolean isBuiltInRenderer() {
			return false;
		}

		@Override
		public TextureAtlasSprite getParticleTexture() {
			return quads.get(0).getSprite();
		}

		@Override
		public ItemOverrideList getOverrides() {
			return ItemOverrideList.NONE;
		}
	}

	@BeforeEach
	void setUp() {
		harness = new PipelineHarness().gate(new FakeGate());
	}

	@AfterEach
	void tearDown() {
		OverlayQuads.setBlockColorsForTests(null);
		ModelProbe.setModelLookupForTests(null);
		PipelineHarness.reset();
	}

	private static String rule(String extra) {
		StringBuilder tiles = new StringBuilder();
		for (int i = 0; i < 17; i++) {
			tiles.append(i == 0 ? "" : " ").append("minecraft:blocks/ov").append(i);
		}
		return "method=overlay\nmatchBlocks=stone\nconnectBlocks=dirt\ntiles=" + tiles + "\n" + extra;
	}

	private void load(String extra) {
		assertTrue(harness.addRule(RULE_ID, rule(extra)));
		harness.publish();
	}

	private BakedQuad stoneQuad(EnumFacing face) {
		return TestQuads.fullFace(face, harness.sprite("minecraft:blocks/stone"), -1);
	}

	private List<BakedQuad> run(FakeBlockAccess world, BlockRenderLayer layer) {
		return harness.run(STONE, POS, world.set(POS, STONE), layer, FACE, List.of(stoneQuad(FACE)));
	}

	/** Tile numbers of the overlay quads in {@code out}, in emission order. */
	private static List<Integer> tiles(List<BakedQuad> out) {
		List<Integer> tiles = new ArrayList<>();
		for (BakedQuad quad : out) {
			String name = quad.getSprite().getIconName();
			assertTrue(name.startsWith("minecraft:blocks/ov"), "unexpected quad " + name);
			tiles.add(Integer.parseInt(name.substring("minecraft:blocks/ov".length())));
		}
		return tiles;
	}

	private static BlockPos side(int i) {
		return POS.offset(DIRS[i]);
	}

	private static BlockPos corner(int i, int j) {
		return POS.offset(DIRS[i]).offset(DIRS[j]);
	}

	@Test
	void dirtToTheLeftOnlyGivesTheLeftEdge() {
		load("");
		List<BakedQuad> out = run(new FakeBlockAccess().set(side(0), DIRT), BlockRenderLayer.CUTOUT_MIPPED);
		assertEquals(List.of(9), tiles(out));
		BakedQuad overlay = out.get(0);
		assertSame(FACE, overlay.getFace());
		assertEquals(-1, overlay.getTintIndex());
	}

	@Test
	void leftEdgeGetsTheCornerWhereTheAdjacentSideHasTheSameOverlay() {
		load("");
		// Stone below (same overlay) and dirt on the down-right diagonal: corner D+R (tile 0).
		FakeBlockAccess world = new FakeBlockAccess().set(side(0), DIRT).set(side(1), STONE).set(corner(1, 2), DIRT);
		assertEquals(List.of(9, 0), tiles(run(world, BlockRenderLayer.CUTOUT_MIPPED)));

		// Without the same overlay beside it, the diagonal dirt adds nothing.
		FakeBlockAccess bare = new FakeBlockAccess().set(side(0), DIRT).set(corner(1, 2), DIRT);
		assertEquals(List.of(9), tiles(run(bare, BlockRenderLayer.CUTOUT_MIPPED)));
	}

	@Test
	void sameOverlayOnTheOppositeSideEnablesBothCorners() {
		load("");
		FakeBlockAccess world = new FakeBlockAccess().set(side(0), DIRT).set(side(2), STONE)
				.set(corner(1, 2), DIRT).set(corner(2, 3), DIRT);
		assertEquals(List.of(9, 0, 14), tiles(run(world, BlockRenderLayer.CUTOUT_MIPPED)));
	}

	@Test
	void surroundedGivesTheFullTile() {
		load("");
		FakeBlockAccess world = new FakeBlockAccess();
		for (int i = 0; i < 4; i++) {
			world.set(side(i), DIRT);
		}
		assertEquals(List.of(8), tiles(run(world, BlockRenderLayer.CUTOUT_MIPPED)));
	}

	@Test
	void twoAdjacentSidesAndOppositeSides() {
		load("");
		assertEquals(List.of(4), tiles(run(new FakeBlockAccess().set(side(0), DIRT).set(side(1), DIRT), BlockRenderLayer.CUTOUT_MIPPED)));
		assertEquals(List.of(9, 7), tiles(run(new FakeBlockAccess().set(side(0), DIRT).set(side(2), DIRT), BlockRenderLayer.CUTOUT_MIPPED)));
		assertEquals(List.of(1, 15), tiles(run(new FakeBlockAccess().set(side(1), DIRT).set(side(3), DIRT), BlockRenderLayer.CUTOUT_MIPPED)));
	}

	@Test
	void cornerOnlyNeedsAnAdjacentSameOverlay() {
		load("");
		// No side applies; stone to the left and dirt on the left-down diagonal: corner L+D (tile 2).
		FakeBlockAccess world = new FakeBlockAccess().set(side(0), STONE).set(corner(0, 1), DIRT);
		assertEquals(List.of(2), tiles(run(world, BlockRenderLayer.CUTOUT_MIPPED)));
	}

	@Test
	void opaqueBlockInFrontOfTheNeighbourBlocksTheOverlay() {
		load("");
		FakeBlockAccess world = new FakeBlockAccess();
		for (int i = 0; i < 4; i++) {
			world.set(side(i), DIRT).set(side(i).offset(FACE), STONE);
		}
		assertTrue(run(world, BlockRenderLayer.CUTOUT_MIPPED).isEmpty());
	}

	@Test
	void neighbourThatConnectsOrIsNotListedAppliesNothing() {
		load("");
		assertTrue(run(new FakeBlockAccess().set(side(0), STONE), BlockRenderLayer.CUTOUT_MIPPED).isEmpty());
		assertTrue(run(new FakeBlockAccess().set(side(0), Blocks.COBBLESTONE.getDefaultState()), BlockRenderLayer.CUTOUT_MIPPED).isEmpty());
	}

	@Test
	void defaultLayerIsCutoutMippedAndTheBaseQuadIsUntouched() {
		load("");
		FakeBlockAccess world = new FakeBlockAccess().set(side(0), DIRT);
		List<BakedQuad> input = List.of(stoneQuad(FACE));
		List<BakedQuad> solid = harness.run(STONE, POS, world.set(POS, STONE), BlockRenderLayer.SOLID, FACE, input);
		assertSame(input, solid);
		assertTrue(run(world, BlockRenderLayer.CUTOUT).isEmpty());
		assertTrue(run(world, BlockRenderLayer.TRANSLUCENT).isEmpty());
		assertEquals(List.of(9), tiles(run(world, BlockRenderLayer.CUTOUT_MIPPED)));
	}

	@Test
	void translucentLayerEmitsOnlyInTheTranslucentPassWithoutNeighbourLookupsElsewhere() {
		load("layer=translucent\n");
		CountingAccess world = new CountingAccess();
		world.set(side(0), DIRT);

		List<BakedQuad> input = List.of(stoneQuad(FACE));
		List<BakedQuad> solid = harness.run(STONE, POS, world.set(POS, STONE), BlockRenderLayer.SOLID, FACE, input);
		assertSame(input, solid);
		assertTrue(run(world, BlockRenderLayer.CUTOUT_MIPPED).isEmpty());
		assertEquals(0, world.neighbourLookups.get(), "non-target passes must not read neighbours");

		assertEquals(List.of(9), tiles(run(world, BlockRenderLayer.TRANSLUCENT)));
		assertTrue(world.neighbourLookups.get() > 0);
	}

	@Test
	void cutoutLayer() {
		load("layer=cutout\n");
		FakeBlockAccess world = new FakeBlockAccess().set(side(0), DIRT);
		assertEquals(List.of(9), tiles(run(world, BlockRenderLayer.CUTOUT)));
		assertTrue(run(world, BlockRenderLayer.CUTOUT_MIPPED).isEmpty());
	}

	@Test
	void solidOrUnknownLayerRejectsTheRule() {
		assertFalse(harness.addRule(RULE_ID, rule("layer=solid\n")));
		assertFalse(harness.addRule(RULE_ID, rule("layer=sparkly\n")));
		assertTrue(harness.addRule(RULE_ID, rule("layer=CUTOUT_MIPPED\n")));
	}

	@Test
	void fewerThan17TilesRejectsTheRule() {
		assertFalse(harness.addRule(RULE_ID, "method=overlay\nmatchBlocks=stone\ntiles=minecraft:blocks/a minecraft:blocks/b\n"));
	}

	@Test
	void tintBlockBakesItsColour() {
		BlockColors colors = new BlockColors();
		AtomicInteger seenTintIndex = new AtomicInteger(-100);
		colors.registerBlockColorHandler((state, world, pos, tintIndex) -> {
			seenTintIndex.set(tintIndex);
			return 0x336699;
		}, Blocks.GRASS);
		OverlayQuads.setBlockColorsForTests(() -> colors);
		load("tintBlock=grass\ntintIndex=2\n");

		List<BakedQuad> out = run(new FakeBlockAccess().set(side(0), DIRT), BlockRenderLayer.CUTOUT_MIPPED);
		assertEquals(List.of(9), tiles(out));
		BakedQuad quad = out.get(0);
		assertEquals(-1, quad.getTintIndex());
		assertEquals(2, seenTintIndex.get());
		int stride = quad.getFormat().getIntegerSize();
		int color = quad.getFormat().getColorOffset() / 4;
		for (int v = 0; v < 4; v++) {
			assertEquals(0xFF996633, quad.getVertexData()[v * stride + color], "vertex " + v);
		}
	}

	@Test
	void tintBlockWithoutTintIndexUsesIndexZero() {
		BlockColors colors = new BlockColors();
		AtomicInteger seenTintIndex = new AtomicInteger(-100);
		colors.registerBlockColorHandler((state, world, pos, tintIndex) -> {
			seenTintIndex.set(tintIndex);
			return 0x000000;
		}, Blocks.GRASS);
		OverlayQuads.setBlockColorsForTests(() -> colors);
		load("tintBlock=minecraft:grass\n");
		run(new FakeBlockAccess().set(side(0), DIRT), BlockRenderLayer.CUTOUT_MIPPED);
		assertEquals(0, seenTintIndex.get());
	}

	@Test
	void noTintBlockMeansUntintedEvenWithATintIndex() {
		load("tintIndex=0\n");
		List<BakedQuad> out = run(new FakeBlockAccess().set(side(0), DIRT), BlockRenderLayer.CUTOUT_MIPPED);
		BakedQuad quad = out.get(0);
		assertEquals(-1, quad.getTintIndex());
		int stride = quad.getFormat().getIntegerSize();
		int color = quad.getFormat().getColorOffset() / 4;
		for (int v = 0; v < 4; v++) {
			assertEquals(0xFFFFFFFF, quad.getVertexData()[v * stride + color]);
		}
	}

	@Test
	void nonUnitQuadGetsNoOverlay() {
		load("");
		EnumFacing up = EnumFacing.UP;
		EnumFacing[] upDirs = DirectionMaps.getMap(up)[0];
		FakeBlockAccess world = new FakeBlockAccess();
		for (EnumFacing dir : upDirs) {
			world.set(POS.offset(dir), DIRT);
		}
		world.set(POS, STONE);

		BakedQuad full = stoneQuad(up);
		assertEquals(List.of(8), tiles(harness.run(STONE, POS, world, BlockRenderLayer.CUTOUT_MIPPED, up, List.of(full))));

		int[] data = full.getVertexData().clone();
		int stride = full.getFormat().getIntegerSize();
		for (int v = 0; v < 4; v++) {
			data[v * stride + 1] = Float.floatToRawIntBits(0.5f);
		}
		BakedQuad slabTop = new BakedQuad(data, -1, up, full.getSprite(), true, full.getFormat());
		assertTrue(harness.run(STONE, POS, world, BlockRenderLayer.CUTOUT_MIPPED, up, List.of(slabTop)).isEmpty());
	}

	@Test
	void layerRouterGrantsTheOverlayLayerOnlyOnTheFastPath() {
		FakeGate gate = new FakeGate();
		harness.gate(gate);
		IBakedModel model = new StoneModel(stoneQuad(FACE));
		ModelProbe.setModelLookupForTests(state -> model);
		load("");

		assertTrue(LayerRouter.allowExtraLayer(STONE, BlockRenderLayer.CUTOUT_MIPPED));
		assertFalse(LayerRouter.allowExtraLayer(STONE, BlockRenderLayer.TRANSLUCENT));
		assertFalse(LayerRouter.allowExtraLayer(STONE, BlockRenderLayer.SOLID));
		gate.fastPath(false);
		assertFalse(LayerRouter.allowExtraLayer(STONE, BlockRenderLayer.CUTOUT_MIPPED));
	}
}
