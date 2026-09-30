/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.ctm;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import com.evilctm.client.EvilCtmClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * Registry of custom CTM logic definitions ({@code ctm.json} + {@code ctm_logic/*.json}),
 * keyed by their namespaced id (e.g. {@code ctm:optifine_full_3tile}).
 * <p>
 * On reload, every pack domain's {@code assets/<ns>/ctm.json} is read; each listed logic loads
 * {@code assets/<ns>/ctm_logic/<name>.json}, is baked into a {@link CtmCustomLogic} and
 * registered under {@code <ns>:<name>}. {@code CtmMcmetaParser} then resolves mcmeta
 * {@code "type"} strings that reference these ids.
 */
public final class CtmDefinitionManager {
	private static volatile Map<String, CtmCustomLogic> logics = Map.of();

	private CtmDefinitionManager() {
	}

	public static void reload() {
		reload(Minecraft.getMinecraft().getResourceManager());
	}

	/** Builds the logic map off to the side and publishes it as one immutable snapshot. */
	public static void reload(IResourceManager resourceManager) {
		Map<String, CtmCustomLogic> built = new Object2ObjectOpenHashMap<>();
		try {
			for (String domain : resourceManager.getResourceDomains()) {
				List<IResource> ctmFiles;
				try {
					ctmFiles = resourceManager.getAllResources(new ResourceLocation(domain, "ctm.json"));
				} catch (IOException ignored) {
					continue; // no ctm.json in this domain
				}
				for (IResource ctmFile : ctmFiles) {
					// one bad ctm.json must not stop the other files and domains
					try (ctmFile; InputStreamReader reader = new InputStreamReader(ctmFile.getInputStream(), StandardCharsets.UTF_8)) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						loadLogics(domain, json, resourceManager, built);
					} catch (IOException | RuntimeException e) {
						EvilCtmClient.LOGGER.error("Failed to read '" + domain + ":ctm.json' in pack '" + ctmFile.getResourcePackName() + "'", e);
					}
				}
			}
		} catch (Exception e) {
			EvilCtmClient.LOGGER.error("Failed to reload CTM logic definitions", e);
		}
		logics = Collections.unmodifiableMap(built);
	}

	private static void loadLogics(String domain, JsonObject ctmFile, IResourceManager resourceManager, Map<String, CtmCustomLogic> into) {
		if (ctmFile.has("logics") && ctmFile.get("logics").isJsonArray()) {
			for (var element : ctmFile.getAsJsonArray("logics")) {
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					EvilCtmClient.LOGGER.warn("Ignoring non-string entry '{}' in the 'logics' of '{}:ctm.json'", element, domain);
					continue;
				}
				String logicName = element.getAsString();
				try {
					try (IResource resource = resourceManager.getResource(new ResourceLocation(domain, "ctm_logic/" + logicName + ".json"));
						 InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						CtmLogicDefinition def = CtmLogicDefinition.fromJson(json);
						CtmCustomLogic logic = CtmLogicBakery.bake(def);
						String id = domain + ":" + logicName;
						into.put(id, logic);
						EvilCtmClient.LOGGER.debug("Registered CTM logic '{}' with {} positions", id, def.positions.size());
					}
				} catch (Exception e) {
					EvilCtmClient.LOGGER.error("Failed to load CTM logic '" + domain + ":ctm_logic/" + logicName + ".json'", e);
				}
			}
		}
	}

	public static void registerLogic(String id, CtmCustomLogic logic) {
		Map<String, CtmCustomLogic> copy = new Object2ObjectOpenHashMap<>(logics);
		copy.put(id, logic);
		logics = Collections.unmodifiableMap(copy);
	}

	@Nullable
	public static CtmCustomLogic getLogic(String id) {
		return logics.get(id);
	}

	public static void clear() {
		logics = Map.of();
	}

	public static boolean isEmpty() {
		return logics.isEmpty();
	}
}