/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.evilctm.client.ctm.CtmDefinition;
import com.evilctm.client.ctm.CtmMcmetaParser;
import com.evilctm.client.ctm.CtmModLoader;
import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestQuads;
import com.google.gson.JsonParser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Every loader method end to end: properties text, the real loader dispatch, holder building and the pipeline. */
class LoaderWiringTest {
	private static final BlockPos POS = new BlockPos(0, 64, 0);
	private static final EnumFacing FACE = EnumFacing.NORTH;

	private PipelineHarness harness;
	private IBlockState stone;

	@BeforeEach
	void setUp() {
		harness = new PipelineHarness().gate(new FakeGate());
		stone = Blocks.STONE.getDefaultState();
	}

	@AfterEach
	void tearDown() {
		PipelineHarness.reset();
	}

	private static String tiles(String prefix, int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append(i == 0 ? "" : " ").append("minecraft:blocks/").append(prefix).append(i);
		}
		return sb.toString();
	}

	private boolean rule(String method, String prefix, int tileCount) {
		return harness.addRule("minecraft:optifine/ctm/w/" + prefix + ".properties",
				"method=" + method + "\nmatchBlocks=stone\ntiles=" + tiles(prefix, tileCount) + "\n");
	}

	private TextureAtlasSprite tile(String prefix, int index) {
		return harness.sprite("minecraft:blocks/" + prefix + index);
	}

	private TextureAtlasSprite run(FakeBlockAccess access) {
		BakedQuad quad = TestQuads.fullFace(FACE, harness.sprite("minecraft:blocks/stone"), -1);
		return harness.run(stone, POS, access.set(POS, stone), BlockRenderLayer.SOLID, FACE, List.of(quad)).get(0).getSprite();
	}

	private FakeBlockAccess row() {
		return new FakeBlockAccess().set(POS.east(), stone).set(POS.west(), stone);
	}

	private FakeBlockAccess column() {
		return new FakeBlockAccess().set(POS.up(), stone).set(POS.down(), stone);
	}

	@Test
	void horizontalConnectsAlongARowOnly() {
		assertTrue(rule("horizontal", "h", 4));
		harness.publish();
		assertSame(tile("h", 1), run(row()));
		assertSame(tile("h", 3), run(column()));
		assertSame(tile("h", 3), run(new FakeBlockAccess()));
	}

	@Test
	void bookshelfIsHorizontal() {
		assertTrue(rule("bookshelf", "b", 4));
		harness.publish();
		assertSame(tile("b", 1), run(row()));
		assertSame(tile("b", 3), run(column()));
	}

	@Test
	void verticalConnectsAlongAColumnOnly() {
		assertTrue(rule("vertical", "v", 4));
		harness.publish();
		assertSame(tile("v", 1), run(column()));
		assertSame(tile("v", 3), run(row()));
	}

	@Test
	void horizontalAndVerticalNeedExactlyFourTiles() {
		for (String method : new String[] {"horizontal", "bookshelf", "vertical"}) {
			assertFalse(rule(method, method + "3_", 3), method);
			assertFalse(rule(method, method + "5_", 5), method);
		}
	}

	@Test
	void horizontalPlusVerticalPrefersTheRow() {
		assertTrue(rule("h+v", "hv", 7));
		harness.publish();
		assertSame(tile("hv", 1), run(row()));
		assertNotSame(tile("hv", 1), run(column()));
		assertNotSame(tile("hv", 3), run(column()));
		assertSame(tile("hv", 3), run(new FakeBlockAccess()));
	}

	@Test
	void verticalPlusHorizontalPrefersTheColumn() {
		assertTrue(rule("v+h", "vh", 7));
		harness.publish();
		assertSame(tile("vh", 1), run(column()));
		assertNotSame(tile("vh", 1), run(row()));
		assertNotSame(tile("vh", 3), run(row()));
		assertSame(tile("vh", 3), run(new FakeBlockAccess()));
	}

	@Test
	void horizontalVerticalCombinationsNeedExactlySevenTiles() {
		for (String method : new String[] {"h+v", "v+h", "horizontal+vertical", "vertical+horizontal"}) {
			assertFalse(rule(method, "x6_", 6), method);
			assertFalse(rule(method, "x8_", 8), method);
		}
	}

	@Test
	void topRetexturesSidesUnderTheSameBlockOnly() {
		assertTrue(rule("top", "t", 1));
		harness.publish();
		assertSame(tile("t", 0), run(new FakeBlockAccess().set(POS.up(), stone)));
		assertSame(harness.sprite("minecraft:blocks/stone"), run(new FakeBlockAccess()));
	}

	@Test
	void topNeedsExactlyOneTile() {
		assertFalse(rule("top", "t2_", 2));
	}

	@Test
	void glassIsCtm() {
		assertTrue(rule("glass", "g", 47));
		harness.publish();
		assertSame(tile("g", 0), run(new FakeBlockAccess()));
		assertFalse(rule("glass", "g20_", 20));
	}

	@Test
	void skipTilesKeepTheOriginalQuad() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/w/skip.properties",
				"method=random\nmatchBlocks=stone\ntiles=<skip> minecraft:blocks/r1\n"));
		harness.publish();
		TextureAtlasSprite original = harness.sprite("minecraft:blocks/stone");
		boolean sawOriginal = false;
		boolean sawTile = false;
		for (int x = 0; x < 60; x++) {
			BlockPos pos = new BlockPos(x, 64, 0);
			BakedQuad quad = TestQuads.fullFace(FACE, original, -1);
			TextureAtlasSprite out = harness.run(stone, pos, new FakeBlockAccess().set(pos, stone), BlockRenderLayer.SOLID, FACE, List.of(quad)).get(0).getSprite();
			assertTrue(out == original || out == harness.sprite("minecraft:blocks/r1"), out.getIconName());
			sawOriginal |= out == original;
			sawTile |= out != original;
		}
		assertTrue(sawOriginal && sawTile);
	}

	@Test
	void unstitchedTileKeepsTheOriginalQuad() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/w/fixed.properties", "method=fixed\nmatchBlocks=stone\ntiles=minecraft:blocks/gone\n"));
		harness.markMissing("minecraft:blocks/gone").publish();
		assertSame(harness.sprite("minecraft:blocks/stone"), run(new FakeBlockAccess()));
	}

	@Test
	void skipOverlayEmitsNothing() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/w/overlay.properties",
				"method=overlay_fixed\nmatchBlocks=stone\ntiles=<skip>\nlayer=cutout_mipped\n"));
		harness.publish();
		BakedQuad quad = TestQuads.fullFace(FACE, harness.sprite("minecraft:blocks/stone"), -1);
		for (BlockRenderLayer layer : BlockRenderLayer.values()) {
			for (BakedQuad out : harness.run(stone, POS, new FakeBlockAccess().set(POS, stone), layer, FACE, List.of(quad))) {
				assertNotSame(harness.missingSprite(), out.getSprite(), layer.toString());
			}
		}
	}

	@Test
	void ctmModDefinitionRetexturesThroughThePipeline() {
		ResourceLocation base = new ResourceLocation("minecraft:blocks/cm");
		CtmDefinition definition = CtmMcmetaParser.parse(base,
				JsonParser.parseString("{\"ctm_version\":1,\"type\":\"CTM\",\"textures\":[\"minecraft:blocks/cm-ctm\"]}").getAsJsonObject(), "test", 0);
		assertTrue(definition != null);
		QuadProcessors.reload(CtmModLoader.createHolders(List.of(definition), id -> harness.sprite(id.toString())));
		LayerRouter.refreshActive();
		TextureAtlasSprite sheet = harness.sprite("minecraft:blocks/cm-ctm");

		BakedQuad quad = TestQuads.fullFace(FACE, harness.sprite(base.toString()), -1);
		List<BakedQuad> connected = harness.run(stone, POS, row().set(POS, stone), BlockRenderLayer.SOLID, FACE, List.of(quad));
		assertTrue(connected.stream().anyMatch(q -> q.getSprite() == sheet), "a connected face uses the CTM sheet");
	}
}
