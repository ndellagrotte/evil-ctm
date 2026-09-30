/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.evilctm.client.resource.ResourceRedirectHandler;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Verifies the sprite redirect mapping used for both OptiFine (optifine/) and MCPatcher
 * (mcpatcher/) CTM packs: {@code evilctm_reserved/} prefixed sprite ids are mapped back to
 * their real pack path so they load from the resource pack.
 */
class ResourceRedirectHandlerTest {

	@Test
	void optifinePathsRedirectBack() {
		ResourceLocation id = new ResourceLocation("minecraft", "textures/evilctm_reserved/optifine/ctm/default/glass/1");
		ResourceLocation redirected = ResourceRedirectHandler.redirect(id);
		assertEquals("optifine/ctm/default/glass/1", redirected.getPath());
		assertEquals("minecraft", redirected.getNamespace());
	}

	@Test
	void mcpatcherPathsRedirectBack() {
		ResourceLocation id = new ResourceLocation("minecraft", "textures/evilctm_reserved/mcpatcher/ctm/cobblestone/default/1");
		ResourceLocation redirected = ResourceRedirectHandler.redirect(id);
		assertEquals("mcpatcher/ctm/cobblestone/default/1", redirected.getPath());
	}

	@Test
	void nonRedirectPathsUnchanged() {
		ResourceLocation plain = new ResourceLocation("minecraft", "textures/block/cobblestone");
		assertEquals(plain, ResourceRedirectHandler.redirect(plain));
	}

	@Test
	void unprefixedReservedPathGetsOptifinePrefix() {
		// OptiFine packs strip "optifine/" in parseTiles, so a reserved path without a known
		// prefix is re-prefixed with optifine/ to restore the pre-mcpatcher behavior.
		ResourceLocation id = new ResourceLocation("minecraft", "textures/evilctm_reserved/ctm/default/glass/blue/42");
		ResourceLocation redirected = ResourceRedirectHandler.redirect(id);
		assertEquals("optifine/ctm/default/glass/blue/42", redirected.getPath());
		assertEquals("minecraft", redirected.getNamespace());
	}

	@Test
	void emissiveCompanionOfMcpatcherTileRedirects() {
		ResourceLocation sprite = new ResourceLocation("minecraft", "evilctm_reserved/mcpatcher/ctm/x/0");
		ResourceLocation companion = EmissiveSuffixLoader.companionId(sprite, "_e");
		assertEquals("evilctm_reserved/mcpatcher/ctm/x/0_e", companion.getPath());
		ResourceLocation image = new ResourceLocation(companion.getNamespace(), "textures/" + companion.getPath() + ".png");
		assertEquals("mcpatcher/ctm/x/0_e.png", ResourceRedirectHandler.redirect(image).getPath());
	}
}
