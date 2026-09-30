/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Quad culling applied whenever connected textures are on. Vanilla pane models have no cullface on their up/down
 * faces, so the segments between two stacked panes are dropped here: a pane's top/bottom segment is hidden when the
 * pane above/below is the same kind and has an arm in the same direction.
 */
public final class QuadCullers {
	private static final float EPS = 1e-4f;
	private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

	private QuadCullers() {
	}

	/** Cheap per-state check: could {@link #shouldCull} ever return true for this (clean) state? */
	public static boolean mayCull(IBlockState cleanState) {
		return cleanState.getBlock() instanceof BlockPane;
	}

	public static boolean shouldCull(BakedQuad quad, IBlockState cleanState, BlockPos pos, IBlockAccess access, EnumFacing side) {
		Block block = cleanState.getBlock();
		if (!(block instanceof BlockPane)) {
			return false;
		}
		EnumFacing face = quad.getFace();
		if (face != EnumFacing.UP && face != EnumFacing.DOWN) {
			return false;
		}
		int[] data = quad.getVertexData();
		int stride = quad.getFormat().getIntegerSize();
		if (stride <= 0 || data.length < stride * 4) {
			return false;
		}
		float plane = face == EnumFacing.UP ? 1f : 0f;
		float sumX = 0f;
		float sumZ = 0f;
		for (int i = 0; i < 4; i++) {
			int o = i * stride;
			float y = Float.intBitsToFloat(data[o + 1]);
			if (Math.abs(y - plane) > EPS) {
				return false;
			}
			sumX += Float.intBitsToFloat(data[o]);
			sumZ += Float.intBitsToFloat(data[o + 2]);
		}
		float midX = sumX / 4f;
		float midZ = sumZ / 4f;

		BlockPos.MutableBlockPos npos = SCRATCH.get();
		npos.setPos(pos.getX() + face.getXOffset(), pos.getY() + face.getYOffset(), pos.getZ() + face.getZOffset());
		IBlockState neighbour = access.getBlockState(npos);
		if (neighbour.getBlock() != block) {
			return false;
		}
		if (block == Blocks.STAINED_GLASS_PANE
				&& neighbour.getValue(BlockStainedGlassPane.COLOR) != cleanState.getValue(BlockStainedGlassPane.COLOR)) {
			return false;
		}
		IBlockState actual = neighbour.getActualState(access, npos);
		if (midX < 0.4f) {
			return actual.getValue(BlockPane.WEST);
		}
		if (midX > 0.6f) {
			return actual.getValue(BlockPane.EAST);
		}
		if (midZ < 0.4f) {
			return actual.getValue(BlockPane.NORTH);
		}
		if (midZ > 0.6f) {
			return actual.getValue(BlockPane.SOUTH);
		}
		return true;
	}
}
