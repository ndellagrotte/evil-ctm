/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client;

import com.evilctm.client.compat.demonica.CtmQuadTransformer;
import com.evilctm.client.compat.demonica.DemonicaBridge;
import com.evilctm.client.compat.demonica.FastRendererLock;
import com.evilctm.client.compat.demonica.RenderPathStatus;
import com.evilctm.client.loader.CtmLoaders;
import com.evilctm.client.processor.ProcessingDataKeys;
import com.evilctm.client.resource.EvilCtmTextureEvents;
import com.evilctm.client.resource.ModelEvents;
import com.evilctm.client.resource.ModelReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Client-side lifecycle. Only reached through {@code ClientProxy}. */
public final class EvilCtmClient {
	public static final String ID = "evilctm";
	public static final String NAME = "Evil CTM";
	public static final Logger LOGGER = LogManager.getLogger(NAME);

	private EvilCtmClient() {
	}

	public static ResourceLocation asId(String path) {
		return new ResourceLocation(ID, path);
	}

	/** Loader registration only (no Demonica, no event bus); safe in unit tests. */
	public static void registerLoaders() {
		ProcessingDataKeys.init();
		CtmLoaders.registerAll();
	}

	public static void preInit() {
		registerLoaders();
		try {
			CtmQuadTransformer.registerOnce();
		} catch (LinkageError e) {
			RenderPathStatus.recordApiMissing();
			LOGGER.error("Demonica's S20 BlockQuadTransformer API is missing; Evil CTM cannot render", e);
		}
		FastRendererLock.install();
		MinecraftForge.EVENT_BUS.register(new EvilCtmTextureEvents());
		MinecraftForge.EVENT_BUS.register(new ModelEvents());
		MinecraftForge.EVENT_BUS.register(new RenderPathWarnings());
	}

	public static void init() {
		IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
		if (manager instanceof IReloadableResourceManager reloadable) {
			reloadable.registerReloadListener(new ModelReloadListener());
		}
	}

	public static void postInit() {
		DemonicaBridge.resolveQuarantine();
		RenderPathStatus.logStartup();
	}
}
