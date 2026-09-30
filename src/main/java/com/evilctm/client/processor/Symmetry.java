/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import net.minecraft.util.EnumFacing;

public enum Symmetry {
	NONE,
	OPPOSITE,
	ALL;

	public EnumFacing apply(EnumFacing face) {
		if (this == Symmetry.OPPOSITE) {
			if (face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) {
				face = face.getOpposite();
			}
		} else if (this == Symmetry.ALL) {
			face = EnumFacing.DOWN;
		}
		return face;
	}
}
