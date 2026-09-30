/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class RenderUtil {
	private static volatile TextureAtlasSprite missing;

	private RenderUtil() {
	}

	/** Set from the block atlas' stitch before any processor is built. */
	public static void setMissingSprite(@Nullable TextureAtlasSprite sprite) {
		missing = sprite;
	}

	public static boolean isMissingSprite(@Nullable TextureAtlasSprite sprite) {
		return sprite != null && (sprite == missing || "missingno".equals(sprite.getIconName()));
	}
}
