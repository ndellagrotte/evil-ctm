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
import net.minecraftforge.fml.client.FMLClientHandler;
import org.apache.commons.io.IOUtils;

public class RedirectedTextureAtlasSprite extends TextureAtlasSprite {
	public RedirectedTextureAtlasSprite(ResourceLocation location) {
		super(location.toString());
	}

	@Override
	public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
		return location.getPath().startsWith(ResourceRedirectHandler.PATH_START);
	}

	/**
	 * In 1.12.2 Forge, returning true from a custom loader makes TextureMap skip stitching this sprite; false stitches
	 * it. A sprite that failed to load has no frames and would crash the stitch ({@code getFrameTextureData(0)}), so
	 * a failure returns true: the atlas then serves the missing sprite for it, which processors treat as missing.
	 */
	@Override
	public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
		ResourceLocation redirectedLocation = ResourceRedirectHandler.redirect(location);
		IResource resource = null;
		try {
			PngSizeInfo sizeInfo;
			try (IResource sizeResource = manager.getResource(redirectedLocation)) {
				sizeInfo = PngSizeInfo.makeFromResource(sizeResource);
			}
			resource = manager.getResource(redirectedLocation);
			boolean animated = resource.getMetadata("animation") != null;
			loadSprite(sizeInfo, animated);
			int mipmapLevels = Minecraft.getMinecraft().gameSettings.mipmapLevels;
			loadSpriteFrames(resource, mipmapLevels + 1);
			generateMipmaps(mipmapLevels);
		} catch (IOException | RuntimeException e) {
			EvilCtmClient.LOGGER.error("Failed to load redirected sprite '{}' from '{}'; it will render as the missing texture", location, redirectedLocation, e);
			trackMissing(location);
			clearFramesTextureData();
			return true;
		} finally {
			IOUtils.closeQuietly(resource);
		}
		if (getFrameCount() == 0) {
			trackMissing(location);
			return true;
		}
		return false;
	}

	private static void trackMissing(ResourceLocation location) {
		try {
			FMLClientHandler.instance().trackMissingTexture(location);
		} catch (RuntimeException | LinkageError e) {
			// not running under FML (tests)
		}
	}
}
