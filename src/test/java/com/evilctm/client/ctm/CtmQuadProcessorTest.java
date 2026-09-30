/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.ctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.evilctm.client.ctm.CtmDefinition;
import com.evilctm.client.ctm.CtmQuadProcessor;
import com.evilctm.client.ctm.QuadClipper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

/**
 * End-to-end verification of the CTM type: a fully-connected face must produce 4 quadrant quads
 * whose UVs land on the CTM sheet's 4 cells of the 4x4 grid (cells 0,1,2,3 for all-connected).
 */
class CtmQuadProcessorTest {

	static class Spr extends TextureAtlasSprite {
		Spr(String n) {
			super(n);
			width = 16;
			height = 16;
		}
		public float getMinU() { return 0; }
		public float getMaxU() { return 1; }
		public float getMinV() { return 0; }
		public float getMaxV() { return 1; }
	}

	private static BakedQuad sideQuad() {
		float[][] v = {
				{0, 0, 0, 0, 0},
				{1, 0, 0, 1, 0},
				{1, 1, 0, 1, 1},
				{0, 1, 0, 0, 1}
		};
		float[] f = new float[4 * 5];
		int[] d = new int[f.length];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 5; j++) {
				f[i * 5 + j] = v[i][j];
			}
		}
		for (int i = 0; i < f.length; i++) {
			d[i] = Float.floatToIntBits(f[i]);
		}
		return new BakedQuad(d, -1, EnumFacing.SOUTH, null, true, DefaultVertexFormats.POSITION_TEX);
	}

	private static float minU(BakedQuad q) { float m = 9; for (int i = 0; i < 4; i++) m = Math.min(m, QuadClipper.getU(q, i)); return m; }
	private static float maxU(BakedQuad q) { float m = -9; for (int i = 0; i < 4; i++) m = Math.max(m, QuadClipper.getU(q, i)); return m; }
	private static float minV(BakedQuad q) { float m = 9; for (int i = 0; i < 4; i++) m = Math.min(m, QuadClipper.getV(q, i)); return m; }
	private static float maxV(BakedQuad q) { float m = -9; for (int i = 0; i < 4; i++) m = Math.max(m, QuadClipper.getV(q, i)); return m; }

	@Test
	void ctmTypeAllConnected_producesFourQuadrantsOnSheetCells() {
		Spr base = new Spr("t:base");
		Spr sheet = new Spr("t:sheet");
		BakedQuad quad = sideQuad();

		CtmDefinition def = new CtmDefinition(new net.minecraft.util.ResourceLocation("t:base"), "test", 0);
		CtmQuadProcessor processor = new CtmQuadProcessor(def, new TextureAtlasSprite[]{base, sheet});

		List<BakedQuad> out = new ArrayList<>();
		// All 8 dirs connected (0xFF) -> CTM sheet cells 0,1,2,3 for the 4 quadrants
		processor.handleCtmWithConnections(quad, base, 0xFF, out);

		assertEquals(4, out.size());
		// Collect the (u,v) centers; each should be the center of a distinct 4x4 cell of the sheet
		for (BakedQuad q : out) {
			float u = (minU(q) + maxU(q)) / 2f;
			float v = (minV(q) + maxV(q)) / 2f;
			// cell centers are at 0.125, 0.375, 0.625, 0.875
			boolean uOk = Math.abs(u - 0.125f) < 0.02f || Math.abs(u - 0.375f) < 0.02f
					|| Math.abs(u - 0.625f) < 0.02f || Math.abs(u - 0.875f) < 0.02f;
			boolean vOk = Math.abs(v - 0.125f) < 0.02f || Math.abs(v - 0.375f) < 0.02f
					|| Math.abs(v - 0.625f) < 0.02f || Math.abs(v - 0.875f) < 0.02f;
			assertTrue(uOk, "u center " + u);
			assertTrue(vOk, "v center " + v);
		}
	}

	private static CtmQuadProcessor fixedOutput(List<BakedQuad> produce) {
		return new CtmQuadProcessor(null, new TextureAtlasSprite[0]) {
			@Override
			protected void transformQuad(BakedQuad quad, TextureAtlasSprite sprite, net.minecraft.world.IBlockAccess level, net.minecraft.util.math.BlockPos pos, net.minecraft.block.state.IBlockState appearanceState, net.minecraft.block.state.IBlockState state, long rand, List<BakedQuad> out) {
				if (produce == null) {
					out.add(quad);
				} else {
					out.addAll(produce);
				}
			}
		};
	}

	@Test
	void processQuad_unchangedQuadStops() {
		com.evilctm.impl.client.ProcessingContextImpl ctx = new com.evilctm.impl.client.ProcessingContextImpl();
		BakedQuad quad = sideQuad();
		assertEquals(com.evilctm.api.client.QuadProcessor.ProcessingResult.STOP,
				fixedOutput(null).processQuad(quad, new Spr("t:base"), null, null, null, null, 0, 0, ctx));
		assertTrue(ctx.getExtraQuads().isEmpty());
	}

	@Test
	void processQuad_emptyResultDiscards() {
		com.evilctm.impl.client.ProcessingContextImpl ctx = new com.evilctm.impl.client.ProcessingContextImpl();
		assertEquals(com.evilctm.api.client.QuadProcessor.ProcessingResult.DISCARD,
				fixedOutput(List.of()).processQuad(sideQuad(), new Spr("t:base"), null, null, null, null, 0, 0, ctx));
		assertTrue(ctx.getExtraQuads().isEmpty());
	}

	@Test
	void processQuad_piecesGoToExtrasAndAreNotCleared() {
		com.evilctm.impl.client.ProcessingContextImpl ctx = new com.evilctm.impl.client.ProcessingContextImpl();
		BakedQuad earlier = sideQuad();
		ctx.getExtraQuads().add(earlier);
		List<BakedQuad> pieces = List.of(sideQuad(), sideQuad(), sideQuad(), sideQuad());

		assertEquals(com.evilctm.api.client.QuadProcessor.ProcessingResult.DISCARD,
				fixedOutput(pieces).processQuad(sideQuad(), new Spr("t:base"), null, null, null, null, 0, 0, ctx));

		assertEquals(5, ctx.getExtraQuads().size());
		assertTrue(ctx.getExtraQuads().get(0) == earlier);
		assertTrue(ctx.getExtraQuads().subList(1, 5).equals(pieces));
	}
}
