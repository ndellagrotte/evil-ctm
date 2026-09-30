/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** 16x16 sprites on distinct cells of a 1024x1024 atlas, so every sprite has its own UV range. */
public final class TestSprites {
	public static final int ATLAS = 1024;
	public static final int SIZE = 16;
	private static final int CELLS_PER_ROW = ATLAS / SIZE;
	private static final AtomicInteger NEXT_CELL = new AtomicInteger();

	private TestSprites() {
	}

	public static final class TestSprite extends TextureAtlasSprite {
		public TestSprite(String name) {
			super(name);
		}
	}

	/** A sprite on the next free cell (cells wrap after 4096). */
	public static TestSprite create(String name) {
		int cell = NEXT_CELL.getAndIncrement() % (CELLS_PER_ROW * CELLS_PER_ROW);
		TestSprite sprite = new TestSprite(name);
		sprite.setIconWidth(SIZE);
		sprite.setIconHeight(SIZE);
		sprite.initSprite(ATLAS, ATLAS, (cell % CELLS_PER_ROW) * SIZE, (cell / CELLS_PER_ROW) * SIZE, false);
		return sprite;
	}

	public static List<TestSprite> grid(String... names) {
		List<TestSprite> sprites = new ArrayList<>(names.length);
		for (String name : names) {
			sprites.add(create(name));
		}
		return sprites;
	}
}
