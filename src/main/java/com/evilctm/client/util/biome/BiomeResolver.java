/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util.biome;

import java.util.Locale;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

/**
 * Parses the {@code biomes=} property into a predicate. Wave 1 keeps CleanContinuity's behaviour exactly: a leading
 * {@code !} negates the whole list, and every token becomes a lowercase {@link ResourceLocation} holder resolved
 * through {@link BiomeHolderManager}.
 */
public final class BiomeResolver {
	private BiomeResolver() {
	}

	/**
	 * @param predicate the biome filter, or {@code null} when the property is absent (no filter)
	 * @param valid false when the property is present but unusable, which invalidates the rule
	 */
	public record Result(@Nullable Predicate<Biome> predicate, boolean valid) {
		static final Result NONE = new Result(null, true);
		static final Result INVALID = new Result(null, false);
	}

	public static Result parse(@Nullable String raw) {
		if (raw == null) {
			return Result.NONE;
		}

		String biomesStr = raw.trim();
		if (biomesStr.isEmpty()) {
			return Result.INVALID;
		}

		boolean negate = false;
		if (biomesStr.charAt(0) == '!') {
			negate = true;
			biomesStr = biomesStr.substring(1);
		}

		String[] biomeStrs = biomesStr.split(" ");
		if (biomeStrs.length == 0) {
			return negate ? Result.NONE : Result.INVALID;
		}

		ObjectOpenHashSet<BiomeHolder> biomeHolderSet = new ObjectOpenHashSet<>();
		for (String biomeStr : biomeStrs) {
			if (biomeStr.isEmpty()) {
				continue;
			}
			ResourceLocation biomeId = new ResourceLocation(biomeStr.toLowerCase(Locale.ROOT));
			biomeHolderSet.add(BiomeHolderManager.getOrCreateHolder(biomeId));
		}

		if (biomeHolderSet.isEmpty()) {
			return negate ? Result.NONE : Result.INVALID;
		}

		biomeHolderSet.trim();
		Predicate<Biome> predicate = new BiomeSetPredicate(biomeHolderSet);
		if (negate) {
			predicate = predicate.negate();
		}
		return new Result(predicate, true);
	}
}
