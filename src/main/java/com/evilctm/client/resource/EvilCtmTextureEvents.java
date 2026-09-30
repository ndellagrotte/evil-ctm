/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.ctm.CtmDefinition;
import com.evilctm.client.ctm.CtmMcmetaLoader;
import com.evilctm.client.ctm.CtmModLoader;
import com.evilctm.client.ctm.CtmRenderLayerRouter;
import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.client.util.RenderUtil;
import com.evilctm.client.util.biome.BiomeHolderManager;
import com.evilctm.impl.client.EmissiveSpriteApiImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Loads CTM rules around the block atlas stitch and publishes processors, emissive pairs and layer routing. */
public class EvilCtmTextureEvents {
	private final ReloadSession.Pending pending = new ReloadSession.Pending();
	private int redirectedCompanions;

	private static boolean isBlockAtlas(TextureMap map) {
		return map != null && map == Minecraft.getMinecraft().getTextureMapBlocks();
	}

	@SubscribeEvent
	public void onTextureStitchPre(TextureStitchEvent.Pre event) {
		if (!isBlockAtlas(event.getMap())) {
			return;
		}
		// The layer router is not cleared here: chunk builds during the stitch still use the old
		// processor tables and cached layer masks, so the old routing stays live until Post step 4 replaces it.
		redirectedCompanions = 0;
		IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
		// The emissive suffix was loaded by TextureMapMixin at the head of loadSprites, before any Pre listener ran.
		ReloadSession session = pending.begin(EvilCtmTextureEvents::scanSession);
		TextureMap textureMap = event.getMap();
		String emissiveSuffix = EmissiveSuffixLoader.getEmissiveSuffix();
		boolean hasSuffix = emissiveSuffix != null && !emissiveSuffix.isEmpty();
		Set<ResourceLocation> dependencies = session.rules().getBlockAtlasSpriteDependencies();
		EvilCtmClient.LOGGER.debug("Registering {} redirected CTM sprite dependencies", dependencies.size());
		for (ResourceLocation spriteId : dependencies) {
			textureMap.setTextureEntry(new RedirectedTextureAtlasSprite(spriteId));
			if (hasSuffix && !spriteId.getPath().endsWith(emissiveSuffix)) {
				ResourceLocation companion = EmissiveSuffixLoader.companionId(spriteId, emissiveSuffix);
				if (EmissiveSuffixLoader.hasRedirectedTexture(manager, companion)) {
					textureMap.setTextureEntry(new RedirectedTextureAtlasSprite(companion));
					redirectedCompanions++;
				}
			}
		}

		// CTM Mod format: register additional textures (base + sheet) into the block atlas.
		// CTM texture paths are vanilla block-atlas paths (e.g. "minecraft:blocks/glass-ctm"),
		// loaded from textures/<path>.png, so the vanilla registerSprite path is used.
		for (CtmDefinition definition : session.definitions()) {
			for (ResourceLocation spriteId : definition.getSpriteDependencies()) {
				if (hasSuffix && spriteId.getPath().endsWith(emissiveSuffix)
						&& !EmissiveSuffixLoader.hasTexture(manager, spriteId)) {
					continue;
				}
				// registerSprite is idempotent for already-registered sprites
				textureMap.registerSprite(spriteId);
			}
		}
	}

	private static ReloadSession scanSession() {
		return ReloadSession.scan(
				() -> {
					BiomeHolderManager.clearCache();
					CtmPropertiesLoader.LoadingResult result = CtmPropertiesLoader.loadAll();
					BiomeHolderManager.refreshHolders();
					return result;
				},
				EvilCtmConfig.INSTANCE.ctmModTextures.get() ? CtmMcmetaLoader::loadAll : null);
	}

	@SubscribeEvent
	public void onTextureStitchPost(TextureStitchEvent.Post event) {
		if (!isBlockAtlas(event.getMap())) {
			return;
		}
		TextureMap textureMap = event.getMap();
		// 1. The missing sprite first: processor factories check sprites against it.
		RenderUtil.setMissingSprite(textureMap.getMissingSprite());

		ReloadSession current = pending.consume(EvilCtmTextureEvents::scanSession);
		CtmPropertiesLoader.LoadingResult lastResult = current.rules();

		// 2. Processor holders: OptiFine rules, then CTM-mod definitions.
		Function<ResourceLocation, TextureAtlasSprite> spriteGetter = id -> textureMap.getAtlasSprite(id.toString());
		List<QuadProcessors.ProcessorHolder> processorHolders = lastResult.createProcessorHolders(spriteGetter);
		EvilCtmClient.LOGGER.debug("Built {} CTM processor holders", processorHolders.size());
		int bloomDefinitions = 0;
		int ctmDefinitionsLoaded = 0;
		List<CtmDefinition> ctmDefinitions = current.definitions();
		ctmDefinitionsLoaded = ctmDefinitions.size();
		for (CtmDefinition definition : ctmDefinitions) {
			TextureAtlasSprite stitched = textureMap.mapUploadedSprites.get(definition.getResourceId().toString());
			if (definition.getLayer() != null && definition.getLayer().name().equals("BLOOM")
					&& stitched != null && stitched != textureMap.getMissingSprite()) {
				bloomDefinitions++;
			}
		}
		processorHolders.addAll(CtmModLoader.createHolders(ctmDefinitions, spriteGetter));

		// 3. Emissive pairs, built off to the side and published at once.
		Map<TextureAtlasSprite, TextureAtlasSprite> emissivePairs = new IdentityHashMap<>();
		String suffix = EmissiveSuffixLoader.getEmissiveSuffix();
		if (suffix != null && !suffix.isEmpty()) {
			for (Map.Entry<String, TextureAtlasSprite> entry : textureMap.mapUploadedSprites.entrySet()) {
				if (entry.getKey().endsWith(suffix) && entry.getValue() != textureMap.getMissingSprite()) {
					String baseKey = entry.getKey().substring(0, entry.getKey().length() - suffix.length());
					TextureAtlasSprite base = textureMap.mapUploadedSprites.get(baseKey);
					if (base != null && base != textureMap.getMissingSprite()) {
						emissivePairs.put(base, entry.getValue());
					}
				}
			}
		}
		EmissiveSpriteApiImpl.INSTANCE.publish(emissivePairs);

		// 4. CTM-mod layer routing.
		CtmRenderLayerRouter.reload(ctmDefinitions);

		// 5. Processors (bumps ReloadEpoch).
		QuadProcessors.reload(processorHolders);

		// 6. Extra-layer switch.
		LayerRouter.refreshActive();

		CtmMcmetaLoader.Diagnostics diagnostics = CtmMcmetaLoader.getLastDiagnostics();
		EvilCtmClient.LOGGER.info("CTM reload: rules {} (by method {}), {} processor holders, {} CTM-mod definitions, {} stitched BLOOM definitions, {} emissive sprite pairs, {} redirected sprites ({} emissive companions), atlas size {}, {} invalid metadata files, {} unresolved resources",
				lastResult.getRules().size(), lastResult.countByMethod(), processorHolders.size(), ctmDefinitionsLoaded, bloomDefinitions, emissivePairs.size(),
				lastResult.getBlockAtlasSpriteDependencies().size() + redirectedCompanions, redirectedCompanions, textureMap.mapUploadedSprites.size(),
				EvilCtmConfig.INSTANCE.ctmModTextures.get() ? diagnostics.invalidMetadata() : 0,
				EvilCtmConfig.INSTANCE.ctmModTextures.get() ? diagnostics.unresolvedResources() : 0);
	}
}
