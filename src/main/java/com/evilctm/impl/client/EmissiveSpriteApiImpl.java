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

	private static final String CTM_TILE_PREFIX = "evilctm_reserved/";

	/** The pair map and its derived flag, swapped together so a reader never sees one without the other. */
	private record Published(Map<TextureAtlasSprite, TextureAtlasSprite> map, boolean ctmTilePairs) {
	}

	private volatile Published published = new Published(new IdentityHashMap<>(), false);

	@Override
	@Nullable
	public TextureAtlasSprite getEmissiveSprite(TextureAtlasSprite sprite) {
		return published.map.get(sprite);
	}

	/** Publishes a copy of {@code pairs} (identity-keyed) in one volatile write. */
	public void publish(Map<TextureAtlasSprite, TextureAtlasSprite> pairs) {
		Map<TextureAtlasSprite, TextureAtlasSprite> copy = new IdentityHashMap<>(pairs);
		boolean ctmTiles = false;
		for (TextureAtlasSprite base : copy.keySet()) {
			if (isCtmTile(base)) {
				ctmTiles = true;
				break;
			}
		}
		published = new Published(copy, ctmTiles);
	}

	/** The currently published pairs, read-only; one call observes one complete publish. */
	public Map<TextureAtlasSprite, TextureAtlasSprite> snapshot() {
		return java.util.Collections.unmodifiableMap(published.map);
	}

	/** One complete publish: the pairs and the flag derived from them. */
	public record View(Map<TextureAtlasSprite, TextureAtlasSprite> pairs, boolean ctmTilePairs) {
	}

	/** The pairs and the CTM-tile flag of one publish, read together. */
	public View view() {
		Published p = published;
		return new View(java.util.Collections.unmodifiableMap(p.map), p.ctmTilePairs);
	}

	public static boolean hasAny() {
		return !INSTANCE.published.map.isEmpty();
	}

	/** True when any base sprite with an emissive pair is a CTM tile (its icon path starts with {@code evilctm_reserved/}). */
	public static boolean hasCtmTilePairs() {
		return INSTANCE.published.ctmTilePairs;
	}

	private static boolean isCtmTile(@Nullable TextureAtlasSprite sprite) {
		String name = sprite != null ? sprite.getIconName() : null;
		if (name == null) {
			return false;
		}
		return name.substring(name.indexOf(':') + 1).startsWith(CTM_TILE_PREFIX);
	}

	public void clear() {
		publish(Map.of());
	}
}
