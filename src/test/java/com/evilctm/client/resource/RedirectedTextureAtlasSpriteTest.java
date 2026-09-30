/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A redirected CTM tile that cannot be loaded must be left out of the stitch, not stitched without frames. */
class RedirectedTextureAtlasSpriteTest {
	private static final ResourceLocation TILE = new ResourceLocation("minecraft", "textures/evilctm_reserved/optifine/ctm/glass/46.png");

	private static IResourceManager manager(byte[] content) {
		return new IResourceManager() {
			@Override
			public Set<String> getResourceDomains() {
				return Set.of("minecraft");
			}

			@Override
			public IResource getResource(ResourceLocation location) throws IOException {
				if (content == null) {
					throw new FileNotFoundException(location.toString());
				}
				return new SimpleResource("test", location, new ByteArrayInputStream(content), null, null);
			}

			@Override
			public List<IResource> getAllResources(ResourceLocation location) throws IOException {
				return List.of(getResource(location));
			}
		};
	}

	@Test
	void missingTileSkipsStitching() {
		RedirectedTextureAtlasSprite sprite = new RedirectedTextureAtlasSprite(new ResourceLocation("minecraft", "evilctm_reserved/optifine/ctm/glass/46"));
		assertTrue(sprite.load(manager(null), TILE, l -> null), "true makes TextureMap skip the stitch");
		assertEquals(0, sprite.getFrameCount());
	}

	@Test
	void corruptTileSkipsStitching() {
		RedirectedTextureAtlasSprite sprite = new RedirectedTextureAtlasSprite(new ResourceLocation("minecraft", "evilctm_reserved/optifine/ctm/glass/46"));
		assertTrue(sprite.load(manager("not a png".getBytes(StandardCharsets.US_ASCII)), TILE, l -> null));
		assertEquals(0, sprite.getFrameCount());
	}
}
