/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.testutil.FakeExtendedState;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.TestProcessors;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QuadProcessorsReloadTest {
	private static final QuadProcessor NOOP = (quad, sprite, level, pos, appearanceState, state, rand, pass, context) -> QuadProcessor.ProcessingResult.NEXT_PROCESSOR;

	@BeforeAll
	static void bootstrap() {
		McBootstrap.ensure();
	}

	@AfterEach
	void reset() {
		QuadProcessors.reload(List.of());
	}

	@Test
	void extendedStateResolvesToTheCleanKeySlice() {
		TextureAtlasSprite sprite = TestSprites.create("test:reload_a");
		QuadProcessors.reload(List.of(TestProcessors.holder(NOOP, TestProcessors.sprites(true, sprite))));
		IBlockState clean = Blocks.STONE.getDefaultState();
		QuadProcessors.Tables tables = QuadProcessors.current();

		QuadProcessors.Slice viaExtended = tables.slice(FakeExtendedState.wrap(clean), sprite);
		QuadProcessors.Slice viaClean = tables.slice(clean, sprite);

		assertSame(viaClean, viaExtended);
		assertEquals(1, viaClean.processors().length);
		assertSame(clean, QuadProcessors.cacheKey(FakeExtendedState.wrap(clean)));
		assertEquals(1, tables.cache.size());
	}

	@Test
	void reloadIncrementsGenerationAndEpoch() {
		long generation = QuadProcessors.generation();
		long epoch = ReloadEpoch.current();

		QuadProcessors.reload(List.of());

		assertEquals(generation + 1, QuadProcessors.generation());
		assertTrue(ReloadEpoch.current() > epoch);
	}

	@Test
	void reloadNeverReturnsSlicesFromOldTables() {
		TextureAtlasSprite sprite = TestSprites.create("test:reload_b");
		List<QuadProcessors.ProcessorHolder> holders = List.of(TestProcessors.holder(NOOP, TestProcessors.sprites(true, sprite)));
		QuadProcessors.reload(holders);
		IBlockState state = Blocks.STONE.getDefaultState();
		QuadProcessors.Tables old = QuadProcessors.current();
		QuadProcessors.Slice before = old.slice(state, sprite);

		QuadProcessors.reload(holders);

		assertNotSame(old, QuadProcessors.current());
		assertNotSame(before, QuadProcessors.current().slice(state, sprite));
		assertNotSame(before, QuadProcessors.getSlice(state, sprite));
	}

	@Test
	void emptyTablesGiveTheEmptySlice() {
		assertTrue(QuadProcessors.current().isEmpty());
		assertSame(QuadProcessors.Slice.EMPTY, QuadProcessors.current().slice(Blocks.STONE.getDefaultState(), TestSprites.create("test:reload_c")));
	}
}
