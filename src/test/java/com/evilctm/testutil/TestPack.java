/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Set;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;

/** An empty resource pack with a name. */
public class TestPack implements IResourcePack {
	private final String name;

	public TestPack() {
		this("test");
	}

	public TestPack(String name) {
		this.name = name;
	}

	@Override
	public InputStream getInputStream(ResourceLocation location) {
		return null;
	}

	@Override
	public boolean resourceExists(ResourceLocation location) {
		return false;
	}

	@Override
	public Set<String> getResourceDomains() {
		return Set.of();
	}

	@Override
	public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer metadataSerializer, String metadataSectionName) {
		return null;
	}

	@Override
	public BufferedImage getPackImage() {
		return null;
	}

	@Override
	public String getPackName() {
		return name;
	}
}
