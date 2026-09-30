/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.testutil.McBootstrap;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReloadSessionTest {
	@BeforeAll
	static void setUp() {
		McBootstrap.ensure();
		EvilCtmClient.registerLoaders();
	}

	private static List<IResourcePack> pack(Path root) throws Exception {
		Path dir = root.resolve("assets/test/optifine/ctm");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("a.properties"), "method=fixed\nmatchBlocks=stone\ntiles=0\n");
		Files.writeString(dir.resolve("b.properties"), "method=fixed\nmatchBlocks=dirt\ntiles=0\n");
		Path builtin = root.resolve("assets/evilctm/optifine/ctm/default/x");
		Files.createDirectories(builtin);
		Files.writeString(builtin.resolve("c.properties"), "method=fixed\nmatchBlocks=glass\ntiles=0\n");
		return List.of(new FolderResourcePack(root.toFile()));
	}

	@Test
	void consecutiveScansAreIdentical(@TempDir Path root) throws Exception {
		List<IResourcePack> packs = pack(root);
		var first = CtmPropertiesLoader.loadFromPacks(packs, null, true);
		var second = CtmPropertiesLoader.loadFromPacks(packs, null, true);
		assertEquals(3, first.getRules().size());
		assertEquals(first.countByMethod(), second.countByMethod());
		assertEquals(first.getBlockAtlasSpriteDependencies(), second.getBlockAtlasSpriteDependencies());
		List<String> a = first.getRules().stream().map(r -> String.valueOf(((com.evilctm.client.properties.BaseCtmProperties) r.properties()).isBuiltin())).toList();
		List<String> b = second.getRules().stream().map(r -> String.valueOf(((com.evilctm.client.properties.BaseCtmProperties) r.properties()).isBuiltin())).toList();
		assertEquals(a, b);
	}

	@Test
	void builtinToggleSkipsDefaultRules(@TempDir Path root) throws Exception {
		List<IResourcePack> packs = pack(root);
		assertEquals(2, CtmPropertiesLoader.loadFromPacks(packs, null, false).getRules().size());
	}

	@Test
	void builtinSkipPredicate() {
		ResourceLocation builtin = new ResourceLocation("evilctm", "optifine/ctm/default/glass/glass.properties");
		ResourceLocation other = new ResourceLocation("evilctm", "optifine/ctm/custom/glass.properties");
		assertTrue(CtmPropertiesLoader.isSkippedBuiltin(builtin, false));
		assertEquals(false, CtmPropertiesLoader.isSkippedBuiltin(builtin, true));
		assertEquals(false, CtmPropertiesLoader.isSkippedBuiltin(other, false));
	}

	private static java.util.function.Supplier<ReloadSession> countingScanner(AtomicInteger scans) {
		var empty = CtmPropertiesLoader.loadFromPacks(List.of(), null, true);
		return () -> {
			scans.incrementAndGet();
			return ReloadSession.scan(() -> empty, null);
		};
	}

	@Test
	void preThenPostScansOnceAndPostGetsThePreSession() {
		AtomicInteger scans = new AtomicInteger();
		var scanner = countingScanner(scans);
		ReloadSession.Pending pending = new ReloadSession.Pending();
		ReloadSession began = pending.begin(scanner);
		assertSame(began, pending.consume(scanner));
		assertEquals(1, scans.get());
	}

	@Test
	void postWithoutPreScansOnce() {
		AtomicInteger scans = new AtomicInteger();
		ReloadSession.Pending pending = new ReloadSession.Pending();
		pending.consume(countingScanner(scans));
		assertEquals(1, scans.get());
	}

	@Test
	void aConsumedSessionIsNeverReusedByTheNextReload() {
		AtomicInteger scans = new AtomicInteger();
		var scanner = countingScanner(scans);
		ReloadSession.Pending pending = new ReloadSession.Pending();
		ReloadSession first = pending.begin(scanner);
		assertSame(first, pending.consume(scanner));
		ReloadSession second = pending.consume(scanner);
		assertEquals(2, scans.get());
		assertTrue(second != first);
	}

	@Test
	void aSecondPreReplacesAnUnconsumedSession() {
		AtomicInteger scans = new AtomicInteger();
		var scanner = countingScanner(scans);
		ReloadSession.Pending pending = new ReloadSession.Pending();
		pending.begin(scanner);
		ReloadSession newer = pending.begin(scanner);
		assertSame(newer, pending.consume(scanner));
		assertEquals(2, scans.get());
	}

	@Test
	void packFormatTwoPacksAreScanned(@TempDir Path root) throws Exception {
		List<IResourcePack> packs = pack(root);
		IResourcePack legacy = new net.minecraft.client.resources.LegacyV2Adapter(packs.get(0));
		assertEquals(2, CtmPropertiesLoader.loadFromPacks(List.of(legacy), null, false).getRules().size());
	}

	@Test
	void sessionRunsEachScanOnce() {
		AtomicInteger rules = new AtomicInteger();
		AtomicInteger defs = new AtomicInteger();
		var empty = CtmPropertiesLoader.loadFromPacks(List.of(), null, true);
		ReloadSession session = ReloadSession.scan(() -> {
			rules.incrementAndGet();
			return empty;
		}, () -> {
			defs.incrementAndGet();
			return List.of();
		});
		for (int i = 0; i < 3; i++) {
			assertSame(empty, session.rules());
			session.definitions();
		}
		assertEquals(1, rules.get());
		assertEquals(1, defs.get());
	}

	@Test
	void disabledDefinitionScanIsNotRun() {
		var empty = CtmPropertiesLoader.loadFromPacks(List.of(), null, true);
		assertTrue(ReloadSession.scan(() -> empty, null).definitions().isEmpty());
	}
}
