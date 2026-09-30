/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.lwjgl.util.vector.Vector3f;

import com.evilctm.client.EvilCtmClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Full-face overlay quads. Quads are baked once per (processor, tile, face) when a processor is created; tinting
 * copies the vertex data and writes the colour into the vertices, so the emitted quad is always untinted
 * ({@code tintIndex == -1}).
 */
public final class OverlayQuads {
	private static final EnumFacing[] FACES = EnumFacing.VALUES;
	private static final Supplier<BlockColors> DEFAULT_COLORS = () -> Minecraft.getMinecraft().getBlockColors();

	private static volatile Supplier<BlockColors> blockColors = DEFAULT_COLORS;

	/** Hand-built fallback: full-face positions in vanilla vertex order (v0 = minU/minV ... v3 = maxU/minV). */
	private static final float[][][] POSITIONS = {
			{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, // DOWN
			{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // UP
			{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // NORTH
			{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // SOUTH
			{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, // WEST
			{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}  // EAST
	};

	private OverlayQuads() {
	}

	/**
	 * A full-face quad for {@code face} textured with the whole of {@code sprite}, in the ITEM format with vanilla
	 * orientation. With {@code colorOrNull} the 0xRRGGBB colour is baked into the vertices.
	 */
	public static BakedQuad quad(EnumFacing face, TextureAtlasSprite sprite, int tintIndex, @Nullable Integer colorOrNull) {
		BakedQuad quad;
		try {
			quad = new FaceBakery().makeBakedQuad(new Vector3f(0, 0, 0), new Vector3f(16, 16, 16),
					new BlockPartFace(null, tintIndex, "", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0)),
					sprite, face, ModelRotation.X0_Y0, null, false, true);
		} catch (RuntimeException | LinkageError e) {
			EvilCtmClient.LOGGER.debug("FaceBakery failed for overlay quad; building it by hand", e);
			quad = handBuilt(face, sprite, tintIndex);
		}
		return colorOrNull == null ? quad : withColor(quad, colorOrNull);
	}

	/** Six untinted quads indexed by {@link EnumFacing#getIndex()}. */
	public static BakedQuad[] faces(TextureAtlasSprite sprite) {
		BakedQuad[] quads = new BakedQuad[FACES.length];
		for (EnumFacing face : FACES) {
			quads[face.getIndex()] = quad(face, sprite, -1, null);
		}
		return quads;
	}

	/**
	 * {@code base} as emitted for a rule. Without {@code tintBlock} it is returned as is (untinted, as the rule's
	 * {@code tintIndex} only applies together with {@code tintBlock}). With it, the tint block's colour at {@code pos}
	 * for {@code max(tintIndex, 0)} is baked into a copy.
	 */
	public static BakedQuad tinted(BakedQuad base, @Nullable IBlockState tintBlock, int tintIndex, IBlockAccess access, BlockPos pos) {
		if (tintBlock == null) {
			return base;
		}
		int color;
		try {
			color = blockColors.get().colorMultiplier(tintBlock, access, pos, Math.max(tintIndex, 0));
		} catch (RuntimeException e) {
			return base;
		}
		if (color == -1) {
			return base;
		}
		return withColor(base, color);
	}

	/** A copy of {@code quad} with every vertex colour set to {@code rgb} (0xRRGGBB, alpha forced to FF) and no tint index. */
	public static BakedQuad withColor(BakedQuad quad, int rgb) {
		VertexFormat format = quad.getFormat();
		int[] data = quad.getVertexData().clone();
		if (format.hasColor()) {
			int abgr = 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
			int stride = format.getIntegerSize();
			int offset = format.getColorOffset() / 4;
			for (int v = 0; v < 4; v++) {
				data[v * stride + offset] = abgr;
			}
		}
		return new BakedQuad(data, -1, quad.getFace(), quad.getSprite(), quad.shouldApplyDiffuseLighting(), format);
	}

	/** Test hook: the {@link BlockColors} tinting reads; {@code null} restores Minecraft's. */
	public static void setBlockColorsForTests(@Nullable Supplier<BlockColors> colors) {
		blockColors = colors != null ? colors : DEFAULT_COLORS;
	}

	private static BakedQuad handBuilt(EnumFacing face, TextureAtlasSprite sprite, int tintIndex) {
		int stride = DefaultVertexFormats.ITEM.getIntegerSize();
		int[] data = new int[4 * stride];
		float[][] positions = POSITIONS[face.getIndex()];
		float[] us = {sprite.getMinU(), sprite.getMinU(), sprite.getMaxU(), sprite.getMaxU()};
		float[] vs = {sprite.getMinV(), sprite.getMaxV(), sprite.getMaxV(), sprite.getMinV()};
		int normal = (byte) (face.getXOffset() * 127) & 0xFF | ((byte) (face.getYOffset() * 127) & 0xFF) << 8 | ((byte) (face.getZOffset() * 127) & 0xFF) << 16;
		for (int v = 0; v < 4; v++) {
			int o = v * stride;
			data[o] = Float.floatToRawIntBits(positions[v][0]);
			data[o + 1] = Float.floatToRawIntBits(positions[v][1]);
			data[o + 2] = Float.floatToRawIntBits(positions[v][2]);
			data[o + 3] = 0xFFFFFFFF;
			data[o + 4] = Float.floatToRawIntBits(us[v]);
			data[o + 5] = Float.floatToRawIntBits(vs[v]);
			data[o + 6] = normal;
		}
		return new BakedQuad(data, tintIndex, face, sprite, true, DefaultVertexFormats.ITEM);
	}
}
