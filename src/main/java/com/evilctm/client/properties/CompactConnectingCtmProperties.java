/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties;

import java.util.Properties;

import javax.annotation.Nullable;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.processor.OrientationMode;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

/** Properties of {@code method=ctm_compact}: the connecting options plus the {@code ctm.<n>=<k>} tile replacements. */
public class CompactConnectingCtmProperties extends OrientedConnectingCtmProperties {
	@Nullable
	protected Int2IntMap tileReplacementMap;

	public CompactConnectingCtmProperties(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, IResourceManager resourceManager, String method, OrientationMode defaultOrientationMode) {
		super(properties, resourceId, pack, packPriority, resourceManager, method, defaultOrientationMode);
	}

	public CompactConnectingCtmProperties(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, IResourceManager resourceManager, String method) {
		this(properties, resourceId, pack, packPriority, resourceManager, method, OrientationMode.TEXTURE);
	}

	@Override
	public void init() {
		super.init();
		parseTileReplacements();
	}

	protected void parseTileReplacements() {
		for (String key : properties.stringPropertyNames()) {
			if (!key.startsWith("ctm.")) {
				continue;
			}
			int index;
			try {
				index = Integer.parseInt(key.substring(4));
			} catch (NumberFormatException e) {
				continue;
			}
			if (index < 0) {
				continue;
			}

			String valueStr = properties.getProperty(key);
			int value;
			try {
				value = Integer.parseInt(valueStr.trim());
			} catch (NumberFormatException e) {
				EvilCtmClient.LOGGER.warn("Invalid '" + key + "' value '" + valueStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
				continue;
			}
			if (value < 0) {
				EvilCtmClient.LOGGER.warn("Invalid '" + key + "' value '" + valueStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
				continue;
			}

			if (tileReplacementMap == null) {
				tileReplacementMap = new Int2IntArrayMap();
			}
			tileReplacementMap.put(index, value);
		}
	}

	@Nullable
	public Int2IntMap getTileReplacementMap() {
		return tileReplacementMap;
	}
}
