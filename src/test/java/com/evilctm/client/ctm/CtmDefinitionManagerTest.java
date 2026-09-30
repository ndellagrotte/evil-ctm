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
}
