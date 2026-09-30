/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/**
 * Hand-built full-cube face quads in {@link DefaultVertexFormats#ITEM} (stride 7: pos 0-2, colour 3, uv 4-5,
 * normal 6), with vanilla vertex order and UVs v0=(minU,minV), v1=(minU,maxV), v2=(maxU,maxV), v3=(maxU,minV).
 */
public final class TestQuads {
	public static final int STRIDE = 7;
	private static final float[][][] POSITIONS = {
			{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, // DOWN
			{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // UP
			{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // NORTH
			{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // SOUTH
			{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, // WEST
			{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}  // EAST
	};

	private TestQuads() {
	}

	public static BakedQuad fullFace(EnumFacing face, TextureAtlasSprite sprite, int tint) {
		return fullFace(face, sprite, tint, true);
	}

	public static BakedQuad fullFace(EnumFacing face, TextureAtlasSprite sprite, int tint, boolean diffuse) {
		int[] data = new int[4 * STRIDE];
		float[][] positions = POSITIONS[face.getIndex()];
		float[] us = {sprite.getMinU(), sprite.getMinU(), sprite.getMaxU(), sprite.getMaxU()};
		float[] vs = {sprite.getMinV(), sprite.getMaxV(), sprite.getMaxV(), sprite.getMinV()};
		for (int v = 0; v < 4; v++) {
			int o = v * STRIDE;
			data[o] = Float.floatToRawIntBits(positions[v][0]);
			data[o + 1] = Float.floatToRawIntBits(positions[v][1]);
			data[o + 2] = Float.floatToRawIntBits(positions[v][2]);
			data[o + 3] = 0xFFFFFFFF;
			data[o + 4] = Float.floatToRawIntBits(us[v]);
			data[o + 5] = Float.floatToRawIntBits(vs[v]);
			data[o + 6] = 0;
		}
		return new BakedQuad(data, tint, face, sprite, diffuse, DefaultVertexFormats.ITEM);
	}

	/** {u, v} of vertex {@code i} (ITEM, BLOCK or any format with UV0). */
	public static float[] uvOf(BakedQuad quad, int i) {
		int stride = quad.getFormat().getIntegerSize();
		int uv = quad.getFormat().getUvOffsetById(0) / 4;
		int[] data = quad.getVertexData();
		return new float[]{Float.intBitsToFloat(data[i * stride + uv]), Float.intBitsToFloat(data[i * stride + uv + 1])};
	}

	/** The quad's {@code sprite} field (a plain {@link BakedQuad}'s {@code getSprite()} returns the field). */
	public static TextureAtlasSprite spriteOf(BakedQuad quad) {
		return quad.getSprite();
	}
}
