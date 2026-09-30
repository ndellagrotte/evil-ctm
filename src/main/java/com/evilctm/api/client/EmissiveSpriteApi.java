/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.api.client;

import javax.annotation.Nullable;

import com.evilctm.impl.client.EmissiveSpriteApiImpl;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public interface EmissiveSpriteApi {
	static EmissiveSpriteApi get() {
		return EmissiveSpriteApiImpl.INSTANCE;
	}

	@Nullable
	TextureAtlasSprite getEmissiveSprite(TextureAtlasSprite sprite);
}
