/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.client.util.QuadUtil;
import com.evilctm.impl.client.ProcessingContextImpl;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestProcessors;
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

class ProcessingChainTest {
	private static final BlockPos POS = new BlockPos(3, 64, -7);

	@AfterEach
	void reset() {
		PipelineHarness.reset();
	}

	private static List<BakedQuad> runChain(BakedQuad quad, IBlockState state) {
		ProcessingContextImpl ctx = new ProcessingContextImpl();
		QuadProcessors.Tables tables = QuadProcessors.current();
		ctx.begin(tables, BlockRenderLayer.SOLID);
		try {
			return new ArrayList<>(ProcessingChain.run(quad, state, POS, new FakeBlockAccess().set(POS, state), tables, ctx, 0L));
		} finally {
			ctx.end();
		}
	}

	@Test
	void randomThenRepeatMultipass() {
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		assertTrue(harness.addRule("minecraft:optifine/ctm/stone/stone_random.properties",
				"method=random\nmatchTiles=blocks/stone\ntiles=minecraft:blocks/tile_a\n"));
		assertTrue(harness.addRule("minecraft:optifine/ctm/stone/tile_a_repeat.properties",
				"method=repeat\nmatchTiles=blocks/tile_a\nwidth=1\nheight=1\ntiles=minecraft:blocks/tile_b\n"));
		harness.publish();
		IBlockState stone = Blocks.STONE.getDefaultState();
		BakedQuad quad = TestQuads.fullFace(EnumFacing.NORTH, harness.sprite("minecraft:blocks/stone"), -1);

		List<BakedQuad> out = harness.run(stone, POS, new FakeBlockAccess().set(POS, stone), BlockRenderLayer.SOLID, EnumFacing.NORTH, List.of(quad));

		assertEquals(1, out.size());
		assertSame(harness.sprite("minecraft:blocks/tile_b"), out.get(0).getSprite());
	}

	@Test
	void discardWithExtrasKeepsTheExtras() {
		TextureAtlasSprite a = TestSprites.create("test:chain_a");
		TextureAtlasSprite piece = TestSprites.create("test:chain_piece");
		QuadProcessor splitter = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> {
			context.getExtraQuads().add(QuadUtil.retexture(quad, piece));
			context.getExtraQuads().add(QuadUtil.retexture(quad, piece));
			return QuadProcessor.ProcessingResult.DISCARD;
		};
		QuadProcessors.reload(List.of(TestProcessors.holder(splitter, TestProcessors.sprites(true, a))));

		List<BakedQuad> out = runChain(TestQuads.fullFace(EnumFacing.UP, a, -1), Blocks.STONE.getDefaultState());

		assertEquals(2, out.size());
		assertSame(piece, out.get(0).getSprite());
		assertSame(piece, out.get(1).getSprite());
	}

	@Test
	void stopKeepsTheOriginal() {
		TextureAtlasSprite a = TestSprites.create("test:chain_stop");
		QuadProcessor stopper = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> QuadProcessor.ProcessingResult.STOP;
		QuadProcessors.reload(List.of(TestProcessors.holder(stopper, TestProcessors.sprites(true, a))));
		BakedQuad quad = TestQuads.fullFace(EnumFacing.UP, a, -1);

		List<BakedQuad> out = runChain(quad, Blocks.STONE.getDefaultState());

		assertEquals(1, out.size());
		assertSame(quad, out.get(0));
	}

	@Test
	void nextProcessorFallsThroughAndDropsItsReplacement() {
		TextureAtlasSprite a = TestSprites.create("test:chain_np");
		TextureAtlasSprite b = TestSprites.create("test:chain_np_b");
		QuadProcessor decliner = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> {
			context.replaceQuad(QuadUtil.retexture(quad, b));
			return QuadProcessor.ProcessingResult.NEXT_PROCESSOR;
		};
		QuadProcessors.reload(List.of(TestProcessors.holder(decliner, TestProcessors.sprites(true, a))));
		BakedQuad quad = TestQuads.fullFace(EnumFacing.UP, a, -1);

		List<BakedQuad> out = runChain(quad, Blocks.STONE.getDefaultState());

		assertEquals(1, out.size());
		assertSame(quad, out.get(0));
	}

	@Test
	void cyclicRulesStopAfterFourPasses() {
		TextureAtlasSprite a = TestSprites.create("test:cycle_a");
		TextureAtlasSprite b = TestSprites.create("test:cycle_b");
		AtomicInteger calls = new AtomicInteger();
		QuadProcessor aToB = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> {
			calls.incrementAndGet();
			context.replaceQuad(QuadUtil.retexture(quad, b));
			return QuadProcessor.ProcessingResult.NEXT_PASS;
		};
		QuadProcessor bToA = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> {
			calls.incrementAndGet();
			context.replaceQuad(QuadUtil.retexture(quad, a));
			return QuadProcessor.ProcessingResult.NEXT_PASS;
		};
		QuadProcessors.reload(List.of(
				TestProcessors.holder(aToB, TestProcessors.sprites(true, a)),
				TestProcessors.holder(bToA, TestProcessors.sprites(true, b))));

		List<BakedQuad> out = runChain(TestQuads.fullFace(EnumFacing.UP, a, -1), Blocks.STONE.getDefaultState());

		assertEquals(ProcessingChain.PASSES, calls.get());
		assertEquals(1, out.size());
		assertSame(a, out.get(0).getSprite());
	}
}
