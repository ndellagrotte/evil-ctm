/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.ctm.CtmDefinition;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;

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

	/**
	 * The session between the Pre and Post stitch of one reload. Pre {@link #begin begins} it; Post
	 * {@link #consume consumes} it (so it is never reused by a later reload) or, when Pre did not run, scans once.
	 * Only touched on the client thread.
	 */
	public static final class Pending {
		private ReloadSession session;

		/** Scans and holds the result for {@link #consume}; replaces an unconsumed earlier session. */
		public ReloadSession begin(Supplier<ReloadSession> scanner) {
			session = scanner.get();
			return session;
		}

		/** The session {@link #begin} left, dropped from this holder; otherwise a fresh scan. */
		public ReloadSession consume(Supplier<ReloadSession> scanner) {
			ReloadSession s = session;
			session = null;
			return s != null ? s : scanner.get();
		}
	}

	public CtmPropertiesLoader.LoadingResult rules() {
		return rules;
	}

	public List<CtmDefinition> definitions() {
		return definitions;
	}

	/**
	 * The pack whose files to enumerate: packs with {@code pack_format} 2 (and such mod packs) are wrapped in a
	 * {@link LegacyV2Adapter}, which is not file-backed itself. Only for listing files; reads and names still go
	 * through the pack as given.
	 */
	public static IResourcePack unwrapForScan(IResourcePack pack) {
		while (pack instanceof LegacyV2Adapter adapter) {
			pack = adapter.getUnadaptedPack();
		}
		return pack;
	}

	/**
	 * The zip or folder to enumerate for {@code pack}, or {@code null} (logged once) when it has none. Some mods
	 * create {@link AbstractResourcePack}s with no backing file, so a file-backed type is not enough.
	 */
	@Nullable
	public static File scanRoot(IResourcePack pack) {
		if (unwrapForScan(pack) instanceof AbstractResourcePack abstractPack) {
			File file = abstractPack.getResourcePackFile();
			if (file != null) {
				return file;
			}
		}
		logUnscannablePack(pack);
		return null;
	}

	/** Logs once per pack name that the pack cannot be scanned for CTM files (it is not file-backed). */
	public static void logUnscannablePack(IResourcePack pack) {
		String name = pack.getPackName();
		if (LOGGED_PACKS.add(name)) {
			EvilCtmClient.LOGGER.info("Resource pack '{}' is not file-backed, so its CTM files are not scanned", name);
		}
	}
}
