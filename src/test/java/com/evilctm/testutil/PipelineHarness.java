/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.client.pipeline.QuadPipeline;
import com.evilctm.client.resource.CtmPropertiesLoader;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Loads OptiFine rules from strings through the real loader dispatch, sort and holder building, and runs the pipeline.
 * Sprites are {@link TestSprites} created on demand by id; each {@link #addRule} call is its own pack, in increasing
 * pack priority.
 */
public class PipelineHarness {
	private final List<CtmPropertiesLoader.LoadedRule<?>> rules = new ArrayList<>();
	private final Map<String, TextureAtlasSprite> sprites = new ConcurrentHashMap<>();
	private int packPriority;

	public PipelineHarness() {
		McBootstrap.ensure();
		EvilCtmClient.registerLoaders();
	}

	/**
	 * @param resourceId e.g. {@code minecraft:optifine/ctm/glass/glass.properties}
	 * @return true when the rule loaded (valid)
	 */
	public boolean addRule(String resourceId, String propertiesText) {
		Properties properties = new Properties();
		try {
			properties.load(new StringReader(propertiesText));
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
		int priority = packPriority++;
		CtmPropertiesLoader.LoadedRule<?> rule = CtmPropertiesLoader.load(properties, new ResourceLocation(resourceId), new TestPack("pack" + priority), priority, null);
		if (rule == null) {
			return false;
		}
		rules.add(rule);
		for (ResourceLocation dependency : rule.properties().getSpriteDependencies()) {
			sprite(dependency.toString());
		}
		return true;
	}

	/** Builds holders for every added rule and publishes them. */
	public PipelineHarness publish() {
		List<QuadProcessors.ProcessorHolder> holders = CtmPropertiesLoader.buildHolders(rules, id -> sprite(id.toString()));
		QuadProcessors.reload(holders);
		LayerRouter.refreshActive();
		return this;
	}

	public int ruleCount() {
		return rules.size();
	}

	/** The test sprite with this id (e.g. {@code minecraft:blocks/glass}), created on first use. */
	public TextureAtlasSprite sprite(String id) {
		String key = new ResourceLocation(id).toString();
		return sprites.computeIfAbsent(key, TestSprites::create);
	}

	public PipelineHarness gate(FakeGate gate) {
		LayerRouter.setGateForTests(gate);
		return this;
	}

	public List<BakedQuad> run(IBlockState state, BlockPos pos, IBlockAccess access, BlockRenderLayer layer, EnumFacing side, List<BakedQuad> quads) {
		return QuadPipeline.transform(state, pos, access, layer, side, quads);
	}

	/** Clears published processors (call from {@code @AfterEach}). */
	public static void reset() {
		QuadProcessors.reload(List.of());
		LayerRouter.setGateForTests(null);
		LayerRouter.refreshActive();
	}
}
