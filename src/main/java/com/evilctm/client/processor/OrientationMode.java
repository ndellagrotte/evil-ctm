/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import com.evilctm.client.util.AxisUtil;
import com.evilctm.client.util.QuadUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.EnumFacing;

public enum OrientationMode {
	NONE,
	STATE_AXIS,
	TEXTURE;

	public static final int[][] AXIS_ORIENTATIONS = new int[][] {
			{ 3, 3, 1, 3, 0, 2 },
			{ 0, 0, 0, 0, 0, 0 },
			{ 2, 0, 2, 0, 1, 3 }
	};

	public int getOrientation(BakedQuad quad, IBlockState state) {
		return switch (this) {
			case NONE -> 0;
			case STATE_AXIS -> {
				EnumFacing face = quad.getFace();
				if (face == null) {
					yield 0;
				}
				EnumFacing.Axis axis = AxisUtil.getAxis(state);
				yield axis == null ? 0 : AXIS_ORIENTATIONS[axis.ordinal()][face.ordinal()];
			}
			case TEXTURE -> QuadUtil.getTextureOrientation(quad);
		};
	}
}
