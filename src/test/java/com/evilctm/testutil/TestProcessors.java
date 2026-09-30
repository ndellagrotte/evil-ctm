/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.evilctm.api.client.CachingPredicates;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.api.client.QuadProcessor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Hand-made processor holders for pipeline tests. */
public final class TestProcessors {
	private TestProcessors() {
	}

	/** Predicates matching exactly {@code sprites} (identity), or every sprite when none are given. */
	public static CachingPredicates sprites(boolean multipass, TextureAtlasSprite... sprites) {
		Set<TextureAtlasSprite> set = sprites.length == 0 ? null : Set.of(sprites);
		return predicates(set, null, multipass);
	}

	public static CachingPredicates predicates(@Nullable Set<TextureAtlasSprite> sprites, @Nullable Predicate<IBlockState> states, boolean multipass) {
		return new CachingPredicates() {
			@Override
			public boolean affectsSprites() {
				return sprites != null;
			}

			@Override
			public boolean affectsSprite(TextureAtlasSprite sprite) {
				return sprites != null && sprites.contains(sprite);
			}

			@Override
			public boolean affectsBlockStates() {
				return states != null;
			}

			@Override
			public boolean affectsBlockState(IBlockState state) {
				return states != null && states.test(state);
			}

			@Override
			public boolean isValidForMultipass() {
				return multipass;
			}
		};
	}

	public static QuadProcessors.ProcessorHolder holder(QuadProcessor processor, CachingPredicates predicates) {
		return new QuadProcessors.ProcessorHolder(processor, predicates);
	}
}
