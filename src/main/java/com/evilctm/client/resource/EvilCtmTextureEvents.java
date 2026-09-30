/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Loads CTM rules around the block atlas stitch and publishes processors, emissive pairs and layer routing. */
public class EvilCtmTextureEvents {
	private CtmPropertiesLoader.LoadingResult lastResult;

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
		EmissiveSuffixLoader.load(Minecraft.getMinecraft().getResourceManager());
		BiomeHolderManager.clearCache();
		lastResult = CtmPropertiesLoader.loadAll();
		BiomeHolderManager.refreshHolders();
		TextureMap textureMap = event.getMap();
		EvilCtmClient.LOGGER.debug("Registering {} redirected CTM sprite dependencies", lastResult.getBlockAtlasSpriteDependencies().size());
		for (ResourceLocation spriteId : lastResult.getBlockAtlasSpriteDependencies()) {
			textureMap.setTextureEntry(new RedirectedTextureAtlasSprite(spriteId));
		}

		// CTM Mod format: register additional textures (base + sheet) into the block atlas.
		// CTM texture paths are vanilla block-atlas paths (e.g. "minecraft:blocks/glass-ctm"),
		// loaded from textures/<path>.png, so the vanilla registerSprite path is used.
		if (EvilCtmConfig.INSTANCE.ctmModTextures.get()) {
			List<CtmDefinition> ctmDefinitions = CtmMcmetaLoader.loadAll();
			String emissiveSuffix = EmissiveSuffixLoader.getEmissiveSuffix();
			for (CtmDefinition definition : ctmDefinitions) {
				for (ResourceLocation spriteId : definition.getSpriteDependencies()) {
					if (emissiveSuffix != null && !emissiveSuffix.isEmpty()
							&& spriteId.getPath().endsWith(emissiveSuffix)
							&& !EmissiveSuffixLoader.hasTexture(Minecraft.getMinecraft().getResourceManager(), spriteId)) {
						continue;
					}
					// registerSprite is idempotent for already-registered sprites
					textureMap.registerSprite(spriteId);
				}
			}
			EvilCtmClient.LOGGER.debug("Registered CTM Mod sprite dependencies from {} definitions", ctmDefinitions.size());
		}
	}

	@SubscribeEvent
	public void onTextureStitchPost(TextureStitchEvent.Post event) {
		if (!isBlockAtlas(event.getMap())) {
			return;
		}
		TextureMap textureMap = event.getMap();
		// 1. The missing sprite first: processor factories check sprites against it.
		RenderUtil.setMissingSprite(textureMap.getMissingSprite());

		if (lastResult == null) {
			BiomeHolderManager.clearCache();
			lastResult = CtmPropertiesLoader.loadAll();
			BiomeHolderManager.refreshHolders();
		}

		// 2. Processor holders: OptiFine rules, then CTM-mod definitions.
		Function<ResourceLocation, TextureAtlasSprite> spriteGetter = id -> textureMap.getAtlasSprite(id.toString());
		List<QuadProcessors.ProcessorHolder> processorHolders = lastResult.createProcessorHolders(spriteGetter);
		EvilCtmClient.LOGGER.debug("Built {} CTM processor holders", processorHolders.size());
		int bloomDefinitions = 0;
		int ctmDefinitionsLoaded = 0;
		List<CtmDefinition> ctmDefinitions = List.of();
		if (EvilCtmConfig.INSTANCE.ctmModTextures.get()) {
			ctmDefinitions = CtmMcmetaLoader.loadAll();
			ctmDefinitionsLoaded = ctmDefinitions.size();
			for (CtmDefinition definition : ctmDefinitions) {
				TextureAtlasSprite stitched = textureMap.mapUploadedSprites.get(definition.getResourceId().toString());
				if (definition.getLayer() != null && definition.getLayer().name().equals("BLOOM")
						&& stitched != null && stitched != textureMap.getMissingSprite()) {
					bloomDefinitions++;
				}
				CtmModLoader loader = new CtmModLoader(definition);
				processorHolders.add(new QuadProcessors.ProcessorHolder(
						loader.getProcessorFactory().createProcessor(definition, spriteGetter),
						loader.getPredicatesFactory().createPredicates(definition, spriteGetter)));
			}
		}

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
		EvilCtmClient.LOGGER.info("CTM reload: {} processor holders, {} CTM-mod definitions, {} stitched BLOOM definitions, {} emissive sprite pairs, {} invalid metadata files, {} unresolved resources",
				processorHolders.size(), ctmDefinitionsLoaded, bloomDefinitions, emissivePairs.size(),
				EvilCtmConfig.INSTANCE.ctmModTextures.get() ? diagnostics.invalidMetadata() : 0,
				EvilCtmConfig.INSTANCE.ctmModTextures.get() ? diagnostics.unresolvedResources() : 0);
		lastResult = null;
	}
}
