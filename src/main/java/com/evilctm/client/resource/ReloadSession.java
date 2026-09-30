/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.ctm.CtmDefinition;
import net.minecraft.client.resources.IResourcePack;

/**
 * The resource scans of one reload: the OptiFine rule result and the CTM-mod definitions. It is created in the
 * block-atlas Pre stitch, consumed (and dropped) in Post, so each scan runs exactly once per reload.
 */
public final class ReloadSession {
	private static final Set<String> LOGGED_PACKS = ConcurrentHashMap.newKeySet();

	private final CtmPropertiesLoader.LoadingResult rules;
	private final List<CtmDefinition> definitions;

	private ReloadSession(CtmPropertiesLoader.LoadingResult rules, List<CtmDefinition> definitions) {
		this.rules = rules;
		this.definitions = definitions;
	}

	/** Runs each scan once. {@code definitionScan} may be null when CTM-mod textures are disabled. */
	public static ReloadSession scan(Supplier<CtmPropertiesLoader.LoadingResult> ruleScan, Supplier<List<CtmDefinition>> definitionScan) {
		CtmPropertiesLoader.LoadingResult rules = ruleScan.get();
		List<CtmDefinition> definitions = definitionScan == null ? List.of() : definitionScan.get();
		return new ReloadSession(rules, definitions);
	}

	public CtmPropertiesLoader.LoadingResult rules() {
		return rules;
	}

	public List<CtmDefinition> definitions() {
		return definitions;
	}

	/** Logs once per pack name that the pack cannot be scanned for CTM files (it is not file-backed). */
	public static void logUnscannablePack(IResourcePack pack) {
		String name = pack.getPackName();
		if (LOGGED_PACKS.add(name)) {
			EvilCtmClient.LOGGER.info("Resource pack '{}' is not file-backed, so its CTM files are not scanned", name);
		}
	}
}
