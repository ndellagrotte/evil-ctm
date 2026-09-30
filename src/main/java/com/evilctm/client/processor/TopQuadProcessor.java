/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.processor.simple.SimpleQuadProcessor;
import com.evilctm.client.properties.ConnectingCtmProperties;
import com.evilctm.client.util.AxisUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class TopQuadProcessor extends AbstractQuadProcessor {
	protected ConnectionPredicate connectionPredicate;
	protected boolean innerSeams;

	public TopQuadProcessor(TextureAtlasSprite[] sprites, ProcessingPredicate processingPredicate, ConnectionPredicate connectionPredicate, boolean innerSeams) {
		super(sprites, processingPredicate);
		this.connectionPredicate = connectionPredicate;
		this.innerSeams = innerSeams;
	}

	@Override
	public ProcessingResult processQuadInner(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, int pass, ProcessingContext context) {
		EnumFacing lightFace = quad.getFace();
		if (lightFace == null) {
			return ProcessingResult.NEXT_PROCESSOR;
		}

		EnumFacing.Axis axis = AxisUtil.getAxis(appearanceState);
		if (axis == null) {
			axis = EnumFacing.Axis.Y;
		}

		if (lightFace.getAxis() != axis) {
			EnumFacing up = fromAxisAndDirection(axis, EnumFacing.AxisDirection.POSITIVE);
			BlockPos.MutableBlockPos mutablePos = context.getData(ProcessingDataKeys.MUTABLE_POS);
			mutablePos.setPos(pos).move(up);
			if (connectionPredicate.shouldConnect(level, pos, appearanceState, state, mutablePos, lightFace, sprite, innerSeams)) {
				return SimpleQuadProcessor.process(quad, sprite, sprites[0], context);
			}
		}
		return ProcessingResult.NEXT_PROCESSOR;
	}

	private static EnumFacing fromAxisAndDirection(EnumFacing.Axis axis, EnumFacing.AxisDirection direction) {
		boolean positive = direction == EnumFacing.AxisDirection.POSITIVE;
		return switch (axis) {
			case X -> positive ? EnumFacing.EAST : EnumFacing.WEST;
			case Y -> positive ? EnumFacing.UP : EnumFacing.DOWN;
			case Z -> positive ? EnumFacing.SOUTH : EnumFacing.NORTH;
		};
	}

	public static class Factory extends AbstractQuadProcessorFactory<ConnectingCtmProperties> {
		@Override
		public QuadProcessor createProcessor(ConnectingCtmProperties properties, TextureAtlasSprite[] sprites) {
			return new TopQuadProcessor(sprites, BaseProcessingPredicate.fromProperties(properties), properties.getConnectionPredicate(), properties.getInnerSeams());
		}

		@Override
		public int getSpriteAmount(ConnectingCtmProperties properties) {
			return 1;
		}
	}
}
