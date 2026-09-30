/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import com.evilctm.api.client.CachingPredicates;
import com.evilctm.api.client.CtmLoader;
import com.evilctm.api.client.CtmLoaderRegistry;
import com.evilctm.api.client.CtmProperties;
import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.util.biome.BiomeHolderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.FMLClientHandler;

public class CtmPropertiesLoader {
	private final IResourceManager resourceManager;
	/** Resource-id prefix of the rules shipped in this mod's own assets. */
	public static final String BUILTIN_PREFIX = "evilctm:optifine/ctm/default/";

	private final List<LoadedRule<?>> containers = new ObjectArrayList<>();
	private final Set<ResourceLocation> blockAtlasSpriteDependencies = new ObjectOpenHashSet<>();

	private CtmPropertiesLoader(IResourceManager resourceManager) {
		this.resourceManager = resourceManager;
	}

	public static LoadingResult loadAllWithState() {
		BiomeHolderManager.clearCache();
		LoadingResult result = loadAll();
		BiomeHolderManager.refreshHolders();
		return result;
	}

	public static LoadingResult loadAll() {
		return new CtmPropertiesLoader(Minecraft.getMinecraft().getResourceManager()).loadAllPacks();
	}

	private LoadingResult loadAllPacks() {
		int packPriority = 0;
		Set<String> seenPacks = new HashSet<>();

		for (IResourcePack pack : FMLClientHandler.instance().getResourcePackList()) {
			if (seenPacks.add(pack.getPackName())) {
				loadAll(pack, packPriority++);
			}
		}

		ResourcePackRepository repository = Minecraft.getMinecraft().getResourcePackRepository();
		for (ResourcePackRepository.Entry entry : repository.getRepositoryEntries()) {
			if (seenPacks.add(entry.getResourcePackName())) {
				loadAll(entry.getResourcePack(), packPriority++);
			}
		}

		IResourcePack serverPack = repository.getServerResourcePack();
		if (serverPack != null && seenPacks.add(serverPack.getPackName())) {
			loadAll(serverPack, packPriority++);
		}

		containers.sort(Comparator.reverseOrder());
		EvilCtmClient.LOGGER.debug("Loaded {} CTM property containers from {} packs", containers.size(), seenPacks.size());
		return new LoadingResult(containers, blockAtlasSpriteDependencies);
	}

	private void loadAll(IResourcePack pack, int packPriority) {
		int[] propertyCount = new int[1];
		scanPack(pack, (namespace, path) -> {
			if (!path.endsWith(".properties")) {
				return;
			}
			propertyCount[0]++;
			try (InputStream stream = pack.getInputStream(new ResourceLocation(namespace, path))) {
				Properties properties = new Properties();
				properties.load(stream);
				load(properties, new ResourceLocation(namespace, path), pack, packPriority);
			} catch (Exception e) {
				EvilCtmClient.LOGGER.error("Failed to load CTM properties from file '" + namespace + ":" + path + "' in pack '" + pack.getPackName() + "'", e);
			}
		});
		EvilCtmClient.LOGGER.debug("Scanned {} CTM property files in pack '{}'", propertyCount[0], pack.getPackName());
	}

	private void load(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority) {
		LoadedRule<?> rule = load(properties, resourceId, pack, packPriority, resourceManager);
		if (rule != null) {
			containers.add(rule);
			blockAtlasSpriteDependencies.addAll(rule.properties().getSpriteDependencies());
		}
	}

	/**
	 * Parses one properties file: dispatches on {@code method}, creates and validates the properties, and marks
	 * built-in rules. Returns {@code null} for an unknown method or invalid properties.
	 */
	@Nullable
	public static LoadedRule<?> load(Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, @Nullable IResourceManager resourceManager) {
		String method = properties.getProperty("method", "ctm").trim();
		CtmLoader<?> loader = CtmLoaderRegistry.get().getLoader(method);
		if (loader == null) {
			EvilCtmClient.LOGGER.error("Unknown 'method' value '" + method + "' in file '" + resourceId + "' in pack '" + pack.getPackName() + "'");
			return null;
		}
		return load(loader, properties, resourceId, pack, packPriority, resourceManager, method);
	}

	@Nullable
	private static <T extends CtmProperties> LoadedRule<T> load(CtmLoader<T> loader, Properties properties, ResourceLocation resourceId, IResourcePack pack, int packPriority, @Nullable IResourceManager resourceManager, String method) {
		T ctmProperties = loader.getPropertiesFactory().createProperties(properties, resourceId, pack, packPriority, resourceManager, method);
		if (ctmProperties == null) {
			return null;
		}
		if (ctmProperties instanceof BaseCtmProperties base && resourceId.toString().startsWith(BUILTIN_PREFIX)) {
			base.setBuiltin(true);
		}
		return new LoadedRule<>(loader, ctmProperties);
	}

	/** Sorts {@code rules} (a copy; highest priority first) and builds their processor holders. */
	public static List<QuadProcessors.ProcessorHolder> buildHolders(List<LoadedRule<?>> rules, Function<ResourceLocation, TextureAtlasSprite> spriteGetter) {
		List<LoadedRule<?>> sorted = new ObjectArrayList<>(rules);
		sorted.sort(Comparator.reverseOrder());
		List<QuadProcessors.ProcessorHolder> processorHolders = new ObjectArrayList<>();
		for (LoadedRule<?> rule : sorted) {
			processorHolders.add(rule.toProcessorHolder(spriteGetter));
		}
		return processorHolders;
	}

	private static void scanPack(IResourcePack pack, ScanConsumer consumer) {
		if (!(pack instanceof AbstractResourcePack abstractPack)) {
			EvilCtmClient.LOGGER.debug("Skipping non-abstract resource pack '{}' while scanning CTM properties", pack.getPackName());
			return;
		}

		File file = abstractPack.getResourcePackFile();
		if (file.isDirectory()) {
			scanDirectory(file.toPath(), consumer);
		} else if (file.isFile()) {
			scanZip(file, consumer);
		}
	}

	private static void scanDirectory(Path root, ScanConsumer consumer) {
		try (var stream = Files.walk(root)) {
			stream.filter(Files::isRegularFile).forEach(path -> {
				String relative = root.relativize(path).toString().replace('\\', '/');
				ResourcePackPath resourcePath = parseResourcePackPath(relative);
				if (resourcePath != null) {
					consumer.accept(resourcePath.namespace(), resourcePath.path());
				}
			});
		} catch (Exception e) {
			EvilCtmClient.LOGGER.error("Failed to scan CTM properties in folder pack '" + root + "'", e);
		}
	}

	private static void scanZip(File file, ScanConsumer consumer) {
		try (ZipFile zipFile = new ZipFile(file)) {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				ResourcePackPath resourcePath = parseResourcePackPath(entry.getName());
				if (resourcePath != null) {
					consumer.accept(resourcePath.namespace(), resourcePath.path());
				}
			}
		} catch (Exception e) {
			EvilCtmClient.LOGGER.error("Failed to scan CTM properties in zip pack '" + file + "'", e);
		}
	}

	@Nullable
	private static ResourcePackPath parseResourcePackPath(String relative) {
		if (!relative.startsWith("assets/")) {
			return null;
		}
		String rest = relative.substring("assets/".length());
		int slash = rest.indexOf('/');
		if (slash <= 0 || slash >= rest.length() - 1) {
			return null;
		}
		String namespace = rest.substring(0, slash);
		String path = rest.substring(slash + 1);
		if (!path.startsWith("optifine/ctm/") && !path.startsWith("mcpatcher/ctm/")) {
			return null;
		}
		return new ResourcePackPath(namespace, path);
	}

	private record ResourcePackPath(String namespace, String path) {
	}

	@FunctionalInterface
	private interface ScanConsumer {
		void accept(String namespace, String path);
	}

	/** One parsed rule and the loader that created it. */
	public record LoadedRule<T extends CtmProperties>(CtmLoader<T> loader, T properties) implements Comparable<LoadedRule<?>> {
		public QuadProcessors.ProcessorHolder toProcessorHolder(Function<ResourceLocation, TextureAtlasSprite> spriteGetter) {
			QuadProcessor processor = loader.getProcessorFactory().createProcessor(properties, spriteGetter);
			CachingPredicates predicates = loader.getPredicatesFactory().createPredicates(properties, spriteGetter);
			return new QuadProcessors.ProcessorHolder(processor, predicates);
		}

		@Override
		public int compareTo(LoadedRule<?> o) {
			return properties.compareTo(o.properties);
		}
	}

	public static class LoadingResult {
		private final List<LoadedRule<?>> containers;
		private final Set<ResourceLocation> blockAtlasSpriteDependencies;

		private LoadingResult(List<LoadedRule<?>> containers, Set<ResourceLocation> blockAtlasSpriteDependencies) {
			this.containers = containers;
			this.blockAtlasSpriteDependencies = blockAtlasSpriteDependencies;
		}

		public List<QuadProcessors.ProcessorHolder> createProcessorHolders(Function<ResourceLocation, TextureAtlasSprite> spriteGetter) {
			return buildHolders(containers, spriteGetter);
		}

		public List<LoadedRule<?>> getRules() {
			return containers;
		}

		public Set<ResourceLocation> getBlockAtlasSpriteDependencies() {
			return blockAtlasSpriteDependencies;
		}
	}
}
