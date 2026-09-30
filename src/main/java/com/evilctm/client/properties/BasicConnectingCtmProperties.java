/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties;

import java.util.Locale;
import java.util.Properties;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.processor.ConnectionPredicate;
import com.evilctm.client.util.SpriteCalculator;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BasicConnectingCtmProperties extends BaseCtmProperties {
	protected ConnectionPredicate connectionPredicate;

	public BasicConnectingCtmProperties(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, IResourceManager resourceManager, String method) {
		super(properties, resourceId, pack, packPriority, resourceManager, method);
	}

	@Override
	public void init() {
		super.init();
		parseConnect();
		detectConnect();
		validateConnect();
	}

	protected void parseConnect() {
		String connectStr = properties.getProperty("connect");
		if (connectStr == null) {
			return;
		}

		try {
			connectionPredicate = ConnectionType.valueOf(connectStr.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			EvilCtmClient.LOGGER.warn("Unknown 'connect' value '" + connectStr + "' (expected block, tile, material or state) in file '" + resourceId + "' in pack '" + packId + "'");
		}
	}

	protected void detectConnect() {
		if (connectionPredicate == null) {
			if (matchBlocksPredicate != null) {
				connectionPredicate = ConnectionType.BLOCK;
			} else if (matchTilesSet != null) {
				connectionPredicate = ConnectionType.TILE;
			}
		}
	}

	protected void validateConnect() {
		if (connectionPredicate == null) {
			EvilCtmClient.LOGGER.error("No valid connection type provided in file '" + resourceId + "' in pack '" + packId + "'");
			valid = false;
		}
	}

	public ConnectionPredicate getConnectionPredicate() {
		return connectionPredicate;
	}

	public enum ConnectionType implements ConnectionPredicate {
		/** Same block and same metadata on the raw states (stained glass colours do not connect). */
		BLOCK {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				if (state == otherState) {
					return true;
				}
				Block block = otherState.getBlock();
				return state.getBlock() == block && state.getBlock().getMetaFromState(state) == block.getMetaFromState(otherState);
			}
		},
		/** The neighbour's model uses the quad's sprite on that face. */
		TILE {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				if (state == otherState) {
					return true;
				}
				return SpriteCalculator.usesSprite(otherAppearanceState, face, quadSprite);
			}
		},
		MATERIAL {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				if (state == otherState) {
					return true;
				}
				return state.getMaterial() == otherState.getMaterial();
			}
		},
		/** Continuity extension (not in OptiFine): the appearance states must be identical. */
		STATE {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				return state == otherState || appearanceState == otherAppearanceState;
			}
		}
	}
}
