/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.overlay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.evilctm.client.processor.DirectionMaps;
import com.evilctm.client.processor.simple.CtmSpriteProvider;
import com.evilctm.client.util.OverlayQuads;
import com.evilctm.client.util.QuadUtil;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleOverlayTest {
	static {
		McBootstrap.ensure();
	}

	private static final IBlockState STONE = Blocks.STONE.getDefaultState();
	private static final BlockPos POS = new BlockPos(-3, 70, 12);
	private static final EnumFacing FACE = EnumFacing.NORTH;

	private PipelineHarness harness;

	@BeforeEach
	void setUp() {
		harness = new PipelineHarness().gate(new FakeGate());
	}

	@AfterEach
	void tearDown() {
		PipelineHarness.reset();
	}

	private static String tiles(String prefix, int count) {
		StringBuilder tiles = new StringBuilder();
		for (int i = 0; i < count; i++) {
			tiles.append(i == 0 ? "" : " ").append("minecraft:blocks/").append(prefix).append(i);
		}
		return tiles.toString();
	}

	private boolean add(String method, String prefix, int tileCount, String extra) {
		return harness.addRule("minecraft:optifine/ctm/overlay/" + method.replace('+', '_') + ".properties",
				"method=" + method + "\nmatchBlocks=stone\ntiles=" + tiles(prefix, tileCount) + "\n" + extra);
	}

	private List<BakedQuad> run(FakeBlockAccess world, BlockRenderLayer layer, List<BakedQuad> input) {
		return harness.run(STONE, POS, world.set(POS, STONE), layer, FACE, input);
	}

	private BakedQuad stoneQuad() {
		return TestQuads.fullFace(FACE, harness.sprite("minecraft:blocks/stone"), -1);
	}

	@Test
	void overlayCtmEmitsTheCtmTile() {
		assertTrue(add("overlay_ctm", "c", 47, ""));
		harness.publish();

		List<BakedQuad> isolated = run(new FakeBlockAccess(), BlockRenderLayer.CUTOUT_MIPPED, List.of(stoneQuad()));
		assertEquals(1, isolated.size());
		assertSame(harness.sprite("minecraft:blocks/c" + CtmSpriteProvider.SPRITE_INDEX_MAP[0]), isolated.get(0).getSprite());

		FakeBlockAccess surrounded = new FakeBlockAccess();
		EnumFacing[] dirs = DirectionMaps.getMap(FACE)[0];
		for (int i = 0; i < 4; i++) {
			surrounded.set(POS.offset(dirs[i]), STONE);
			surrounded.set(POS.offset(dirs[i]).offset(dirs[(i + 1) % 4]), STONE);
		}
		List<BakedQuad> out = run(surrounded, BlockRenderLayer.CUTOUT_MIPPED, List.of(stoneQuad()));
		assertEquals(1, out.size());
		assertSame(harness.sprite("minecraft:blocks/c" + CtmSpriteProvider.SPRITE_INDEX_MAP[0xFF]), out.get(0).getSprite());
		assertEquals(-1, out.get(0).getTintIndex());
		assertTrue(OverlayProcessingPredicate.isUnitSquareOnFace(out.get(0)));
	}

	@Test
	void overlayCtmNeedsAtLeast47Tiles() {
		assertFalse(add("overlay_ctm", "c", 46, ""));
	}

	@Test
	void overlayFixedCombinesWithALowerPriorityRetexture() {
		// Lower pack priority: sorted after the overlay.
		assertTrue(harness.addRule("minecraft:optifine/ctm/base/stone.properties", "method=fixed\nmatchBlocks=stone\ntiles=minecraft:blocks/retextured\n"));
		assertTrue(add("overlay_fixed", "f", 1, ""));
		harness.publish();

		BakedQuad input = stoneQuad();
		List<BakedQuad> solid = run(new FakeBlockAccess(), BlockRenderLayer.SOLID, List.of(input));
		assertEquals(1, solid.size());
		assertSame(harness.sprite("minecraft:blocks/retextured"), solid.get(0).getSprite());

		List<BakedQuad> overlay = run(new FakeBlockAccess(), BlockRenderLayer.CUTOUT_MIPPED, List.of(input));
		assertEquals(1, overlay.size());
		assertSame(harness.sprite("minecraft:blocks/f0"), overlay.get(0).getSprite());
	}

	@Test
	void overlayFixedNeedsExactlyOneTile() {
		assertFalse(add("overlay_fixed", "f", 2, ""));
	}

	@Test
	void overlayRandomAcceptsAnyTileCount() {
		assertTrue(add("overlay_random", "r", 3, ""));
		harness.publish();
		List<BakedQuad> out = run(new FakeBlockAccess(), BlockRenderLayer.CUTOUT_MIPPED, List.of(stoneQuad()));
		assertEquals(1, out.size());
		assertTrue(out.get(0).getSprite().getIconName().startsWith("minecraft:blocks/r"));
	}

	@Test
	void overlayRepeatUsesTheRepeatValidator() {
		assertTrue(add("overlay_repeat", "p", 4, "width=2\nheight=2\n"));
		assertFalse(add("overlay_repeat", "p", 3, "width=2\nheight=2\n"));
	}

	@Test
	void horizontalVerticalAndAliasesAreRegistered() {
		assertTrue(add("overlay_horizontal", "h", 4, ""));
		assertFalse(add("overlay_horizontal", "h", 5, ""));
		assertTrue(add("overlay_vertical", "v", 4, ""));
		assertFalse(add("overlay_vertical", "v", 3, ""));
		for (String method : new String[] {"overlay_horizontal+vertical", "overlay_h+v", "overlay_vertical+horizontal", "overlay_v+h"}) {
			assertTrue(add(method, "hv", 7, ""), method);
			assertFalse(add(method, "hv", 6, ""), method);
		}
	}

	@Test
	void layerKeyAppliesToSimpleOverlays() {
		assertFalse(add("overlay_fixed", "f", 1, "layer=solid\n"));
		assertTrue(add("overlay_fixed", "f", 1, "layer=cutout\n"));
		harness.publish();
		assertTrue(run(new FakeBlockAccess(), BlockRenderLayer.CUTOUT_MIPPED, List.of(stoneQuad())).isEmpty());
		List<BakedQuad> out = run(new FakeBlockAccess(), BlockRenderLayer.CUTOUT, List.of(stoneQuad()));
		assertEquals(1, out.size());
		assertSame(harness.sprite("minecraft:blocks/f0"), out.get(0).getSprite());
	}

	@Test
	void nonUnitQuadGetsNoSimpleOverlay() {
		assertTrue(add("overlay_fixed", "f", 1, ""));
		harness.publish();
		BakedQuad full = stoneQuad();
		int[] data = full.getVertexData().clone();
		int stride = full.getFormat().getIntegerSize();
		// Shrink the quad to the left half of the face.
		for (int v = 0; v < 4; v++) {
			float x = Float.intBitsToFloat(data[v * stride]);
			data[v * stride] = Float.floatToRawIntBits(x * 0.5f);
		}
		BakedQuad half = new BakedQuad(data, -1, FACE, full.getSprite(), true, full.getFormat());
		assertTrue(run(new FakeBlockAccess(), BlockRenderLayer.CUTOUT_MIPPED, List.of(half)).isEmpty());
	}

	@Test
	void bakedOverlayQuadsHaveVanillaOrientation() {
		TextureAtlasSprite sprite = TestSprites.create("test:overlay_orientation");
		for (EnumFacing face : EnumFacing.VALUES) {
			BakedQuad baked = OverlayQuads.quad(face, sprite, -1, null);
			BakedQuad reference = TestQuads.fullFace(face, sprite, -1);
			assertSame(DefaultVertexFormats.ITEM, baked.getFormat());
			assertSame(face, baked.getFace());
			assertEquals(-1, baked.getTintIndex());
			assertEquals(0, QuadUtil.getTextureOrientation(baked), face.toString());
			for (int v = 0; v < 4; v++) {
				for (int axis = 0; axis < 3; axis++) {
					assertEquals(QuadUtil.positionComponent(reference, v, axis), QuadUtil.positionComponent(baked, v, axis), 1e-6, face + " vertex " + v + " axis " + axis);
				}
				assertArrayEquals(TestQuads.uvOf(reference, v), TestQuads.uvOf(baked, v), 1e-6f, face + " uv " + v);
			}
			assertTrue(OverlayProcessingPredicate.isUnitSquareOnFace(baked));
		}
	}

	@Test
	void unitSquareCheckRequiresTheFacePlane() {
		TextureAtlasSprite sprite = TestSprites.create("test:overlay_plane");
		BakedQuad up = TestQuads.fullFace(EnumFacing.UP, sprite, -1);
		assertTrue(OverlayProcessingPredicate.isUnitSquareOnFace(up));
		// The same square labelled DOWN lies on y=1, not on the DOWN face plane.
		BakedQuad mislabelled = new BakedQuad(up.getVertexData().clone(), -1, EnumFacing.DOWN, sprite, true, up.getFormat());
		assertFalse(OverlayProcessingPredicate.isUnitSquareOnFace(mislabelled));
	}
}
