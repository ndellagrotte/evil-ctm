/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.ctm;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CtmDefinitionManagerTest {
	private static final IResourceManager EMPTY = new IResourceManager() {
		@Override
		public Set<String> getResourceDomains() {
			return Set.of("minecraft");
		}

		@Override
		public IResource getResource(ResourceLocation location) throws IOException {
			throw new java.io.FileNotFoundException(location.toString());
		}

		@Override
		public List<IResource> getAllResources(ResourceLocation location) throws IOException {
			throw new java.io.FileNotFoundException(location.toString());
		}
	};

	@AfterEach
	void tearDown() {
		CtmDefinitionManager.clear();
	}

	@Test
	void reloadReplacesPublishedLogics() {
		CtmCustomLogic logic = new CtmCustomLogic(List.of(), new int[][] {{}}, new CtmCustomLogic.OutputFace[0]);
		CtmDefinitionManager.registerLogic("t:a", logic);
		assertSame(logic, CtmDefinitionManager.getLogic("t:a"));
		CtmDefinitionManager.reload(EMPTY);
		assertNull(CtmDefinitionManager.getLogic("t:a"));
		assertTrue(CtmDefinitionManager.isEmpty());
	}

	private static final String LOGIC = "{\"positions\":[{\"id\":\"TOP\",\"directions\":[\"up\"]}],"
			+ "\"submaps\":{\"\":{\"type\":\"grid\",\"width\":2,\"height\":1}},"
			+ "\"rules\":[{\"output\":\"0,0\",\"connected\":[],\"unconnected\":[\"TOP\"]},"
			+ "{\"output\":\"1,0\",\"connected\":[\"TOP\"],\"unconnected\":[]}]}";

	@Test
	void malformedCtmJsonInOneDomainDoesNotStopTheOthers() {
		java.util.Map<String, String> files = new java.util.HashMap<>();
		files.put("aaa:ctm.json", "{ this is not json");
		files.put("bbb:ctm.json", "[1, 2]");
		files.put("ccc:ctm.json", "{\"logics\":[{\"bad\":1}, \"good\"]}");
		files.put("ccc:ctm_logic/good.json", LOGIC);
		IResourceManager manager = new IResourceManager() {
			@Override
			public Set<String> getResourceDomains() {
				return new java.util.LinkedHashSet<>(List.of("aaa", "bbb", "ccc"));
			}

			@Override
			public IResource getResource(ResourceLocation location) throws IOException {
				String content = files.get(location.toString());
				if (content == null) {
					throw new java.io.FileNotFoundException(location.toString());
				}
				return new net.minecraft.client.resources.SimpleResource("test", location,
						new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)), null, null);
			}

			@Override
			public List<IResource> getAllResources(ResourceLocation location) throws IOException {
				return List.of(getResource(location));
			}
		};
		CtmDefinitionManager.reload(manager);
		assertTrue(CtmDefinitionManager.getLogic("ccc:good") != null);
	}
}
