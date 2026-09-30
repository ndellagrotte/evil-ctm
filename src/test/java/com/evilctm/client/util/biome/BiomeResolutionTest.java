/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util.biome;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;

import com.evilctm.testutil.McBootstrap;
import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomeResolutionTest {
	@BeforeEach
	void setUp() {
		McBootstrap.ensure();
		BiomeHolderManager.clearCache();
	}

	@AfterEach
	void tearDown() {
		BiomeHolderManager.clearCache();
	}

	private static Predicate<Biome> parse(String raw) {
		Predicate<Biome> p = BiomeResolver.parse(raw).predicate();
		assertNotNull(p);
		BiomeHolderManager.refreshHolders();
		return p;
	}

	@Test
	void plainsAndNamespacedPlains() {
		assertTrue(parse("plains").test(Biomes.PLAINS));
		assertTrue(parse("minecraft:plains").test(Biomes.PLAINS));
		assertFalse(parse("plains").test(Biomes.DESERT));
	}

	@Test
	void displayNameExtremehills() {
		assertTrue(parse("extremehills").test(Biomes.EXTREME_HILLS));
	}

	@Test
	void netherAlias() {
		assertTrue(parse("nether").test(Biomes.HELL));
	}

	@Test
	void negationInvertsWholeList() {
		Predicate<Biome> p = parse("!desert");
		assertFalse(p.test(Biomes.DESERT));
		assertTrue(p.test(Biomes.PLAINS));
	}

	@Test
	void compactPathResolves() {
		assertTrue(parse("roofedforest").test(Biomes.ROOFED_FOREST));
		assertTrue(parse("ice_flats").test(Biomes.ICE_PLAINS));
	}

	@Test
	void unknownBiomeMatchesNothingAndDoesNotThrow() {
		Predicate<Biome> p = parse("nosuchbiome");
		assertFalse(p.test(Biomes.PLAINS));
		assertFalse(p.test(null));
		BiomeHolderManager.refreshHolders();
	}

	@Test
	void predicateBuiltBeforeRefreshMatchesAfter() {
		Predicate<Biome> p = BiomeResolver.parse("plains desert").predicate();
		assertNotNull(p);
		BiomeHolderManager.refreshHolders();
		BiomeHolderManager.refreshHolders();
		assertTrue(p.test(Biomes.PLAINS));
		assertTrue(p.test(Biomes.DESERT));
		assertFalse(p.test(Biomes.HELL));
	}
}
