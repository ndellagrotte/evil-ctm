/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util.biome;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.world.biome.Biome;

/**
 * Parses the {@code biomes=} property into a predicate. A leading {@code !} negates the whole list; each remaining
 * token is kept raw and resolved by {@link BiomeHolderManager} (display name, {@code nether} alias, registry id,
 * {@code minecraft:} path, compact path, then any namespace).
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
			biomeHolderSet.add(BiomeHolderManager.getOrCreateHolder(biomeStr));
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
