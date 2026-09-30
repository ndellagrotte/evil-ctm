/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import javax.annotation.Nullable;

import com.evilctm.api.client.ProcessingDataProvider;
import com.evilctm.client.processor.ConnectionPredicate;
import com.evilctm.client.processor.DirectionMaps;
import com.evilctm.client.processor.OrientationMode;
import com.evilctm.client.processor.ProcessingDataKeys;
import com.evilctm.client.properties.OrientedConnectingCtmProperties;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Horizontal connections pick tiles 0-3 (same layout as {@code horizontal}); when there are none, the vertical connections pick tiles 4-6,
 * and tile 3 is used for an isolated block.
 */
public class HorizontalVerticalSpriteProvider implements SpriteProvider {
	/** Connection bits (bit 0 = first direction, bit 1 = second) to a tile index; shared by both axes. */
	static final int[] INDEX_MAP = new int[] {
			3, 2, 0, 1,
	};
	/** Axis tile index (0-2) to the secondary-axis tile; 3 stays 3. */
	static final int[] SECONDARY_MAP = new int[] {
			4, 5, 6, 3,
	};

	protected final TextureAtlasSprite[] sprites;
	protected final ConnectionPredicate connectionPredicate;
	protected final boolean innerSeams;
	protected final OrientationMode orientationMode;

	public HorizontalVerticalSpriteProvider(TextureAtlasSprite[] sprites, ConnectionPredicate connectionPredicate, boolean innerSeams, OrientationMode orientationMode) {
		this.sprites = sprites;
		this.connectionPredicate = connectionPredicate;
		this.innerSeams = innerSeams;
		this.orientationMode = orientationMode;
	}

	/** Tile index for the given horizontal and vertical connection bits. */
	static int selectIndex(int horizontalConnections, int verticalConnections) {
		int primary = INDEX_MAP[horizontalConnections];
		if (primary != 3) {
			return primary;
		}
		return SECONDARY_MAP[INDEX_MAP[verticalConnections]];
	}

	@Override
	@Nullable
	public TextureAtlasSprite getSprite(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, ProcessingDataProvider dataProvider) {
		EnumFacing[] directions = DirectionMaps.getDirections(orientationMode, quad, appearanceState);
		BlockPos.MutableBlockPos mutablePos = dataProvider.getData(ProcessingDataKeys.MUTABLE_POS);
		EnumFacing face = quad.getFace();
		int horizontal = getConnections(0, directions, mutablePos, level, pos, appearanceState, state, face, sprite);
		int vertical = getConnections(1, directions, mutablePos, level, pos, appearanceState, state, face, sprite);
		return sprites[selectIndex(horizontal, vertical)];
	}

	private int getConnections(int offset, EnumFacing[] directions, BlockPos.MutableBlockPos mutablePos, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, EnumFacing face, TextureAtlasSprite quadSprite) {
		int connections = 0;
		for (int i = 0; i < 2; i++) {
			mutablePos.setPos(pos).move(directions[i * 2 + offset]);
			if (connectionPredicate.shouldConnect(level, pos, appearanceState, state, mutablePos, face, quadSprite, innerSeams)) {
				connections |= 1 << i;
			}
		}
		return connections;
	}

	public static class Factory implements SpriteProvider.Factory<OrientedConnectingCtmProperties> {
		@Override
		public SpriteProvider createSpriteProvider(TextureAtlasSprite[] sprites, OrientedConnectingCtmProperties properties) {
			return new HorizontalVerticalSpriteProvider(sprites, properties.getConnectionPredicate(), properties.getInnerSeams(), properties.getOrientationMode());
		}

		@Override
		public int getSpriteAmount(OrientedConnectingCtmProperties properties) {
			return 7;
		}
	}
}
