/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.Properties;

import javax.annotation.Nullable;

import com.evilctm.client.EvilCtmClient;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public final class EmissiveSuffixLoader {
	public static final ResourceLocation LOCATION = new ResourceLocation("minecraft", "optifine/emissive.properties");

	private static String emissiveSuffix;

	private EmissiveSuffixLoader() {
	}

	@Nullable
	public static String getEmissiveSuffix() {
		return emissiveSuffix;
	}

	public static void load(IResourceManager manager) {
		emissiveSuffix = null;
		try {
			IResource resource = manager.getResource(LOCATION);
			try (InputStream inputStream = resource.getInputStream()) {
				Properties properties = new Properties();
				properties.load(inputStream);
				emissiveSuffix = properties.getProperty("suffix.emissive");
			}
		} catch (FileNotFoundException e) {
			// Optional OptiFine file.
		} catch (IOException e) {
			EvilCtmClient.LOGGER.error("Failed to load emissive suffix from file '" + LOCATION + "'", e);
		}
	}

	public static boolean hasTexture(IResourceManager manager, ResourceLocation spriteId) {
		ResourceLocation imageId = new ResourceLocation(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
		try (InputStream ignored = manager.getResource(imageId).getInputStream()) {
			return true;
		} catch (FileNotFoundException ignored) {
			return false;
		} catch (IOException e) {
			EvilCtmClient.LOGGER.warn("Could not inspect emissive texture '{}'", imageId, e);
			return false;
		}
	}
}
