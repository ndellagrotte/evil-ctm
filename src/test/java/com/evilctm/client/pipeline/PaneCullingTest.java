/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.McBootstrap;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PaneCullingTest {
	private static final BlockPos LOWER = new BlockPos(5, 60, 5);
	private static final BlockPos UPPER = LOWER.up();

	@BeforeAll
	static void boot() {
		McBootstrap.ensure();
	}

	/** Flat quad at y with x/z extents, DOWN-facing. */
	private static BakedQuad flat(EnumFacing face, float y, float x0, float x1, float z0, float z1) {
		float[][] p = {{x0, y, z1}, {x0, y, z0}, {x1, y, z0}, {x1, y, z1}};
		int[] data = new int[28];
		for (int v = 0; v < 4; v++) {
			data[v * 7] = Float.floatToRawIntBits(p[v][0]);
			data[v * 7 + 1] = Float.floatToRawIntBits(p[v][1]);
			data[v * 7 + 2] = Float.floatToRawIntBits(p[v][2]);
		}
		return new BakedQuad(data, -1, face, null, true, DefaultVertexFormats.ITEM);
	}

	private static FakeBlockAccess stackWithEastArms(IBlockState lower, IBlockState upper) {
		FakeBlockAccess a = new FakeBlockAccess().set(LOWER, lower).set(UPPER, upper);
		a.set(LOWER.east(), lower).set(UPPER.east(), upper);
		return a;
	}

	@Test
	void stackedPanesCullMatchingSegments() {
		IBlockState pane = Blocks.GLASS_PANE.getDefaultState();
		FakeBlockAccess a = stackWithEastArms(pane, pane);
		BakedQuad east = flat(EnumFacing.DOWN, 0f, 0.6875f, 1f, 0.4375f, 0.5625f);
		BakedQuad centre = flat(EnumFacing.DOWN, 0f, 0.4375f, 0.5625f, 0.4375f, 0.5625f);
		BakedQuad north = flat(EnumFacing.DOWN, 0f, 0.4375f, 0.5625f, 0f, 0.4375f);
		assertTrue(QuadCullers.shouldCull(east, pane, UPPER, a, null));
		assertTrue(QuadCullers.shouldCull(centre, pane, UPPER, a, null));
		assertFalse(QuadCullers.shouldCull(north, pane, UPPER, a, null));
	}

	@Test
	void upFaceChecksPaneAbove() {
		IBlockState pane = Blocks.GLASS_PANE.getDefaultState();
		FakeBlockAccess a = stackWithEastArms(pane, pane);
		BakedQuad east = flat(EnumFacing.UP, 1f, 0.6875f, 1f, 0.4375f, 0.5625f);
		assertTrue(QuadCullers.shouldCull(east, pane, LOWER, a, null));
		assertFalse(QuadCullers.shouldCull(east, pane, UPPER, a, null));
	}

	@Test
	void offPlaneQuadsAreKept() {
		IBlockState pane = Blocks.GLASS_PANE.getDefaultState();
		FakeBlockAccess a = stackWithEastArms(pane, pane);
		assertFalse(QuadCullers.shouldCull(flat(EnumFacing.DOWN, 0.5f, 0.4375f, 0.5625f, 0.4375f, 0.5625f), pane, UPPER, a, null));
	}

	@Test
	void differentColoursCullNothing() {
		IBlockState red = Blocks.STAINED_GLASS_PANE.getDefaultState().withProperty(BlockStainedGlassPane.COLOR, EnumDyeColor.RED);
		IBlockState blue = Blocks.STAINED_GLASS_PANE.getDefaultState().withProperty(BlockStainedGlassPane.COLOR, EnumDyeColor.BLUE);
		FakeBlockAccess a = stackWithEastArms(blue, red);
		assertFalse(QuadCullers.shouldCull(flat(EnumFacing.DOWN, 0f, 0.6875f, 1f, 0.4375f, 0.5625f), red, UPPER, a, null));
		assertFalse(QuadCullers.shouldCull(flat(EnumFacing.DOWN, 0f, 0.4375f, 0.5625f, 0.4375f, 0.5625f), red, UPPER, a, null));
		FakeBlockAccess same = stackWithEastArms(red, red);
		assertTrue(QuadCullers.shouldCull(flat(EnumFacing.DOWN, 0f, 0.4375f, 0.5625f, 0.4375f, 0.5625f), red, UPPER, same, null));
	}

	@Test
	void differentBlockOrSideFaceIsKept() {
		IBlockState pane = Blocks.GLASS_PANE.getDefaultState();
		IBlockState bars = Blocks.IRON_BARS.getDefaultState();
		FakeBlockAccess a = stackWithEastArms(bars, pane);
		assertFalse(QuadCullers.shouldCull(flat(EnumFacing.DOWN, 0f, 0.4375f, 0.5625f, 0.4375f, 0.5625f), pane, UPPER, a, null));
		FakeBlockAccess b = stackWithEastArms(pane, pane);
		assertFalse(QuadCullers.shouldCull(flat(EnumFacing.NORTH, 0f, 0.4375f, 0.5625f, 0.4375f, 0.5625f), pane, UPPER, b, null));
	}

	@Test
	void nonPaneBlockMayNotCull() {
		assertFalse(QuadCullers.mayCull(Blocks.STONE.getDefaultState()));
		assertFalse(QuadCullers.mayCull(Blocks.GLASS.getDefaultState()));
		assertTrue(QuadCullers.mayCull(Blocks.GLASS_PANE.getDefaultState()));
		FakeBlockAccess a = new FakeBlockAccess().set(LOWER, Blocks.GLASS.getDefaultState()).set(UPPER, Blocks.GLASS.getDefaultState());
		assertFalse(QuadCullers.shouldCull(flat(EnumFacing.DOWN, 0f, 0.4375f, 0.5625f, 0.4375f, 0.5625f), Blocks.GLASS.getDefaultState(), UPPER, a, null));
	}
}
