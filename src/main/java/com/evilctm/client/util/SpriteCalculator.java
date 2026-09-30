/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.evilctm.client.layer.ModelProbe;
import com.evilctm.client.model.ReloadEpoch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/**
 * The sprites a block state's model uses per face, cached until the next {@link ReloadEpoch} bump. Backs
 * {@code connect=tile}. Index 6 of each entry holds the sprites of the face-less (null face) quads; a face lookup
 * returns that face's sprites followed by the face-less ones.
 */
public final class SpriteCalculator {
	private static final TextureAtlasSprite[] NONE = new TextureAtlasSprite[0];

	/** Resolves the model of a state. */
	@FunctionalInterface
	public interface ModelLookup {
		@Nullable
		IBakedModel get(IBlockState state);
	}

	/** Pluggable for tests; the default goes through {@link ModelProbe}. */
	public static volatile ModelLookup lookup = ModelProbe::model;

	private static final class Cache {
		final long epoch;
		final ConcurrentHashMap<IBlockState, TextureAtlasSprite[][]> map = new ConcurrentHashMap<>();

		Cache(long epoch) {
			this.epoch = epoch;
		}
	}

	private static volatile Cache cache = new Cache(ReloadEpoch.current());

	private SpriteCalculator() {
	}

	/** Sprites of {@code state} for {@code face} (null = face-less quads only). Never null, never throws. */
	public static TextureAtlasSprite[] getSprites(IBlockState state, @Nullable EnumFacing face) {
		try {
			return entry(state)[face == null ? 6 : face.getIndex()];
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return NONE;
		}
	}

	public static boolean usesSprite(IBlockState state, @Nullable EnumFacing face, @Nullable TextureAtlasSprite sprite) {
		if (sprite == null) {
			return false;
		}
		for (TextureAtlasSprite s : getSprites(state, face)) {
			if (s == sprite) {
				return true;
			}
		}
		return false;
	}

	private static TextureAtlasSprite[][] entry(IBlockState state) {
		long epoch = ReloadEpoch.current();
		Cache c = cache;
		if (c.epoch != epoch) {
			synchronized (SpriteCalculator.class) {
				c = cache;
				if (c.epoch != epoch) {
					c = new Cache(epoch);
					cache = c;
				}
			}
		}
		TextureAtlasSprite[][] entry = c.map.get(state);
		if (entry == null) {
			entry = compute(state);
			c.map.put(state, entry);
		}
		return entry;
	}

	private static TextureAtlasSprite[][] compute(IBlockState state) {
		TextureAtlasSprite[][] result = new TextureAtlasSprite[7][];
		try {
			IBakedModel model = lookup.get(state);
			TextureAtlasSprite[] faceless = collect(model, state, null, null);
			result[6] = faceless;
			for (EnumFacing face : EnumFacing.VALUES) {
				result[face.getIndex()] = collect(model, state, face, faceless);
			}
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			for (int i = 0; i < 7; i++) {
				result[i] = NONE;
			}
		}
		return result;
	}

	private static TextureAtlasSprite[] collect(@Nullable IBakedModel model, IBlockState state, @Nullable EnumFacing face, @Nullable TextureAtlasSprite[] extra) {
		List<TextureAtlasSprite> out = new ArrayList<>();
		for (BakedQuad quad : ModelProbe.quads(model, state, face)) {
			addUnique(out, quad.getSprite());
		}
		if (extra != null) {
			for (TextureAtlasSprite s : extra) {
				addUnique(out, s);
			}
		}
		return out.isEmpty() ? NONE : out.toArray(new TextureAtlasSprite[0]);
	}

	private static void addUnique(List<TextureAtlasSprite> out, @Nullable TextureAtlasSprite sprite) {
		if (sprite == null) {
			return;
		}
		for (TextureAtlasSprite s : out) {
			if (s == sprite) {
				return;
			}
		}
		out.add(sprite);
	}
}
