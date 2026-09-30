/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Existence probes must close the whole resource, including the .mcmeta stream opened with it. */
class EmissiveSuffixLoaderTest {
	private static final class TrackedStream extends ByteArrayInputStream {
		boolean closed;

		TrackedStream() {
			super(new byte[] {1});
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}

	@Test
	void probesCloseTheMetadataStream() {
		List<TrackedStream> streams = new ArrayList<>();
		IResourceManager manager = new IResourceManager() {
			@Override
			public Set<String> getResourceDomains() {
				return Set.of("minecraft");
			}

			@Override
			public IResource getResource(ResourceLocation location) {
				TrackedStream image = new TrackedStream();
				TrackedStream mcmeta = new TrackedStream();
				streams.add(image);
				streams.add(mcmeta);
				return new SimpleResource("test", location, image, mcmeta, null);
			}

			@Override
			public List<IResource> getAllResources(ResourceLocation location) {
				return List.of(getResource(location));
			}
		};
		assertTrue(EmissiveSuffixLoader.hasTexture(manager, new ResourceLocation("minecraft:blocks/ore_e")));
		assertTrue(EmissiveSuffixLoader.hasRedirectedTexture(manager, new ResourceLocation("minecraft:evilctm_reserved/optifine/ctm/ore/0_e")));
		assertTrue(streams.size() == 4);
		for (TrackedStream stream : streams) {
			assertTrue(stream.closed);
		}
	}
}
