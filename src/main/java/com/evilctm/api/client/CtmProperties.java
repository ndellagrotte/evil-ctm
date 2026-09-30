/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.api.client;

import java.util.Collection;
import java.util.Properties;

import javax.annotation.Nullable;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

public interface CtmProperties extends Comparable<CtmProperties> {
	Collection<ResourceLocation> getSpriteDependencies();

	interface Factory<T extends CtmProperties> {
		@Nullable
		T createProperties(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, IResourceManager resourceManager, String method);
	}
}
