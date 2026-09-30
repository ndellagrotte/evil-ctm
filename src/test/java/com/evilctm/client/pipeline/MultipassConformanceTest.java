/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.McBootstrap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MultipassConformanceTest {
	static {
		McBootstrap.ensure();
	}

	private static final IBlockState STONE = Blocks.STONE.getDefaultState();
	private static final BlockPos POS = new BlockPos(3, 64, -7);

	@AfterEach
	void reset() {
		PipelineHarness.reset();
	}

	private static String fixedRule(String from, String to) {
		return "method=fixed\nmatchTiles=blocks/" + from + "\ntiles=minecraft:blocks/" + to + "\n";
	}

	private static List<BakedQuad> run(PipelineHarness harness, FakeBlockAccess access, BlockPos pos, EnumFacing face, String sprite) {
		BakedQuad quad = TestQuads.fullFace(face, harness.sprite("minecraft:blocks/" + sprite), -1);
		return harness.run(STONE, pos, access.set(pos, STONE), BlockRenderLayer.SOLID, face, List.of(quad));
	}

	@Test
	void randomThenRepeatChain() {
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		assertTrue(harness.addRule("minecraft:optifine/ctm/m/rand.properties", "method=random\nmatchTiles=blocks/stone\ntiles=minecraft:blocks/ra minecraft:blocks/rb\n"));
		assertTrue(harness.addRule("minecraft:optifine/ctm/m/ra.properties", "method=repeat\nmatchTiles=blocks/ra\nwidth=1\nheight=1\ntiles=minecraft:blocks/xa\n"));
		assertTrue(harness.addRule("minecraft:optifine/ctm/m/rb.properties", "method=repeat\nmatchTiles=blocks/rb\nwidth=1\nheight=1\ntiles=minecraft:blocks/xb\n"));
		harness.publish();
		TextureAtlasSprite xa = harness.sprite("minecraft:blocks/xa");
		TextureAtlasSprite xb = harness.sprite("minecraft:blocks/xb");
		boolean sawA = false;
		boolean sawB = false;
		for (int x = 0; x < 60; x++) {
			BlockPos pos = new BlockPos(x, 64, 0);
			List<BakedQuad> out = run(harness, new FakeBlockAccess(), pos, EnumFacing.NORTH, "stone");
			assertEquals(1, out.size());
			TextureAtlasSprite result = out.get(0).getSprite();
			assertTrue(result == xa || result == xb, "unexpected " + result.getIconName());
			sawA |= result == xa;
			sawB |= result == xb;
		}
		assertTrue(sawA && sawB, "random should reach both branches");
	}

	@Test
	void chainIsBoundedToFourPasses() {
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		String[] names = {"stone", "c1", "c2", "c3", "c4", "c5", "c6"};
		for (int i = 0; i + 1 < names.length; i++) {
			assertTrue(harness.addRule("minecraft:optifine/ctm/m/chain" + i + ".properties", fixedRule(names[i], names[i + 1])));
		}
		harness.publish();
		List<BakedQuad> out = run(harness, new FakeBlockAccess(), POS, EnumFacing.NORTH, "stone");
		assertEquals(1, out.size());
		assertSame(harness.sprite("minecraft:blocks/c4"), out.get(0).getSprite());
	}

	@Test
	void chainShorterThanTheLimitRunsToTheEnd() {
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		assertTrue(harness.addRule("minecraft:optifine/ctm/m/s.properties", fixedRule("stone", "c1")));
		assertTrue(harness.addRule("minecraft:optifine/ctm/m/c.properties", fixedRule("c1", "c2")));
		harness.publish();
		List<BakedQuad> out = run(harness, new FakeBlockAccess(), POS, EnumFacing.NORTH, "stone");
		assertSame(harness.sprite("minecraft:blocks/c2"), out.get(0).getSprite());
	}

	@Test
	void facesSidesFilter() {
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		assertTrue(harness.addRule("minecraft:optifine/ctm/m/f.properties", fixedRule("stone", "fx") + "faces=sides\n"));
		harness.publish();
		TextureAtlasSprite fx = harness.sprite("minecraft:blocks/fx");
		for (EnumFacing face : EnumFacing.Plane.HORIZONTAL) {
			assertSame(fx, run(harness, new FakeBlockAccess(), POS, face, "stone").get(0).getSprite(), face.toString());
		}
		assertSame(harness.sprite("minecraft:blocks/stone"), run(harness, new FakeBlockAccess(), POS, EnumFacing.UP, "stone").get(0).getSprite());
		assertSame(harness.sprite("minecraft:blocks/stone"), run(harness, new FakeBlockAccess(), POS, EnumFacing.DOWN, "stone").get(0).getSprite());
	}

	@Test
	void heightsFilter() {
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		assertTrue(harness.addRule("minecraft:optifine/ctm/m/h.properties", fixedRule("stone", "hx") + "heights=10-20\n"));
		harness.publish();
		TextureAtlasSprite hx = harness.sprite("minecraft:blocks/hx");
		TextureAtlasSprite stone = harness.sprite("minecraft:blocks/stone");
		assertSame(hx, run(harness, new FakeBlockAccess(), new BlockPos(0, 10, 0), EnumFacing.NORTH, "stone").get(0).getSprite());
		assertSame(hx, run(harness, new FakeBlockAccess(), new BlockPos(0, 15, 0), EnumFacing.NORTH, "stone").get(0).getSprite());
		assertSame(hx, run(harness, new FakeBlockAccess(), new BlockPos(0, 20, 0), EnumFacing.NORTH, "stone").get(0).getSprite());
		assertSame(stone, run(harness, new FakeBlockAccess(), new BlockPos(0, 9, 0), EnumFacing.NORTH, "stone").get(0).getSprite());
		assertSame(stone, run(harness, new FakeBlockAccess(), new BlockPos(0, 21, 0), EnumFacing.NORTH, "stone").get(0).getSprite());
	}

	@Test
	void nameRegexFilterAgainstANamedChest() {
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		assertTrue(harness.addRule("minecraft:optifine/ctm/m/n.properties", fixedRule("stone", "nx") + "name=regex:Treasure.*\n"));
		harness.publish();
		TextureAtlasSprite nx = harness.sprite("minecraft:blocks/nx");
		TextureAtlasSprite stone = harness.sprite("minecraft:blocks/stone");

		TileEntityChest named = new TileEntityChest();
		named.setCustomName("Treasure Chest");
		TileEntityChest other = new TileEntityChest();
		other.setCustomName("Junk");
		TileEntityChest unnamed = new TileEntityChest();

		assertSame(nx, run(harness, new FakeBlockAccess().setTileEntity(POS, named), POS, EnumFacing.NORTH, "stone").get(0).getSprite());
		assertSame(stone, run(harness, new FakeBlockAccess().setTileEntity(POS, other), POS, EnumFacing.NORTH, "stone").get(0).getSprite());
		assertSame(stone, run(harness, new FakeBlockAccess().setTileEntity(POS, unnamed), POS, EnumFacing.NORTH, "stone").get(0).getSprite());
		assertSame(stone, run(harness, new FakeBlockAccess(), POS, EnumFacing.NORTH, "stone").get(0).getSprite());
	}
}
