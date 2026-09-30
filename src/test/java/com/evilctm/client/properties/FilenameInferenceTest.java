/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties;

import static com.evilctm.client.properties.PropsTestSupport.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import com.evilctm.testutil.McBootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FilenameInferenceTest {
	@BeforeAll
	static void boot() {
		McBootstrap.ensure();
	}

	private static BaseCtmProperties file(String name, String extra) {
		return parse("optifine/ctm/x/" + name, "tiles=0\n" + extra);
	}

	@Test
	void blockFileNamesInferGlass() {
		for (String name : new String[] { "block_glass.properties", "block20.properties", "block20a.properties" }) {
			BaseCtmProperties props = file(name, "");
			assertTrue(props.isValid(), name);
			assertNotNull(props.getMatchBlocksPredicate(), name);
			assertTrue(props.getMatchBlocksPredicate().test(Blocks.GLASS.getDefaultState()), name);
			assertFalse(props.getMatchBlocksPredicate().test(Blocks.STONE.getDefaultState()), name);
			assertNull(props.getMatchTilesSet(), name);
		}
	}

	@Test
	void blockInferenceRunsEvenWithMatchTiles() {
		BaseCtmProperties props = file("block20.properties", "matchTiles=x\n");
		assertTrue(props.isValid());
		assertTrue(props.getMatchBlocksPredicate().test(Blocks.GLASS.getDefaultState()));
		assertEquals(Set.of(new ResourceLocation("minecraft", "blocks/x")), props.getMatchTilesSet());
	}

	@Test
	void explicitMatchBlocksWinsOverFileName() {
		BaseCtmProperties props = file("block20.properties", "matchBlocks=stone\n");
		assertTrue(props.getMatchBlocksPredicate().test(Blocks.STONE.getDefaultState()));
		assertFalse(props.getMatchBlocksPredicate().test(Blocks.GLASS.getDefaultState()));
	}

	@Test
	void plainNameInfersTileWithoutResourceManager() {
		BaseCtmProperties props = file("glass.properties", "");
		assertTrue(props.isValid());
		assertEquals(Set.of(new ResourceLocation("minecraft", "blocks/glass")), props.getMatchTilesSet());
		assertNull(props.getMatchBlocksPredicate());
	}

	@Test
	void tilesAreRequired() {
		assertFalse(parse("optifine/ctm/x/block20.properties", "method=random\n").isValid());
	}

	@Test
	void renderPassAboveZeroIsRejected() {
		assertFalse(file("block20.properties", "renderPass=1\n").isValid());
		assertTrue(file("block20.properties", "renderPass=overlay\n").isValid());
		assertTrue(file("block20.properties", "renderPass=0\n").isValid());
	}

	@Test
	void weightParses() {
		assertEquals(0, file("block20.properties", "").getWeight());
		assertEquals(5, file("block20.properties", "weight=5\n").getWeight());
	}
}
