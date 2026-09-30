/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.ctm;

import java.util.Properties;

import javax.annotation.Nullable;

import com.evilctm.api.client.CachingPredicates;
import com.evilctm.api.client.CtmLoader;
import com.evilctm.api.client.CtmProperties;
import com.evilctm.api.client.QuadProcessor;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

/**
 * Adapts a single parsed CTM Mod format definition ({@link CtmDefinition}) to the Evil CTM
 * loader pipeline. The three factories are used by {@code CtmPropertiesLoader} to build the
 * processor + predicates at stitch time.
 */
public class CtmModLoader implements CtmLoader<CtmDefinition> {
	protected final CtmDefinition properties;

	public CtmModLoader(CtmDefinition properties) {
		this.properties = properties;
	}

	@Override
	public CtmProperties.Factory<CtmDefinition> getPropertiesFactory() {
		return new CtmProperties.Factory<>() {
			@Override
			@Nullable
			public CtmDefinition createProperties(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, IResourceManager resourceManager, String method) {
				return CtmModLoader.this.properties;
			}
		};
	}

	@Override
	public QuadProcessor.Factory<CtmDefinition> getProcessorFactory() {
		return new CtmQuadProcessor.Factory();
	}

	@Override
	public CachingPredicates.Factory<CtmDefinition> getPredicatesFactory() {
		return new CtmCachingPredicates.Factory<>();
	}
}
