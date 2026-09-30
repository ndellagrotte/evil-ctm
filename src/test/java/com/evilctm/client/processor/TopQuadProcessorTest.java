/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.impl.client.ProcessingContextImpl;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class TopQuadProcessorTest {
	static {
		McBootstrap.ensure();
	}

	private static final BlockPos POS = new BlockPos(4, 64, 4);
	private final IBlockState stone = Blocks.STONE.getDefaultState();
	private final TextureAtlasSprite top = TestSprites.create("test:top_tile");
	private final TextureAtlasSprite base = TestSprites.create("test:top_base");

	private TopQuadProcessor processor(boolean innerSeams) {
		ConnectionPredicate connect = (level, pos, a, s, otherPos, oa, os, face, sprite) -> os.getBlock() == Blocks.STONE;
		return new TopQuadProcessor(new TextureAtlasSprite[] {top}, (q, sp, l, p, a, s, d) -> true, connect, innerSeams);
	}

	private QuadProcessor.ProcessingResult run(TopQuadProcessor processor, EnumFacing face, FakeBlockAccess access, ProcessingContextImpl context) {
		BakedQuad quad = TestQuads.fullFace(face, base, -1);
		return processor.processQuad(quad, base, access, POS, stone, stone, 0L, 0, context);
	}

	@Test
	void sideFaceUnderAConnectingBlockGetsTheTopTile() {
		FakeBlockAccess access = new FakeBlockAccess().set(POS, stone).set(POS.up(), stone);
		for (EnumFacing face : EnumFacing.Plane.HORIZONTAL) {
			ProcessingContextImpl context = new ProcessingContextImpl();
			assertEquals(QuadProcessor.ProcessingResult.NEXT_PASS, run(processor(false), face, access, context), face.toString());
			BakedQuad replacement = context.takeReplacement();
			assertSame(top, replacement.getSprite());
			assertEquals(face, replacement.getFace());
		}
	}

	@Test
	void noBlockAboveLeavesTheQuadAlone() {
		FakeBlockAccess access = new FakeBlockAccess().set(POS, stone);
		ProcessingContextImpl context = new ProcessingContextImpl();
		assertEquals(QuadProcessor.ProcessingResult.NEXT_PROCESSOR, run(processor(false), EnumFacing.NORTH, access, context));
		assertNull(context.takeReplacement());
	}

	@Test
	void topAndBottomFacesAreNeverReplacedForAVerticalBlock() {
		FakeBlockAccess access = new FakeBlockAccess().set(POS, stone).set(POS.up(), stone);
		assertEquals(QuadProcessor.ProcessingResult.NEXT_PROCESSOR, run(processor(false), EnumFacing.UP, access, new ProcessingContextImpl()));
		assertEquals(QuadProcessor.ProcessingResult.NEXT_PROCESSOR, run(processor(false), EnumFacing.DOWN, access, new ProcessingContextImpl()));
	}

	@Test
	void innerSeamsBreaksTheConnectionWhenTheFaceIsCovered() {
		FakeBlockAccess access = new FakeBlockAccess().set(POS, stone).set(POS.up(), stone).set(POS.up().north(), stone);
		assertEquals(QuadProcessor.ProcessingResult.NEXT_PROCESSOR, run(processor(true), EnumFacing.NORTH, access, new ProcessingContextImpl()));
		assertEquals(QuadProcessor.ProcessingResult.NEXT_PASS, run(processor(false), EnumFacing.NORTH, access, new ProcessingContextImpl()));
	}

	@Test
	void quadWithoutAFaceIsSkipped() {
		FakeBlockAccess access = new FakeBlockAccess().set(POS, stone).set(POS.up(), stone);
		BakedQuad faceless = new BakedQuad(TestQuads.fullFace(EnumFacing.NORTH, base, -1).getVertexData(), -1, null, base, true, net.minecraft.client.renderer.vertex.DefaultVertexFormats.ITEM);
		assertEquals(QuadProcessor.ProcessingResult.NEXT_PROCESSOR, processor(false).processQuad(faceless, base, access, POS, stone, stone, 0L, 0, new ProcessingContextImpl()));
	}
}
