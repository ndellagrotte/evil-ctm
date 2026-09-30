/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties.overlay;

import java.util.Properties;

import com.evilctm.client.processor.OrientationMode;
import com.evilctm.client.properties.OrientedConnectingCtmProperties;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

/** {@link OrientedConnectingCtmProperties} plus the {@link OverlayPropertiesSection}; overlays default to {@code orient=none}. */
public class OrientedConnectingOverlayCtmProperties extends OrientedConnectingCtmProperties implements OverlayPropertiesSection.Provider {
	protected OverlayPropertiesSection overlaySection;

	public OrientedConnectingOverlayCtmProperties(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, IResourceManager resourceManager, String method, OrientationMode defaultOrientationMode) {
		super(properties, resourceId, pack, packPriority, resourceManager, method, defaultOrientationMode);
		overlaySection = new OverlayPropertiesSection(properties, resourceId, packId);
	}

	public OrientedConnectingOverlayCtmProperties(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, IResourceManager resourceManager, String method) {
		this(properties, resourceId, pack, packPriority, resourceManager, method, OrientationMode.NONE);
	}

	@Override
	public void init() {
		super.init();
		overlaySection.init();
		if (!overlaySection.isValid()) {
			valid = false;
		}
	}

	@Override
	public OverlayPropertiesSection getOverlayPropertiesSection() {
		return overlaySection;
	}
}
