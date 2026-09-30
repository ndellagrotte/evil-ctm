/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties.overlay;

import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.evilctm.client.properties.BasicConnectingCtmProperties;
import com.evilctm.client.properties.PropertiesParsingHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

/** {@code method=overlay}: the overlay section plus {@code connectTiles} and {@code connectBlocks}. */
public class StandardOverlayCtmProperties extends BasicConnectingCtmProperties implements OverlayPropertiesSection.Provider {
	protected OverlayPropertiesSection overlaySection;
	@Nullable
	protected Set<ResourceLocation> connectTilesSet;
	@Nullable
	protected Predicate<IBlockState> connectBlocksPredicate;

	public StandardOverlayCtmProperties(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, IResourceManager resourceManager, String method) {
		super(properties, resourceId, pack, packPriority, resourceManager, method);
		overlaySection = new OverlayPropertiesSection(properties, resourceId, packId);
	}

	@Override
	public void init() {
		super.init();
		overlaySection.init();
		if (!overlaySection.isValid()) {
			valid = false;
		}
		parseConnectTiles();
		parseConnectBlocks();
	}

	@Override
	public OverlayPropertiesSection getOverlayPropertiesSection() {
		return overlaySection;
	}

	protected void parseConnectTiles() {
		connectTilesSet = PropertiesParsingHelper.parseMatchTiles(properties, "connectTiles", resourceId, packId);
	}

	protected void parseConnectBlocks() {
		connectBlocksPredicate = PropertiesParsingHelper.parseBlockStates(properties, "connectBlocks", resourceId, packId);
	}

	@Nullable
	public Set<ResourceLocation> getConnectTilesSet() {
		return connectTilesSet;
	}

	@Nullable
	public Predicate<IBlockState> getConnectBlocksPredicate() {
		return connectBlocksPredicate;
	}
}
