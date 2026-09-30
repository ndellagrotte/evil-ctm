/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public interface ConnectionPredicate {
	boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite);

	default boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, EnumFacing face, TextureAtlasSprite quadSprite) {
		IBlockState otherState = level.getBlockState(otherPos);
		IBlockState otherAppearanceState = otherState.getActualState(level, otherPos);
		return shouldConnect(level, pos, appearanceState, state, otherPos, otherAppearanceState, otherState, face, quadSprite);
	}

	/**
	 * With {@code innerSeams}, a connection also needs the block in front of the neighbour (along {@code face}) not to
	 * connect. A quad without a face (some modded models) has no "in front", so that probe is skipped for it.
	 */
	default boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos.MutableBlockPos otherPos, EnumFacing face, TextureAtlasSprite quadSprite, boolean innerSeams) {
		if (shouldConnect(level, pos, appearanceState, state, otherPos, face, quadSprite)) {
			if (innerSeams && face != null) {
				otherPos.move(face);
				return !shouldConnect(level, pos, appearanceState, state, otherPos, face, quadSprite);
			} else {
				return true;
			}
		}
		return false;
	}
}
