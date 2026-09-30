/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.overlay;

import java.util.EnumSet;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.evilctm.api.client.ProcessingDataProvider;
import com.evilctm.client.processor.BaseProcessingPredicate;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.util.QuadUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.Biome;

/** The base filters, plus: overlays only go on quads that cover a whole block face. */
public class OverlayProcessingPredicate extends BaseProcessingPredicate {
	private static final float EPSILON = 1.0e-4f;

	public OverlayProcessingPredicate(@Nullable EnumSet<EnumFacing> faces, @Nullable Predicate<Biome> biomePredicate, @Nullable IntPredicate heightPredicate, @Nullable Predicate<String> blockEntityNamePredicate) {
		super(faces, biomePredicate, heightPredicate, blockEntityNamePredicate);
	}

	@Override
	public boolean shouldProcessQuad(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, ProcessingDataProvider dataProvider) {
		if (!super.shouldProcessQuad(quad, sprite, level, pos, appearanceState, state, dataProvider)) {
			return false;
		}
		return isUnitSquareOnFace(quad);
	}

	/**
	 * True when all 4 vertices lie on the block face plane of the quad's face (0 or 1 on the face axis) and the
	 * quad spans [0,1] on both other axes.
	 */
	public static boolean isUnitSquareOnFace(BakedQuad quad) {
		EnumFacing face = quad.getFace();
		if (face == null) {
			return false;
		}
		int normalAxis;
		int axisA;
		int axisB;
		switch (face.getAxis()) {
			case X -> {
				normalAxis = 0;
				axisA = 1;
				axisB = 2;
			}
			case Y -> {
				normalAxis = 1;
				axisA = 0;
				axisB = 2;
			}
			default -> {
				normalAxis = 2;
				axisA = 0;
				axisB = 1;
			}
		}
		float plane = face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1 : 0;
		int seenA = 0;
		int seenB = 0;
		try {
			for (int i = 0; i < 4; i++) {
				if (!near(QuadUtil.positionComponent(quad, i, normalAxis), plane)) {
					return false;
				}
				int a = edge(QuadUtil.positionComponent(quad, i, axisA));
				int b = edge(QuadUtil.positionComponent(quad, i, axisB));
				if (a == 0 || b == 0) {
					return false;
				}
				seenA |= a;
				seenB |= b;
			}
		} catch (RuntimeException e) {
			return false;
		}
		return seenA == 3 && seenB == 3;
	}

	/** 1 when {@code v} is 0, 2 when it is 1, else 0. */
	private static int edge(float v) {
		if (near(v, 0)) {
			return 1;
		}
		if (near(v, 1)) {
			return 2;
		}
		return 0;
	}

	private static boolean near(float v, float target) {
		return v > target - EPSILON && v < target + EPSILON;
	}

	public static OverlayProcessingPredicate fromProperties(BaseCtmProperties properties) {
		return new OverlayProcessingPredicate(properties.getFaces(), properties.getBiomePredicate(), properties.getHeightPredicate(), properties.getBlockEntityNamePredicate());
	}
}
