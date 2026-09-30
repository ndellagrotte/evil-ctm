/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import com.evilctm.api.client.QuadProcessor.ProcessingResult;
import com.evilctm.client.util.AxisUtil;
import com.evilctm.impl.client.ProcessingContextImpl;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockQuartz;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AxisHandlingTest {
	private static final BlockPos POS = new BlockPos(4, 70, 4);

	@BeforeAll
	static void bootstrap() {
		McBootstrap.ensure();
	}

	private static IBlockState log(BlockLog.EnumAxis axis) {
		return Blocks.LOG.getDefaultState().withProperty(BlockLog.LOG_AXIS, axis);
	}

	private static boolean accepts(EnumSet<EnumFacing> faces, IBlockState state, EnumFacing face) {
		TextureAtlasSprite sprite = TestSprites.create("minecraft:blocks/log_oak");
		BakedQuad quad = TestQuads.fullFace(face, sprite, -1);
		BaseProcessingPredicate predicate = new BaseProcessingPredicate(faces, null, null, null);
		return predicate.shouldProcessQuad(quad, sprite, new FakeBlockAccess().set(POS, state), POS, state, state, new ProcessingContextImpl());
	}

	@Test
	void axisResolution() {
		assertEquals(EnumFacing.Axis.X, AxisUtil.getAxis(log(BlockLog.EnumAxis.X)));
		assertEquals(EnumFacing.Axis.Y, AxisUtil.getAxis(log(BlockLog.EnumAxis.Y)));
		assertEquals(EnumFacing.Axis.Z, AxisUtil.getAxis(log(BlockLog.EnumAxis.Z)));
		assertNull(AxisUtil.getAxis(log(BlockLog.EnumAxis.NONE)));
		assertNull(AxisUtil.getAxis(Blocks.STONE.getDefaultState()));
		IBlockState quartz = Blocks.QUARTZ_BLOCK.getDefaultState();
		assertEquals(EnumFacing.Axis.X, AxisUtil.getAxis(quartz.withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.LINES_X)));
		assertEquals(EnumFacing.Axis.Z, AxisUtil.getAxis(quartz.withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.LINES_Z)));
		assertNull(AxisUtil.getAxis(quartz.withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.CHISELED)));
	}

	@Test
	void topBottomRulesFollowTheXLogAxis() {
		EnumSet<EnumFacing> top = EnumSet.of(EnumFacing.UP);
		EnumSet<EnumFacing> bottom = EnumSet.of(EnumFacing.DOWN);
		EnumSet<EnumFacing> both = EnumSet.of(EnumFacing.UP, EnumFacing.DOWN);
		IBlockState x = log(BlockLog.EnumAxis.X);
		assertTrue(accepts(top, x, EnumFacing.WEST));
		assertFalse(accepts(top, x, EnumFacing.EAST));
		assertFalse(accepts(top, x, EnumFacing.UP));
		assertTrue(accepts(bottom, x, EnumFacing.EAST));
		assertFalse(accepts(bottom, x, EnumFacing.WEST));
		assertTrue(accepts(both, x, EnumFacing.EAST));
		assertTrue(accepts(both, x, EnumFacing.WEST));
		assertFalse(accepts(both, x, EnumFacing.UP));
	}

	@Test
	void topBottomRulesFollowTheZLogAxis() {
		EnumSet<EnumFacing> top = EnumSet.of(EnumFacing.UP);
		EnumSet<EnumFacing> bottom = EnumSet.of(EnumFacing.DOWN);
		IBlockState z = log(BlockLog.EnumAxis.Z);
		assertTrue(accepts(top, z, EnumFacing.NORTH));
		assertFalse(accepts(top, z, EnumFacing.SOUTH));
		assertTrue(accepts(bottom, z, EnumFacing.SOUTH));
		assertFalse(accepts(bottom, z, EnumFacing.NORTH));
		assertFalse(accepts(top, z, EnumFacing.UP));
		assertFalse(accepts(bottom, z, EnumFacing.DOWN));
	}

	@Test
	void sideRulesOnZLogs() {
		IBlockState z = log(BlockLog.EnumAxis.Z);
		assertTrue(accepts(EnumSet.of(EnumFacing.NORTH), z, EnumFacing.DOWN));
		assertFalse(accepts(EnumSet.of(EnumFacing.NORTH), z, EnumFacing.UP));
		assertTrue(accepts(EnumSet.of(EnumFacing.SOUTH), z, EnumFacing.UP));
		assertFalse(accepts(EnumSet.of(EnumFacing.SOUTH), z, EnumFacing.DOWN));
		assertTrue(accepts(EnumSet.of(EnumFacing.EAST), z, EnumFacing.EAST));
		assertTrue(accepts(EnumSet.of(EnumFacing.WEST), z, EnumFacing.WEST));
	}

	@Test
	void sideRulesOnSidewaysLogs() {
		EnumSet<EnumFacing> sides = EnumSet.of(EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST);
		IBlockState x = log(BlockLog.EnumAxis.X);
		assertTrue(accepts(sides, x, EnumFacing.UP));
		assertTrue(accepts(sides, x, EnumFacing.DOWN));
		assertTrue(accepts(sides, x, EnumFacing.NORTH));
		assertFalse(accepts(sides, x, EnumFacing.WEST));
		assertTrue(accepts(EnumSet.of(EnumFacing.EAST), x, EnumFacing.UP));
	}

	@Test
	void verticalLogIsUnchanged() {
		EnumSet<EnumFacing> top = EnumSet.of(EnumFacing.UP);
		IBlockState y = log(BlockLog.EnumAxis.Y);
		assertTrue(accepts(top, y, EnumFacing.UP));
		assertFalse(accepts(top, y, EnumFacing.EAST));
		assertFalse(accepts(top, y, EnumFacing.NORTH));
	}

	@Test
	void quartzLinesZChecksConnectionAtSouth() {
		IBlockState state = Blocks.QUARTZ_BLOCK.getDefaultState().withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.LINES_Z);
		TextureAtlasSprite sprite = TestSprites.create("minecraft:blocks/quartz_block_lines");
		TextureAtlasSprite replacement = TestSprites.create("minecraft:evilctm_reserved/top/0");
		AtomicReference<BlockPos> checked = new AtomicReference<>();
		ConnectionPredicate connection = (level, pos, appearance, st, otherPos, otherAppearance, otherState, face, quadSprite) -> {
			checked.set(otherPos.toImmutable());
			return true;
		};
		TopQuadProcessor processor = new TopQuadProcessor(new TextureAtlasSprite[] { replacement },
				new BaseProcessingPredicate(null, null, null, null), connection, false);
		ProcessingContextImpl context = new ProcessingContextImpl();
		BakedQuad quad = TestQuads.fullFace(EnumFacing.UP, sprite, -1);

		ProcessingResult result = processor.processQuadInner(quad, sprite, new FakeBlockAccess().set(POS, state), POS, state, state, 0L, 0, context);

		assertEquals(POS.south(), checked.get());
		assertEquals(ProcessingResult.NEXT_PASS, result);
		assertSame(replacement, context.takeReplacement().getSprite());
	}

	@Test
	void stateAxisOrientationUsesTheLogAxis() {
		IBlockState x = log(BlockLog.EnumAxis.X);
		TextureAtlasSprite sprite = TestSprites.create("minecraft:blocks/log_oak");
		for (EnumFacing face : EnumFacing.values()) {
			BakedQuad quad = TestQuads.fullFace(face, sprite, -1);
			assertEquals(OrientationMode.AXIS_ORIENTATIONS[EnumFacing.Axis.X.ordinal()][face.ordinal()], OrientationMode.STATE_AXIS.getOrientation(quad, x));
		}
		assertEquals(0, OrientationMode.STATE_AXIS.getOrientation(TestQuads.fullFace(EnumFacing.EAST, sprite, -1), Blocks.STONE.getDefaultState()));
	}
}
