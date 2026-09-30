/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util.biome;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.world.biome.Biome;

public class BiomeSetPredicate implements Predicate<Biome> {
	private final Set<BiomeHolder> holders;
	/** Identity set, replaced wholesale and never mutated after publication. */
	private volatile Set<Biome> biomes = Collections.emptySet();

	public BiomeSetPredicate(Set<BiomeHolder> holders) {
		this.holders = holders;
		refresh();
		BiomeHolderManager.addRefreshCallback(this::refresh);
	}

	@Override
	public boolean test(Biome biome) {
		return biome != null && biomes.contains(biome);
	}

	private void refresh() {
		Set<Biome> set = Collections.newSetFromMap(new IdentityHashMap<>());
		for (BiomeHolder holder : holders) {
			Biome biome = holder.getBiome();
			if (biome != null) {
				set.add(biome);
			}
		}
		this.biomes = Collections.unmodifiableSet(set);
	}
}
