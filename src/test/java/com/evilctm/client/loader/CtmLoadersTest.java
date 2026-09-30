/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.evilctm.client.model.QuadProcessors;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestQuads;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CtmLoadersTest {
	private static final BlockPos POS = new BlockPos(0, 64, 0);
	private PipelineHarness harness;

	@BeforeEach
	void setUp() {
		harness = new PipelineHarness().gate(new FakeGate());
	}

	@AfterEach
	void tearDown() {
		PipelineHarness.reset();
	}

	@Test
	void fixedWithoutTilesIsRejected() {
		assertFalse(harness.addRule("minecraft:optifine/ctm/fixed/no_tiles.properties", "method=fixed\nmatchTiles=blocks/stone\n"));
	}

	@Test
	void fixedAffectsOnlyItsMatchedSprite() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/fixed/stone.properties", "method=fixed\nmatchTiles=blocks/stone\ntiles=minecraft:blocks/fixed_tile\n"));
		harness.publish();
		assertEquals(1, QuadProcessors.current().size());
		IBlockState stone = Blocks.STONE.getDefaultState();
		FakeBlockAccess access = new FakeBlockAccess().set(POS, stone);
		assertEquals(1, QuadProcessors.current().slice(stone, harness.sprite("minecraft:blocks/stone")).processors().length);
		assertEquals(0, QuadProcessors.current().slice(stone, harness.sprite("minecraft:blocks/dirt")).processors().length);

		List<BakedQuad> stoneOut = harness.run(stone, POS, access, BlockRenderLayer.SOLID, EnumFacing.UP,
				List.of(TestQuads.fullFace(EnumFacing.UP, harness.sprite("minecraft:blocks/stone"), -1)));
		List<BakedQuad> dirtIn = List.of(TestQuads.fullFace(EnumFacing.UP, harness.sprite("minecraft:blocks/dirt"), -1));
		List<BakedQuad> dirtOut = harness.run(stone, POS, access, BlockRenderLayer.SOLID, EnumFacing.UP, dirtIn);

		assertSame(harness.sprite("minecraft:blocks/fixed_tile"), stoneOut.get(0).getSprite());
		assertSame(dirtIn, dirtOut);
	}

	@Test
	void ctmWithTwentyTilesIsRejected() {
		assertFalse(harness.addRule("minecraft:optifine/ctm/glass/short.properties", "method=ctm\nmatchBlocks=glass\ntiles=0-19\n"));
	}

	@Test
	void ctmWithFortySevenTilesLoads() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/glass/full.properties", "method=ctm\nmatchBlocks=glass\ntiles=0-46\n"));
	}

	@Test
	void registerAllIsIdempotentAndRegistersTheBaseMethods() {
		CtmLoaders.registerAll();
		CtmLoaders.registerAll();
		for (String method : new String[]{"ctm", "glass", "horizontal", "bookshelf", "vertical", "top", "random", "repeat", "fixed"}) {
			assertTrue(com.evilctm.api.client.CtmLoaderRegistry.get().getLoader(method) != null, method);
		}
	}
}
