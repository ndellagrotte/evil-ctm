/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.ctm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtmMcmetaLoaderScanTest {
	@TempDir
	Path tempDir;

	@Test
	void baseResourcesDirectoryMapsContentTweakerMetadataToItsResourceNamespace() throws Exception {
		Path resources = Files.createDirectory(tempDir.resolve("resources"));
		Path metadata = resources.resolve("contenttweaker/textures/blocks/glass.png.mcmeta");
		Files.createDirectories(metadata.getParent());
		Files.writeString(metadata, "{}");

		List<String> found = new ArrayList<>();
		CtmMcmetaLoader.scanDirectory(resources, (namespace, path) -> found.add(namespace + ":" + path));

		assertEquals(List.of("contenttweaker:textures/blocks/glass.png.mcmeta"), found);
	}

	@Test
	void normalPackKeepsAssetsMappingAndIgnoresUnmappedContentTweakerFolder() throws Exception {
		Path pack = Files.createDirectory(tempDir.resolve("pack"));
		Path metadata = pack.resolve("assets/contenttweaker/textures/blocks/glass.png.mcmeta");
		Files.createDirectories(metadata.getParent());
		Files.writeString(metadata, "{}");
		Path unmapped = pack.resolve("contenttweaker/textures/blocks/stray.png.mcmeta");
		Files.createDirectories(unmapped.getParent());
		Files.writeString(unmapped, "{}");

		List<String> found = new ArrayList<>();
		CtmMcmetaLoader.scanDirectory(pack, (namespace, path) -> found.add(namespace + ":" + path));

		assertEquals(List.of("contenttweaker:textures/blocks/glass.png.mcmeta"), found);
	}

	@Test
	void resourceLoaderPacksDiscoverNamespacedMetadataWithoutAssetsDirectory() throws Exception {
		for (String folder : List.of("resources", "oresources")) {
			Path root = Files.createDirectory(tempDir.resolve(folder));
			Path metadata = root.resolve("minecraft/textures/blocks/stone.png.mcmeta");
			Files.createDirectories(metadata.getParent());
			Files.writeString(metadata, "{}");
		}
		List<String> found = new ArrayList<>();
		for (Path root : CtmMcmetaLoader.externalRoots(tempDir, Set.of(), true)) {
			CtmMcmetaLoader.scanDirectory(root, (namespace, path) -> found.add(namespace + ":" + path));
		}
		assertEquals(List.of("minecraft:textures/blocks/stone.png.mcmeta",
				"minecraft:textures/blocks/stone.png.mcmeta"), found);
		assertEquals(List.of(tempDir.resolve("oresources")),
				CtmMcmetaLoader.externalRoots(tempDir, Set.of(tempDir.resolve("resources").toAbsolutePath().normalize()), true));
		assertEquals(List.of(tempDir.resolve("resources")),
				CtmMcmetaLoader.externalRoots(tempDir, Set.of(), false));
	}

	private static final String CTM_MCMETA = "{\"ctm\":{\"ctm_version\":1,\"type\":\"CTM\",\"textures\":[\"blocks/x-ctm\"]}}";

	/** A resource manager over {@code files} (id to content); a PNG has metadata when {@code pngsWithMetadata} lists it. */
	private static net.minecraft.client.resources.IResourceManager manager(java.util.Map<String, String> files, Set<String> pngsWithMetadata) {
		return new net.minecraft.client.resources.IResourceManager() {
			@Override
			public Set<String> getResourceDomains() {
				return Set.of("minecraft");
			}

			@Override
			public net.minecraft.client.resources.IResource getResource(net.minecraft.util.ResourceLocation location) throws java.io.IOException {
				String id = location.toString();
				String content = files.get(id);
				if (content == null) {
					throw new java.io.FileNotFoundException(id);
				}
				java.io.InputStream mcmeta = pngsWithMetadata.contains(id) ? new java.io.ByteArrayInputStream(new byte[0]) : null;
				return new net.minecraft.client.resources.SimpleResource("top", location,
						new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)), mcmeta, null);
			}

			@Override
			public List<net.minecraft.client.resources.IResource> getAllResources(net.minecraft.util.ResourceLocation location) throws java.io.IOException {
				return List.of(getResource(location));
			}
		};
	}

	private static java.util.function.Consumer<java.util.function.BiConsumer<String, String>> lists(String path) {
		return consumer -> consumer.accept("minecraft", path);
	}

	@Test
	void pngOverriddenWithoutMetadataByAHigherPackGetsNoDefinition() {
		com.evilctm.testutil.McBootstrap.ensure();
		// the lower (mod) pack has x.png, x.png.mcmeta and x-ctm.png; the upper pack retextures x.png only
		CtmMcmetaLoader loader = new CtmMcmetaLoader(manager(java.util.Map.of(
				"minecraft:textures/blocks/x.png", "png",
				"minecraft:textures/blocks/x.png.mcmeta", CTM_MCMETA), Set.of()));
		loader.loadAll("mod", 0, lists("textures/blocks/x.png.mcmeta"));
		assertEquals(0, loader.finish().size());
	}

	@Test
	void sameMetadataInTwoPacksGivesOneDefinitionWithTheHigherPriority() {
		com.evilctm.testutil.McBootstrap.ensure();
		CtmMcmetaLoader loader = new CtmMcmetaLoader(manager(java.util.Map.of(
				"minecraft:textures/blocks/x.png", "png",
				"minecraft:textures/blocks/x.png.mcmeta", CTM_MCMETA), Set.of("minecraft:textures/blocks/x.png")));
		loader.loadAll("low", 0, lists("textures/blocks/x.png.mcmeta"));
		loader.loadAll("high", 1, lists("textures/blocks/x.png.mcmeta"));
		loader.loadAll("resources", -1, lists("textures/blocks/x.png.mcmeta"));
		List<CtmDefinition> definitions = loader.finish();
		assertEquals(1, definitions.size());
		assertEquals(1, definitions.get(0).getPackPriority());
		assertEquals("high", definitions.get(0).getPackId());
	}
}
