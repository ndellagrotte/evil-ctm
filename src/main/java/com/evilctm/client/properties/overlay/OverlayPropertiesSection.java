/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties.overlay;

import java.util.Locale;
import java.util.Properties;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.properties.PropertiesParsingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;

/**
 * The overlay keys shared by every {@code overlay*} method: {@code tintIndex}, {@code tintBlock} and {@code layer}.
 * An invalid {@code layer} (including {@code solid}) invalidates the whole rule, as OptiFine does.
 */
public class OverlayPropertiesSection {
	protected Properties properties;
	protected ResourceLocation resourceId;
	protected String packId;

	protected int tintIndex = -1;
	@Nullable
	protected IBlockState tintBlock;
	protected BlockRenderLayer layer = BlockRenderLayer.CUTOUT_MIPPED;
	protected boolean valid = true;

	public OverlayPropertiesSection(Properties properties, ResourceLocation resourceId, String packId) {
		this.properties = properties;
		this.resourceId = resourceId;
		this.packId = packId;
	}

	public void init() {
		parseTintIndex();
		parseTintBlock();
		parseLayer();
	}

	protected void parseTintIndex() {
		String tintIndexStr = properties.getProperty("tintIndex");
		if (tintIndexStr == null) {
			return;
		}

		try {
			int tintIndex = Integer.parseInt(tintIndexStr.trim());
			if (tintIndex >= 0) {
				this.tintIndex = tintIndex;
				return;
			}
		} catch (NumberFormatException e) {
			//
		}
		EvilCtmClient.LOGGER.warn("Invalid 'tintIndex' value '" + tintIndexStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
	}

	protected void parseTintBlock() {
		String tintBlockStr = properties.getProperty("tintBlock");
		if (tintBlockStr == null) {
			return;
		}
		if (tintBlockStr.trim().isEmpty()) {
			EvilCtmClient.LOGGER.warn("Invalid 'tintBlock' value '" + tintBlockStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
			return;
		}

		Predicate<IBlockState> predicate = PropertiesParsingHelper.parseBlockSpec(tintBlockStr, "tintBlock", resourceId, packId);
		if (predicate == PropertiesParsingHelper.EMPTY_BLOCK_STATE_PREDICATE) {
			return;
		}
		tintBlock = firstMatchingState(predicate);
		if (tintBlock == null) {
			EvilCtmClient.LOGGER.warn("No block state matches 'tintBlock' value '" + tintBlockStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
		}
	}

	/** The first registered state matching {@code predicate}; each block's default state is tried before its others. */
	@Nullable
	static IBlockState firstMatchingState(Predicate<IBlockState> predicate) {
		for (Block block : Block.REGISTRY) {
			IBlockState defaultState = block.getDefaultState();
			if (predicate.test(defaultState)) {
				return defaultState;
			}
			for (IBlockState state : block.getBlockState().getValidStates()) {
				if (predicate.test(state)) {
					return state;
				}
			}
		}
		return null;
	}

	protected void parseLayer() {
		String layerStr = properties.getProperty("layer");
		if (layerStr == null) {
			return;
		}

		switch (layerStr.trim().toLowerCase(Locale.ROOT)) {
			case "cutout_mipped" -> layer = BlockRenderLayer.CUTOUT_MIPPED;
			case "cutout" -> layer = BlockRenderLayer.CUTOUT;
			case "translucent" -> layer = BlockRenderLayer.TRANSLUCENT;
			default -> {
				EvilCtmClient.LOGGER.warn("Invalid overlay layer '" + layerStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
				valid = false;
			}
		}
	}

	public int getTintIndex() {
		return tintIndex;
	}

	@Nullable
	public IBlockState getTintBlock() {
		return tintBlock;
	}

	public BlockRenderLayer getLayer() {
		return layer;
	}

	public boolean isValid() {
		return valid;
	}

	public interface Provider {
		OverlayPropertiesSection getOverlayPropertiesSection();
	}
}
