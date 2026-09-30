/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util.biome;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

public final class BiomeHolderManager {
	private static final Map<String, BiomeHolder> HOLDER_CACHE = new ConcurrentHashMap<>();
	private static final Set<Runnable> REFRESH_CALLBACKS = ConcurrentHashMap.newKeySet();

	private BiomeHolderManager() {
	}

	/** @param token the raw biome token, which is lowercased here */
	public static BiomeHolder getOrCreateHolder(String token) {
		return HOLDER_CACHE.computeIfAbsent(token.toLowerCase(Locale.ROOT), BiomeHolder::new);
	}

	public static void addRefreshCallback(Runnable callback) {
		REFRESH_CALLBACKS.add(callback);
	}

	public static void init() {
		refreshHolders();
	}

	public static void refreshHolders() {
		Index index = new Index();
		for (BiomeHolder holder : HOLDER_CACHE.values()) {
			holder.refresh(index);
		}
		for (Runnable callback : REFRESH_CALLBACKS) {
			callback.run();
		}
	}

	public static void clearCache() {
		HOLDER_CACHE.clear();
		REFRESH_CALLBACKS.clear();
	}

	/** Snapshot of the biome registry used to resolve tokens. */
	static final class Index {
		private final Map<String, Biome> byDisplayName = new HashMap<>();
		private final Map<ResourceLocation, Biome> byId = new HashMap<>();
		private final Map<String, Biome> byCompactPath = new HashMap<>();
		private final Map<String, Biome> byPath = new HashMap<>();

		Index() {
			for (ResourceLocation id : Biome.REGISTRY.getKeys()) {
				Biome biome = Biome.REGISTRY.getObject(id);
				if (biome == null) {
					continue;
				}
				byId.put(id, biome);
				String name = biome.getBiomeName();
				if (name != null) {
					byDisplayName.putIfAbsent(name.replace(" ", "").toLowerCase(Locale.ROOT), biome);
				}
				byCompactPath.putIfAbsent(id.getPath().replace("_", ""), biome);
				byPath.putIfAbsent(id.getPath(), biome);
			}
		}

		@Nullable
		Biome resolve(String t) {
			Biome b = byDisplayName.get(t);
			if (b != null) {
				return b;
			}
			if (t.equals("nether")) {
				return Biomes.HELL;
			}
			if (t.indexOf(':') >= 0) {
				return byId.get(new ResourceLocation(t));
			}
			b = byId.get(new ResourceLocation("minecraft", t));
			if (b != null) {
				return b;
			}
			b = byCompactPath.get(t);
			if (b != null) {
				return b;
			}
			return byPath.get(t);
		}
	}
}
