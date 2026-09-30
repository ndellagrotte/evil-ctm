/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.junit.jupiter.api.Test;

class PipelineConcurrencyTest {
	private static final int THREADS = 8;
	private static final int CALLS = 10_000;
	private static final int RELOADS = 50;

	@AfterEach
	void tearDown() {
		PipelineHarness.reset();
	}

	@Test
	void transformsSurviveConcurrentReloads() throws Exception {
		ErrorReporter.resetForTests();
		PipelineHarness harness = new PipelineHarness().gate(new FakeGate());
		harness.addRule("minecraft:optifine/ctm/glass/glass.properties", "method=ctm\nmatchBlocks=glass\ntiles=0-46\nconnect=block\n");
		harness.addRule("minecraft:optifine/ctm/stone/stone.properties", "method=random\nmatchTiles=blocks/stone\ntiles=minecraft:blocks/stone_a minecraft:blocks/stone_b\n");
		harness.publish();
		IBlockState glass = Blocks.GLASS.getDefaultState();
		IBlockState stone = Blocks.STONE.getDefaultState();
		FakeBlockAccess access = new FakeBlockAccess();
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 4; z++) {
				access.set(new BlockPos(x, 64, z), (x + z) % 2 == 0 ? glass : stone);
			}
		}
		List<BakedQuad> glassQuads = List.of(TestQuads.fullFace(EnumFacing.NORTH, harness.sprite("minecraft:blocks/glass"), -1));
		List<BakedQuad> stoneQuads = List.of(TestQuads.fullFace(EnumFacing.UP, harness.sprite("minecraft:blocks/stone"), -1));

		ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
		AtomicInteger nulls = new AtomicInteger();
		AtomicInteger changed = new AtomicInteger();
		AtomicBoolean go = new AtomicBoolean();
		CountDownLatch done = new CountDownLatch(THREADS);
		for (int t = 0; t < THREADS; t++) {
			int seed = t;
			Thread thread = new Thread(() -> {
				try {
					while (!go.get()) {
						Thread.onSpinWait();
					}
					BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
					for (int i = 0; i < CALLS; i++) {
						int x = (i + seed) & 3;
						int z = (i >> 2) & 3;
						pos.setPos(x, 64, z);
						boolean isGlass = (x + z) % 2 == 0;
						List<BakedQuad> out = QuadPipeline.transformSafely(isGlass ? glass : stone, pos, access,
								isGlass ? BlockRenderLayer.CUTOUT : BlockRenderLayer.SOLID, null, isGlass ? glassQuads : stoneQuads);
						if (out == null) {
							nulls.incrementAndGet();
						} else if (out != glassQuads && out != stoneQuads) {
							changed.incrementAndGet();
						}
					}
				} catch (Throwable e) {
					failures.add(e);
				} finally {
					done.countDown();
				}
			}, "pipeline-worker-" + t);
			thread.start();
		}
		go.set(true);
		for (int i = 0; i < RELOADS; i++) {
			harness.publish();
			Thread.yield();
		}
		done.await();

		assertTrue(failures.isEmpty(), () -> "failures: " + failures);
		assertEquals(0, nulls.get());
		assertTrue(changed.get() > 0, "no call applied a rule");
		assertEquals(0, ErrorReporter.reportedCount(), "pipeline errors were reported");
	}
}
