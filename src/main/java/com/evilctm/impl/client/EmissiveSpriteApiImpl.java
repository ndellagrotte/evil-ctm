/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.impl.client;

import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.evilctm.api.client.EmissiveSpriteApi;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Base sprite to emissive ({@code _e}) sprite pairs. The map is built off to the side, published in one volatile
 * write and never mutated afterwards, so chunk-builder threads always see a complete map.
 */
public final class EmissiveSpriteApiImpl implements EmissiveSpriteApi {
	public static final EmissiveSpriteApiImpl INSTANCE = new EmissiveSpriteApiImpl();

	private volatile Map<TextureAtlasSprite, TextureAtlasSprite> map = new IdentityHashMap<>();

	@Override
	@Nullable
	public TextureAtlasSprite getEmissiveSprite(TextureAtlasSprite sprite) {
		return map.get(sprite);
	}

	/** Publishes a copy of {@code pairs} (identity-keyed). */
	public void publish(Map<TextureAtlasSprite, TextureAtlasSprite> pairs) {
		map = new IdentityHashMap<>(pairs);
	}

	public static boolean hasAny() {
		return !INSTANCE.map.isEmpty();
	}

	public void clear() {
		publish(Map.of());
	}
}
