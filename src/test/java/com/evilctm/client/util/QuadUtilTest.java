/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

class QuadUtilTest {
	private static final float EPS = 1e-6f;

	@Test
	void retextureSetsSpriteFieldAndKeepsQuadProperties() {
		TextureAtlasSprite a = TestSprites.create("test:a");
		TextureAtlasSprite b = TestSprites.create("test:b");
		BakedQuad quad = TestQuads.fullFace(EnumFacing.NORTH, a, 3, false);

		BakedQuad out = QuadUtil.retexture(quad, b);

		assertSame(b, out.getSprite());
		assertSame(b, TestQuads.spriteOf(out));
		assertEquals(EnumFacing.NORTH, out.getFace());
		assertEquals(3, out.getTintIndex());
		assertFalse(out.shouldApplyDiffuseLighting());
		assertSame(quad.getFormat(), out.getFormat());
		assertNotSame(quad.getVertexData(), out.getVertexData());
		// The input is untouched.
		assertEquals(a.getMinU(), TestQuads.uvOf(quad, 0)[0], EPS);
	}

	@Test
	void retexturedUvsLieInsideTheNewSprite() {
		TextureAtlasSprite a = TestSprites.create("test:a2");
		TextureAtlasSprite b = TestSprites.create("test:b2");
		BakedQuad out = QuadUtil.retexture(TestQuads.fullFace(EnumFacing.UP, a, -1), b);

		for (int i = 0; i < 4; i++) {
			float[] uv = TestQuads.uvOf(out, i);
			assertTrue(uv[0] >= b.getMinU() - EPS && uv[0] <= b.getMaxU() + EPS, "u " + uv[0]);
			assertTrue(uv[1] >= b.getMinV() - EPS && uv[1] <= b.getMaxV() + EPS, "v " + uv[1]);
		}
		assertEquals(b.getMinU(), TestQuads.uvOf(out, 0)[0], EPS);
		assertEquals(b.getMinV(), TestQuads.uvOf(out, 0)[1], EPS);
		assertEquals(b.getMaxU(), TestQuads.uvOf(out, 2)[0], EPS);
		assertEquals(b.getMaxV(), TestQuads.uvOf(out, 2)[1], EPS);
	}

	@Test
	void retextureKeepsDiffuseTrue() {
		TextureAtlasSprite a = TestSprites.create("test:a3");
		TextureAtlasSprite b = TestSprites.create("test:b3");
		assertTrue(QuadUtil.retexture(TestQuads.fullFace(EnumFacing.EAST, a, -1, true), b).shouldApplyDiffuseLighting());
	}
}
