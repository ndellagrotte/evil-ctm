/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Quad culling applied whenever connected textures are on (glass-pane rule). Wave 2 (U10) implements it. */
public final class QuadCullers {
	private QuadCullers() {
	}

	/** Cheap per-state check: could {@link #shouldCull} ever return true for this (clean) state? */
	public static boolean mayCull(IBlockState cleanState) {
		return false;
	}

	public static boolean shouldCull(BakedQuad quad, IBlockState cleanState, BlockPos pos, IBlockAccess access, EnumFacing side) {
		return false;
	}
}
