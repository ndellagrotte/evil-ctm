/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util.biome;

import javax.annotation.Nullable;

import com.evilctm.client.EvilCtmClient;
import net.minecraft.world.biome.Biome;

/** A raw lowercase {@code biomes=} token and the biome it currently resolves to. */
public final class BiomeHolder {
	private final String token;
	@Nullable
	private volatile Biome biome;
	private volatile boolean warned;

	BiomeHolder(String token) {
		this.token = token;
	}

	public String getToken() {
		return token;
	}

	@Nullable
	public Biome getBiome() {
		return biome;
	}

	void refresh(BiomeHolderManager.Index index) {
		Biome resolved = index.resolve(token);
		biome = resolved;
		if (resolved == null && !warned) {
			warned = true;
			EvilCtmClient.LOGGER.warn("Unknown biome '" + token + "'");
		}
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof BiomeHolder that && token.equals(that.token));
	}

	@Override
	public int hashCode() {
		return token.hashCode();
	}
}
