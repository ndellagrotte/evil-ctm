/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeExtendedState;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestProcessors;
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

class QuadPipelineContractTest {
	private static final BlockPos POS = new BlockPos(10, 70, 10);
	private static final String GLASS_RULE = "method=ctm\nmatchBlocks=glass\ntiles=0-46\nconnect=block\n";

	private PipelineHarness harness;
	private FakeGate gate;
	private IBlockState glass;
	private FakeBlockAccess access;

	@BeforeEach
	void setUp() {
		harness = new PipelineHarness();
		gate = new FakeGate();
		harness.gate(gate);
		glass = Blocks.GLASS.getDefaultState();
		access = new FakeBlockAccess().set(POS, glass);
		ErrorReporter.resetForTests();
	}

	@AfterEach
	void tearDown() {
		PipelineHarness.reset();
	}

	private List<BakedQuad> glassQuads() {
		return List.of(TestQuads.fullFace(EnumFacing.NORTH, harness.sprite("minecraft:blocks/glass"), -1));
	}

	@Test
	void noRulesReturnsTheSameInstanceWithoutAllocating() {
		harness.publish();
		List<BakedQuad> quads = glassQuads();
		for (int i = 0; i < 20_000; i++) {
			assertSame(quads, QuadPipeline.transformSafely(glass, POS, access, BlockRenderLayer.CUTOUT, EnumFacing.NORTH, quads));
		}
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long before = threads.getCurrentThreadAllocatedBytes();
		for (int i = 0; i < 10_000; i++) {
			QuadPipeline.transformSafely(glass, POS, access, BlockRenderLayer.CUTOUT, EnumFacing.NORTH, quads);
		}
		long allocated = threads.getCurrentThreadAllocatedBytes() - before;
		assertTrue(allocated < 10_000, "fast path allocated " + allocated + " bytes over 10k calls");
	}

	@Test
	void immutableInputIsNeverMutated() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/glass/glass.properties", GLASS_RULE));
		harness.publish();
		List<BakedQuad> quads = glassQuads();
		BakedQuad original = quads.get(0);

		List<BakedQuad> out = QuadPipeline.transform(glass, POS, access, BlockRenderLayer.CUTOUT, EnumFacing.NORTH, quads);

		assertEquals(1, quads.size());
		assertSame(original, quads.get(0));
		assertEquals(1, out.size());
		assertSame(harness.sprite("minecraft:evilctm_reserved/ctm/glass/0"), out.get(0).getSprite());
	}

	@Test
	void throwingProcessorKeepsNativeInputDropsNonNativeAndLogsOnce() {
		QuadProcessor thrower = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> {
			throw new IllegalStateException("boom");
		};
		QuadProcessors.reload(List.of(TestProcessors.holder(thrower, TestProcessors.sprites(true))));
		List<BakedQuad> quads = glassQuads();

		List<BakedQuad> nativeOut = QuadPipeline.transformSafely(glass, POS, access, BlockRenderLayer.CUTOUT, EnumFacing.NORTH, quads);
		List<BakedQuad> otherOut = QuadPipeline.transformSafely(glass, POS, access, BlockRenderLayer.SOLID, EnumFacing.NORTH, quads);

		assertSame(quads, nativeOut);
		assertNotNull(otherOut);
		assertTrue(otherOut.isEmpty());
		assertEquals(1, ErrorReporter.reportedCount());
	}

	@Test
	void contextIsCleanAfterAProcessorThrows() {
		QuadProcessor thrower = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> {
			context.getExtraQuads().add(quad);
			context.replaceQuad(quad);
			throw new IllegalStateException("boom");
		};
		QuadProcessors.reload(List.of(TestProcessors.holder(thrower, TestProcessors.sprites(true))));

		QuadPipeline.transformSafely(glass, POS, access, BlockRenderLayer.CUTOUT, EnumFacing.NORTH, glassQuads());

		assertTrue(QuadPipeline.contextForTests().isClean());
	}

	@Test
	void nonNativeLayerWithoutOverlaysIsEmpty() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/glass/glass.properties", GLASS_RULE));
		harness.publish();

		List<BakedQuad> out = QuadPipeline.transformSafely(glass, POS, access, BlockRenderLayer.SOLID, EnumFacing.NORTH, glassQuads());

		assertTrue(out.isEmpty());
	}

	@Test
	void extendedStateIsProcessedAsItsCleanState() {
		AtomicReference<IBlockState> seenState = new AtomicReference<>();
		AtomicReference<IBlockState> seenAppearance = new AtomicReference<>();
		QuadProcessor recorder = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> {
			seenState.set(state);
			seenAppearance.set(appearanceState);
			return QuadProcessor.ProcessingResult.NEXT_PROCESSOR;
		};
		QuadProcessors.reload(List.of(TestProcessors.holder(recorder, TestProcessors.predicates(null, s -> s == glass, true))));
		List<BakedQuad> quads = glassQuads();

		List<BakedQuad> out = QuadPipeline.transform(FakeExtendedState.wrap(glass), POS, access, BlockRenderLayer.CUTOUT, EnumFacing.NORTH, quads);

		assertSame(quads, out);
		assertSame(glass, seenState.get());
		assertSame(glass, seenAppearance.get());
	}

	@Test
	void shaderLayerOverrideKeepsBaseQuads() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/glass/glass.properties", GLASS_RULE));
		harness.publish();
		assertTrue(QuadPipeline.transform(glass, POS, access, BlockRenderLayer.TRANSLUCENT, EnumFacing.NORTH, glassQuads()).isEmpty());

		gate.override(Blocks.GLASS, BlockRenderLayer.TRANSLUCENT);
		List<BakedQuad> out = QuadPipeline.transform(glass, POS, access, BlockRenderLayer.TRANSLUCENT, EnumFacing.NORTH, glassQuads());

		assertEquals(1, out.size());
		assertSame(harness.sprite("minecraft:evilctm_reserved/ctm/glass/0"), out.get(0).getSprite());
	}
}
