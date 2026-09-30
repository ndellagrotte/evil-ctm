/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import static com.evilctm.client.processor.simple.SpriteProviderTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.processor.BaseProcessingPredicate;
import com.evilctm.impl.client.ProcessingContextImpl;
import com.evilctm.testutil.FakeBlockAccess;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class FixedSpriteProviderTest {
	private final TextureAtlasSprite fixed = sprites("fixed", 1)[0];
	private final FakeBlockAccess access = new FakeBlockAccess();

	@Test
	void alwaysReturnsTheSameSprite() {
		FixedSpriteProvider provider = new FixedSpriteProvider(fixed);
		for (EnumFacing face : EnumFacing.values()) {
			assertSame(fixed, run(provider, quad(face), access, new BlockPos(face.ordinal() * 7, 64, -3)));
		}
	}

	@Test
	void factoryUsesTheFirstSpriteAndCountsOne() {
		FixedSpriteProvider.Factory factory = new FixedSpriteProvider.Factory();
		assertSame(fixed, run(factory.createSpriteProvider(new TextureAtlasSprite[] {fixed}, null), quad(EnumFacing.UP), access, BlockPos.ORIGIN));
		assertEquals(1, factory.getSpriteAmount(null));
	}

	@Test
	void nullSpriteStopsTheChain() {
		SimpleQuadProcessor processor = new SimpleQuadProcessor(new FixedSpriteProvider(null), new BaseProcessingPredicate(null, null, null, null));
		ProcessingContextImpl context = new ProcessingContextImpl();
		BakedQuad quad = quad(EnumFacing.UP);
		assertEquals(QuadProcessor.ProcessingResult.STOP, processor.processQuad(quad, quad.getSprite(), access, BlockPos.ORIGIN, STONE, STONE, 0L, 0, context));
		assertNull(context.takeReplacement());
	}

	@Test
	void processorReplacesTheQuadSprite() {
		SimpleQuadProcessor processor = new SimpleQuadProcessor(new FixedSpriteProvider(fixed), new BaseProcessingPredicate(null, null, null, null));
		ProcessingContextImpl context = new ProcessingContextImpl();
		BakedQuad quad = quad(EnumFacing.EAST);
		assertEquals(QuadProcessor.ProcessingResult.NEXT_PASS, processor.processQuad(quad, quad.getSprite(), access, BlockPos.ORIGIN, STONE, STONE, 0L, 0, context));
		BakedQuad replacement = context.takeReplacement();
		assertSame(fixed, replacement.getSprite());
		assertEquals(EnumFacing.EAST, replacement.getFace());
	}
}
