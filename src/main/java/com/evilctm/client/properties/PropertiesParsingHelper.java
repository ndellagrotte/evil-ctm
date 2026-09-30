/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.processor.OrientationMode;
import com.evilctm.client.processor.Symmetry;
import com.evilctm.client.resource.ResourceRedirectHandler;
import com.evilctm.client.util.IntRangeParser;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public final class PropertiesParsingHelper {
	private static final Pattern NUMERIC_ID = Pattern.compile("^[0-9]+$");
	private static final Pattern META_LIST = Pattern.compile("^[0-9,\\-]+$");
	public static final Predicate<IBlockState> EMPTY_BLOCK_STATE_PREDICATE = state -> false;

	private PropertiesParsingHelper() {
	}

	@Nullable
	public static Set<ResourceLocation> parseMatchTiles(Properties properties, String propertyKey, ResourceLocation fileLocation, String packId) {
		String matchTilesStr = properties.getProperty(propertyKey);
		if (matchTilesStr == null) {
			return null;
		}

		String[] matchTileStrs = matchTilesStr.trim().split(" ");
		if (matchTileStrs.length != 0) {
			String basePath = FilenameUtils.getPath(fileLocation.getPath());
			ObjectOpenHashSet<ResourceLocation> set = new ObjectOpenHashSet<>();

			for (int i = 0; i < matchTileStrs.length; i++) {
				String matchTileStr = matchTileStrs[i];
				if (matchTileStr.isEmpty()) {
					continue;
				}

				String[] parts = matchTileStr.split(":", 2);
				String namespace = null;
				String path;
				if (parts.length > 1) {
					namespace = parts[0];
					path = parts[1];
				} else {
					path = parts[0];
				}

				if (path.endsWith(".png")) {
					path = path.substring(0, path.length() - 4);
				}

				if (namespace == null) {
					if (path.startsWith("assets/minecraft/")) {
						path = path.substring(17);
					} else if (path.startsWith("./")) {
						// MCPatcher-style relative matchTiles ("matchTiles=./1.png") name a tile
						// inside the properties directory. OptiFine resolves these to the tile's own
						// sprite id (basePath + name), matching CTM tile output; it does NOT infer a
						// vanilla "blocks/<block>" sprite from the ctm/<block>/ path.
						path = basePath + path.substring(2);
					} else if (path.startsWith("~/")) {
						path = "optifine/" + path.substring(2);
					} else if (path.startsWith("/")) {
						path = "optifine/" + path.substring(1);
					}
				}

				if (path.startsWith("textures/")) {
					path = path.substring(9);
				} else if (path.startsWith("optifine/")) {
					path = ResourceRedirectHandler.SPRITE_PATH_START + path.substring(9);
					if (namespace == null) {
						namespace = fileLocation.getNamespace();
					}
				} else if (path.startsWith("mcpatcher/")) {
					// Relative tiles inside an MCPatcher ctm directory are registered in the atlas
					// under the evilctm_reserved/ redirect (see BaseCtmProperties.parseTiles), so a
					// relative matchTiles must resolve to the same id to chain onto tile output.
					path = ResourceRedirectHandler.SPRITE_PATH_START + path;
					if (namespace == null) {
						namespace = fileLocation.getNamespace();
					}
				} else if (!path.contains("/")) {
					// 1.12.2 vanilla block textures live under blocks/ (plural)
					path = "blocks/" + path;
				}

				if (namespace == null) {
					namespace = "minecraft";
				}

				set.add(new ResourceLocation(namespace, path));
			}

			set.trim();
			return set;
		}
		return Collections.emptySet();
	}

	@Nullable
	public static Predicate<IBlockState> parseBlockStates(Properties properties, String propertyKey, ResourceLocation fileLocation, String packId) {
		String blockStatesStr = properties.getProperty(propertyKey);
		if (blockStatesStr == null) {
			return null;
		}
		return parseBlockSpec(blockStatesStr, propertyKey, fileLocation, packId);
	}

	/**
	 * Parses a block spec: space-separated tokens {@code blockref(:part)*}, where {@code blockref} is
	 * {@code [ns:]name} or a numeric id (0-4095) and each part is either a {@code key=v1,v2} property filter or
	 * a metadata list with ranges such as {@code 1,3-5}. Returns {@link #EMPTY_BLOCK_STATE_PREDICATE} if nothing usable was found.
	 */
	public static Predicate<IBlockState> parseBlockSpec(String blockStatesStr, String propertyKey, ResourceLocation fileLocation, String packId) {
		String[] blockStateStrs = blockStatesStr.trim().split("\\s+");
		ReferenceOpenHashSet<Block> wholeBlocks = new ReferenceOpenHashSet<>();
		Reference2ObjectOpenHashMap<Block, ObjectArrayList<Predicate<IBlockState>>> filters = new Reference2ObjectOpenHashMap<>();

		for (int i = 0; i < blockStateStrs.length; i++) {
			String blockStateStr = blockStateStrs[i].trim();
			if (blockStateStr.isEmpty()) {
				continue;
			}
			String where = "in '" + propertyKey + "' element '" + blockStateStr + "' at index " + i + " in file '" + fileLocation + "' in pack '" + packId + "'";

			String[] parts = blockStateStr.split(":");
			Block block;
			int startIndex;
			if (NUMERIC_ID.matcher(parts[0]).matches()) {
				int id;
				try {
					id = Integer.parseInt(parts[0]);
				} catch (NumberFormatException e) {
					id = -1;
				}
				if (id < 0 || id > 4095) {
					EvilCtmClient.LOGGER.warn("Block id '" + parts[0] + "' out of range 0-4095 " + where);
					continue;
				}
				block = Block.getBlockById(id);
				if (id != 0 && Block.getIdFromBlock(block) != id) {
					EvilCtmClient.LOGGER.warn("Unknown block id '" + id + "' " + where);
					continue;
				}
				startIndex = 1;
			} else {
				ResourceLocation blockId;
				if (parts.length > 1 && !parts[1].contains("=") && !META_LIST.matcher(parts[1]).matches()) {
					blockId = new ResourceLocation(parts[0], parts[1]);
					startIndex = 2;
				} else {
					blockId = new ResourceLocation(parts[0]);
					startIndex = 1;
				}
				if (!Block.REGISTRY.containsKey(blockId)) {
					EvilCtmClient.LOGGER.warn("Unknown block '" + blockId + "' " + where);
					continue;
				}
				block = Block.REGISTRY.getObject(blockId);
			}

			if (wholeBlocks.contains(block)) {
				continue;
			}

			IntPredicate metaPredicate = null;
			Object2ObjectOpenHashMap<IProperty<?>, ObjectOpenHashSet<Comparable<?>>> propertyMap = null;
			boolean bad = false;
			for (int j = startIndex; j < parts.length && !bad; j++) {
				String part = parts[j];
				if (part.isEmpty()) {
					continue;
				}
				if (part.contains("=")) {
					String[] propertyParts = part.split("=", 2);
					IProperty<?> property = block.getBlockState().getProperty(propertyParts[0]);
					if (property == null) {
						EvilCtmClient.LOGGER.warn("Unknown block property '" + propertyParts[0] + "' for block '" + block.getRegistryName() + "' " + where);
						bad = true;
						break;
					}
					if (propertyMap == null) {
						propertyMap = new Object2ObjectOpenHashMap<>();
					}
					ObjectOpenHashSet<Comparable<?>> valueSet = propertyMap.computeIfAbsent(property, p -> new ObjectOpenHashSet<>());
					for (String propertyValueStr : propertyParts[1].split(",")) {
						com.google.common.base.Optional<?> optionalValue = property.parseValue(propertyValueStr);
						if (optionalValue.isPresent()) {
							valueSet.add((Comparable<?>) optionalValue.get());
						} else {
							EvilCtmClient.LOGGER.warn("Invalid block property value '" + propertyValueStr + "' for property '" + propertyParts[0] + "' for block '" + block.getRegistryName() + "' " + where);
							bad = true;
							break;
						}
					}
				} else if (META_LIST.matcher(part).matches()) {
					IntPredicate parsed = IntRangeParser.parse(part);
					if (parsed == null) {
						EvilCtmClient.LOGGER.warn("Invalid metadata list '" + part + "' for block '" + block.getRegistryName() + "' " + where);
						bad = true;
					} else {
						metaPredicate = metaPredicate == null ? parsed : and(metaPredicate, parsed);
					}
				} else {
					EvilCtmClient.LOGGER.warn("Invalid block property definition '" + part + "' for block '" + block.getRegistryName() + "' " + where);
					bad = true;
				}
			}
			if (bad) {
				continue;
			}

			if (metaPredicate == null && propertyMap == null) {
				wholeBlocks.add(block);
				filters.remove(block);
				continue;
			}
			filters.computeIfAbsent(block, b -> new ObjectArrayList<>()).add(tokenPredicate(metaPredicate, propertyMap));
		}

		if (wholeBlocks.isEmpty() && filters.isEmpty()) {
			return EMPTY_BLOCK_STATE_PREDICATE;
		}
		if (filters.isEmpty()) {
			if (wholeBlocks.size() == 1) {
				Block block = wholeBlocks.iterator().next();
				return state -> state.getBlock() == block;
			}
			wholeBlocks.trim();
			return state -> wholeBlocks.contains(state.getBlock());
		}

		Reference2ReferenceOpenHashMap<Block, Predicate<IBlockState>> predicateMap = new Reference2ReferenceOpenHashMap<>();
		wholeBlocks.forEach(block -> predicateMap.put(block, state -> true));
		filters.forEach((block, list) -> {
			@SuppressWarnings("unchecked")
			Predicate<IBlockState>[] array = list.toArray(new Predicate[0]);
			predicateMap.put(block, state -> {
				for (Predicate<IBlockState> predicate : array) {
					if (predicate.test(state)) {
						return true;
					}
				}
				return false;
			});
		});
		return state -> {
			Predicate<IBlockState> predicate = predicateMap.get(state.getBlock());
			return predicate != null && predicate.test(state);
		};
	}

	private static IntPredicate and(IntPredicate a, IntPredicate b) {
		return v -> a.test(v) && b.test(v);
	}

	private static Predicate<IBlockState> tokenPredicate(@Nullable IntPredicate metaPredicate, @Nullable Object2ObjectOpenHashMap<IProperty<?>, ObjectOpenHashSet<Comparable<?>>> propertyMap) {
		List<Map.Entry<IProperty<?>, ObjectOpenHashSet<Comparable<?>>>> entryList = null;
		if (propertyMap != null) {
			entryList = new ObjectArrayList<>(propertyMap.entrySet());
			entryList.forEach(entry -> entry.getValue().trim());
		}
		List<Map.Entry<IProperty<?>, ObjectOpenHashSet<Comparable<?>>>> entries = entryList;
		return state -> {
			if (metaPredicate != null && !metaPredicate.test(metaOf(state))) {
				return false;
			}
			if (entries != null) {
				Map<IProperty<?>, Comparable<?>> stateProperties = state.getProperties();
				for (Map.Entry<IProperty<?>, ObjectOpenHashSet<Comparable<?>>> entry : entries) {
					Comparable<?> targetValue = stateProperties.get(entry.getKey());
					if (targetValue != null && !entry.getValue().contains(targetValue)) {
						return false;
					}
				}
			}
			return true;
		};
	}

	/** The block's metadata for {@code state}, or -1 if it cannot be computed. */
	public static int metaOf(IBlockState state) {
		try {
			return state.getBlock().getMetaFromState(state);
		} catch (RuntimeException e) {
			return -1;
		}
	}

	@Nullable
	public static Symmetry parseSymmetry(Properties properties, String propertyKey, ResourceLocation fileLocation, String packId) {
		String symmetryStr = properties.getProperty(propertyKey);
		if (symmetryStr == null) {
			return null;
		}

		try {
			return Symmetry.valueOf(symmetryStr.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			EvilCtmClient.LOGGER.warn("Unknown '" + propertyKey + "' value '" + symmetryStr + "' in file '" + fileLocation + "' in pack '" + packId + "'");
		}
		return null;
	}

	@Nullable
	public static OrientationMode parseOrientationMode(Properties properties, String propertyKey, ResourceLocation fileLocation, String packId) {
		String orientationModeStr = properties.getProperty(propertyKey);
		if (orientationModeStr == null) {
			return null;
		}

		try {
			return OrientationMode.valueOf(orientationModeStr.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			EvilCtmClient.LOGGER.warn("Unknown '" + propertyKey + "' value '" + orientationModeStr + "' in file '" + fileLocation + "' in pack '" + packId + "'");
		}
		return null;
	}

	public static boolean parseOptifineOnly(Properties properties, ResourceLocation fileLocation) {
		if (!fileLocation.getNamespace().equals("minecraft")) {
			return false;
		}

		String optifineOnlyStr = properties.getProperty("optifineOnly");
		if (optifineOnlyStr == null) {
			return false;
		}

		return Boolean.parseBoolean(optifineOnlyStr.trim());
	}
}
