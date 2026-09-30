/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import java.io.IOException;
import java.util.function.Function;

import com.evilctm.client.EvilCtmClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public class RedirectedTextureAtlasSprite extends TextureAtlasSprite {
	public RedirectedTextureAtlasSprite(ResourceLocation location) {
		super(location.toString());
	}

	@Override
	public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
		return location.getPath().startsWith(ResourceRedirectHandler.PATH_START);
	}

	@Override
	public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
		try {
			ResourceLocation redirectedLocation = ResourceRedirectHandler.redirect(location);
			IResource resource = manager.getResource(redirectedLocation);
			PngSizeInfo sizeInfo = PngSizeInfo.makeFromResource(resource);
			resource = manager.getResource(redirectedLocation);
			boolean animated = resource.getMetadata("animation") != null;
			loadSprite(sizeInfo, animated);
			int mipmapLevels = Minecraft.getMinecraft().gameSettings.mipmapLevels;
			loadSpriteFrames(resource, mipmapLevels + 1);
			generateMipmaps(mipmapLevels);
			// In 1.12.2 Forge, returning true from a custom loader makes TextureMap skip stitching.
			// Return false after loading so the sprite still goes through the normal stitch path.
			return false;
		} catch (IOException e) {
			EvilCtmClient.LOGGER.error("Failed to load redirected sprite '{}'", location, e);
			return false;
		}
	}
}
