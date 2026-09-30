/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties;

import static com.evilctm.client.properties.PropsTestSupport.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.IntPredicate;
import java.util.function.Predicate;

import com.evilctm.client.util.IntRangeParser;
import com.evilctm.testutil.McBootstrap;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockSpecParsingTest {
	private static final String PATH = "optifine/ctm/x/rule.properties";

	@BeforeAll
	static void boot() {
		McBootstrap.ensure();
	}

	private static Predicate<IBlockState> matchBlocks(String spec) {
		BaseCtmProperties props = parse(PATH, "tiles=0\nmatchBlocks=" + spec + "\n");
		assertTrue(props.isValid(), spec);
		return props.getMatchBlocksPredicate();
	}

	@Test
	void metaListMatchesOnlyListedMeta() {
		Predicate<IBlockState> p = matchBlocks("stone:1,2");
		assertFalse(p.test(Blocks.STONE.getStateFromMeta(0)));
		assertTrue(p.test(Blocks.STONE.getStateFromMeta(1)));
		assertTrue(p.test(Blocks.STONE.getStateFromMeta(2)));
		assertFalse(p.test(Blocks.STONE.getStateFromMeta(3)));
		assertFalse(p.test(Blocks.DIRT.getDefaultState()));
	}

	@Test
	void numericIdMatchesBlock() {
		Predicate<IBlockState> p = matchBlocks("1");
		assertTrue(p.test(Blocks.STONE.getDefaultState()));
		assertFalse(p.test(Blocks.DIRT.getDefaultState()));
	}

	@Test
	void namespacedNameWithMetaRanges() {
		Predicate<IBlockState> p = matchBlocks("minecraft:wool:1-3,14");
		for (int meta = 0; meta < 16; meta++) {
			IBlockState state = Blocks.WOOL.getStateFromMeta(meta);
			assertEquals(meta >= 1 && meta <= 3 || meta == 14, p.test(state), "meta " + meta);
		}
	}

	@Test
	void numericIdWithMetaList() {
		Predicate<IBlockState> p = matchBlocks("17:0,4,8");
		assertTrue(p.test(Blocks.LOG.getStateFromMeta(0)));
		assertTrue(p.test(Blocks.LOG.getStateFromMeta(4)));
		assertFalse(p.test(Blocks.LOG.getStateFromMeta(1)));
	}

	@Test
	void propertyFilterStillWorks() {
		Predicate<IBlockState> p = matchBlocks("wool:color=red,blue");
		assertTrue(p.test(Blocks.WOOL.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.RED)));
		assertFalse(p.test(Blocks.WOOL.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.GREEN)));
	}

	@Test
	void tokensAreUnioned() {
		Predicate<IBlockState> p = matchBlocks("stone:1 dirt glass");
		assertTrue(p.test(Blocks.STONE.getStateFromMeta(1)));
		assertFalse(p.test(Blocks.STONE.getStateFromMeta(0)));
		assertTrue(p.test(Blocks.DIRT.getDefaultState()));
		assertTrue(p.test(Blocks.GLASS.getDefaultState()));
	}

	@Test
	void unknownIdsAndNamesAreRejected() {
		assertFalse(parse(PATH, "tiles=0\nmatchBlocks=4000\n").isValid());
		assertFalse(parse(PATH, "tiles=0\nmatchBlocks=5000\n").isValid());
		assertFalse(parse(PATH, "tiles=0\nmatchBlocks=nosuchblock\n").isValid());
	}

	@Test
	void metadataFiltersTileRule() {
		BaseCtmProperties props = parse("optifine/ctm/x/rule.properties", "tiles=0\nmatchTiles=stone\nmetadata=0-1\n");
		assertTrue(props.isValid());
		Predicate<IBlockState> filter = props.getBlockStateFilter();
		assertNotNull(filter);
		assertTrue(filter.test(Blocks.STONE.getStateFromMeta(0)));
		assertTrue(filter.test(Blocks.STONE.getStateFromMeta(1)));
		assertFalse(filter.test(Blocks.STONE.getStateFromMeta(2)));
	}

	@Test
	void metadataAndMatchBlocksCombine() {
		BaseCtmProperties props = parse(PATH, "tiles=0\nmatchBlocks=stone dirt\nmetadata=1\n");
		Predicate<IBlockState> filter = props.getBlockStateFilter();
		assertTrue(filter.test(Blocks.STONE.getStateFromMeta(1)));
		assertFalse(filter.test(Blocks.STONE.getStateFromMeta(0)));
		assertFalse(filter.test(Blocks.GRASS.getStateFromMeta(0)));
		// the plain matchBlocks predicate is unaffected by metadata
		assertTrue(props.getMatchBlocksPredicate().test(Blocks.STONE.getStateFromMeta(0)));
	}

	@Test
	void intRangeParser() {
		IntPredicate p = IntRangeParser.parse("1,3-5 9");
		assertTrue(p.test(1) && p.test(4) && p.test(9));
		assertFalse(p.test(2) || p.test(6));
		assertEquals(null, IntRangeParser.parse("a"));
		assertEquals(null, IntRangeParser.parse("5-3"));
		assertEquals(null, IntRangeParser.parse(""));
	}
}
