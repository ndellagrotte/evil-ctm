/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.model;

import com.evilctm.client.util.QuadUtil;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Builds emissive overlay quads. The quad is a real {@link BakedQuad} whose {@code sprite} field is the emissive
 * sprite, so animated {@code _e} sprites register with Celeritas, and whose UV1 is full bright.
 */
public final class EmissiveBakedQuad {
	private EmissiveBakedQuad() {
	}

	public static BakedQuad create(BakedQuad base, TextureAtlasSprite emissive) {
		return BakedQuadLightmap.withMinimum(QuadUtil.retexture(base, emissive), 15, 15);
	}
}
