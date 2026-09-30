/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.overlay;

import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import com.evilctm.api.client.LayerTargetingProcessor;
import com.evilctm.api.client.ProcessingDataKey;
import com.evilctm.api.client.ProcessingDataKeyRegistry;
import com.evilctm.api.client.ProcessingDataProvider;
import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.processor.AbstractQuadProcessor;
import com.evilctm.client.processor.AbstractQuadProcessorFactory;
import com.evilctm.client.processor.ConnectionPredicate;
import com.evilctm.client.processor.DirectionMaps;
import com.evilctm.client.processor.ProcessingDataKeys;
import com.evilctm.client.processor.ProcessingPredicate;
import com.evilctm.client.properties.overlay.OverlayPropertiesSection;
import com.evilctm.client.properties.overlay.StandardOverlayCtmProperties;
import com.evilctm.client.util.OverlayQuads;
import com.evilctm.client.util.RenderUtil;
import com.evilctm.client.util.SpriteCalculator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * {@code method=overlay}: the 17-tile edge overlay. Each side of the face whose neighbour applies the overlay (see
 * {@link #appliesOverlay}) contributes an edge tile; corners are added where two sides of the same overlay meet a
 * diagonal neighbour that applies it. At most 4 tiles are emitted per face.
 */
public class StandardOverlayQuadProcessor extends AbstractQuadProcessor implements LayerTargetingProcessor {
	public static final ProcessingDataKey<SpriteCollector> SPRITE_COLLECTOR = ProcessingDataKeyRegistry.get().registerKey(
			EvilCtmClient.asId("overlay_sprite_collector"), SpriteCollector::new, SpriteCollector::clear);

	protected static final int TILES = 17;
	private static final int FACES = 6;

	@Nullable
	protected final Set<String> matchTilesSet;
	@Nullable
	protected final Predicate<IBlockState> matchBlocksPredicate;
	@Nullable
	protected final Set<String> connectTilesSet;
	@Nullable
	protected final Predicate<IBlockState> connectBlocksPredicate;
	protected final ConnectionPredicate connectionPredicate;

	protected final int tintIndex;
	@Nullable
	protected final IBlockState tintBlock;
	protected final BlockRenderLayer layer;
	/** {@code faceQuads[tile * 6 + face.getIndex()]}; null for null or missing tiles. */
	protected final BakedQuad[] faceQuads;

	public StandardOverlayQuadProcessor(TextureAtlasSprite[] sprites, ProcessingPredicate processingPredicate, @Nullable Set<ResourceLocation> matchTilesSet, @Nullable Predicate<IBlockState> matchBlocksPredicate, @Nullable Set<ResourceLocation> connectTilesSet, @Nullable Predicate<IBlockState> connectBlocksPredicate, ConnectionPredicate connectionPredicate, int tintIndex, @Nullable IBlockState tintBlock, BlockRenderLayer layer) {
		super(sprites, processingPredicate);
		this.matchTilesSet = toNames(matchTilesSet);
		this.matchBlocksPredicate = matchBlocksPredicate;
		this.connectTilesSet = toNames(connectTilesSet);
		this.connectBlocksPredicate = connectBlocksPredicate;
		this.connectionPredicate = connectionPredicate;
		this.tintIndex = tintIndex;
		this.tintBlock = tintBlock;
		this.layer = layer;

		// Missing sprites become null: there is no functional difference for this processor, and null is cheaper.
		for (int i = 0; i < sprites.length; i++) {
			if (RenderUtil.isMissingSprite(sprites[i])) {
				sprites[i] = null;
			}
		}
		faceQuads = new BakedQuad[sprites.length * FACES];
		for (int tile = 0; tile < sprites.length; tile++) {
			TextureAtlasSprite sprite = sprites[tile];
			if (sprite != null) {
				System.arraycopy(OverlayQuads.faces(sprite), 0, faceQuads, tile * FACES, FACES);
			}
		}
	}

	/** Forces registration of {@link #SPRITE_COLLECTOR}. */
	public static void init() {
	}

	@Nullable
	private static Set<String> toNames(@Nullable Set<ResourceLocation> ids) {
		if (ids == null) {
			return null;
		}
		ObjectOpenHashSet<String> names = new ObjectOpenHashSet<>(ids.size());
		for (ResourceLocation id : ids) {
			names.add(id.toString());
		}
		names.trim();
		return names;
	}

	@Override
	public BlockRenderLayer getTargetLayer() {
		return layer;
	}

	@Override
	public ProcessingResult processQuad(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, int pass, ProcessingContext context) {
		if (context.currentLayer() != layer) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		return super.processQuad(quad, sprite, level, pos, appearanceState, state, rand, pass, context);
	}

	@Override
	public ProcessingResult processQuadInner(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, int pass, ProcessingContext context) {
		EnumFacing lightFace = quad.getFace();
		if (lightFace == null) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		SpriteCollector collector = getSprites(level, pos, appearanceState, state, lightFace, sprite, DirectionMaps.getMap(lightFace)[0], context);
		if (collector != null) {
			int faceIndex = lightFace.getIndex();
			for (int i = 0; i < collector.amount; i++) {
				BakedQuad base = faceQuads[collector.tiles[i] * FACES + faceIndex];
				context.emitOverlay(layer, OverlayQuads.tinted(base, tintBlock, tintIndex, level, pos));
			}
			collector.clear();
		}
		return ProcessingResult.NEXT_PROCESSOR;
	}

	protected static boolean matchesAny(Set<String> tiles, TextureAtlasSprite[] sprites) {
		for (TextureAtlasSprite sprite : sprites) {
			if (tiles.contains(sprite.getIconName())) {
				return true;
			}
		}
		return false;
	}

	protected static IBlockState actualState(IBlockState state, IBlockAccess level, BlockPos pos) {
		try {
			return state.getActualState(level, pos);
		} catch (RuntimeException e) {
			return state;
		}
	}

	protected boolean appliesOverlay(BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, EnumFacing face, TextureAtlasSprite quadSprite) {
		// OptiFine never applies overlays from blocks with dynamic bounds. isFullCube is asked instead, which only
		// differs for retracted pistons and shulker boxes among vanilla blocks.
		if (!otherState.isFullCube()) {
			return false;
		}
		if (connectBlocksPredicate != null) {
			if (!connectBlocksPredicate.test(otherAppearanceState)) {
				return false;
			}
		}
		if (connectTilesSet != null) {
			if (!matchesAny(connectTilesSet, SpriteCalculator.getSprites(otherAppearanceState, face))) {
				return false;
			}
		}
		return !connectionPredicate.shouldConnect(level, pos, appearanceState, state, otherPos, otherAppearanceState, otherState, face, quadSprite);
	}

	protected boolean hasSameOverlay(@Nullable IBlockState otherAppearanceState, EnumFacing face) {
		if (otherAppearanceState == null) {
			return false;
		}
		if (matchBlocksPredicate != null) {
			if (!matchBlocksPredicate.test(otherAppearanceState)) {
				return false;
			}
		}
		if (matchTilesSet != null) {
			if (!matchesAny(matchTilesSet, SpriteCalculator.getSprites(otherAppearanceState, face))) {
				return false;
			}
		}
		return true;
	}

	protected boolean appliesOverlayCorner(EnumFacing dir0, EnumFacing dir1, BlockPos.MutableBlockPos mutablePos, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, EnumFacing lightFace, TextureAtlasSprite quadSprite) {
		mutablePos.setPos(pos).move(dir0).move(dir1);
		IBlockState otherState = level.getBlockState(mutablePos);
		IBlockState otherAppearanceState = actualState(otherState, level, mutablePos);
		if (appliesOverlay(mutablePos, otherAppearanceState, otherState, level, pos, appearanceState, state, lightFace, quadSprite)) {
			mutablePos.move(lightFace);
			return !level.getBlockState(mutablePos).isOpaqueCube();
		}
		return false;
	}

	protected SpriteCollector fromTwoSidesAdj(SpriteCollector collector, @Nullable IBlockState appearanceState0, @Nullable IBlockState appearanceState1, EnumFacing dir0, EnumFacing dir1, int sprite, int spriteC01, BlockPos.MutableBlockPos mutablePos, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, EnumFacing lightFace, TextureAtlasSprite quadSprite) {
		add(collector, sprite);
		// OptiFine does not check whether the other two adjacent blocks have the same overlay before trying to apply
		// the corner overlay. Continuity considers this a bug, since it is inconsistent with the other cases, and
		// checks those blocks.
		if ((hasSameOverlay(appearanceState0, lightFace)
				|| hasSameOverlay(appearanceState1, lightFace))
				&& appliesOverlayCorner(dir0, dir1, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite)) {
			add(collector, spriteC01);
		}
		return collector;
	}

	protected SpriteCollector fromOneSide(SpriteCollector collector, @Nullable IBlockState appearanceState0, @Nullable IBlockState appearanceState1, @Nullable IBlockState appearanceState2, EnumFacing dir0, EnumFacing dir1, EnumFacing dir2, int sprite, int spriteC01, int spriteC12, BlockPos.MutableBlockPos mutablePos, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, EnumFacing lightFace, TextureAtlasSprite quadSprite) {
		boolean c01;
		boolean c12;
		if (hasSameOverlay(appearanceState1, lightFace)) {
			c01 = true;
			c12 = true;
		} else {
			c01 = hasSameOverlay(appearanceState0, lightFace);
			c12 = hasSameOverlay(appearanceState2, lightFace);
		}

		add(collector, sprite);
		if (c01 && appliesOverlayCorner(dir0, dir1, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite)) {
			add(collector, spriteC01);
		}
		if (c12 && appliesOverlayCorner(dir1, dir2, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite)) {
			add(collector, spriteC12);
		}
		return collector;
	}

	protected void add(SpriteCollector collector, int tile) {
		if (tile < sprites.length && sprites[tile] != null) {
			collector.add(tile);
		}
	}

	protected static SpriteCollector getCollector(ProcessingDataProvider dataProvider) {
		return dataProvider.getData(SPRITE_COLLECTOR);
	}

	protected SpriteCollector prepareCollector(SpriteCollector collector, int sprite0) {
		add(collector, sprite0);
		return collector;
	}

	protected SpriteCollector prepareCollector(SpriteCollector collector, int sprite0, int sprite1) {
		add(collector, sprite0);
		add(collector, sprite1);
		return collector;
	}

	/**
	 * Reads side {@code dir} and returns its actual state, or {@code null} when the block in front of it is opaque.
	 * Sets {@code applies[0]} when that side applies the overlay.
	 */
	@Nullable
	private IBlockState side(EnumFacing dir, boolean[] applies, BlockPos.MutableBlockPos mutablePos, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, EnumFacing lightFace, TextureAtlasSprite quadSprite) {
		mutablePos.setPos(pos).move(dir).move(lightFace);
		if (level.getBlockState(mutablePos).isOpaqueCube()) {
			applies[0] = false;
			return null;
		}
		mutablePos.setPos(pos).move(dir);
		IBlockState otherState = level.getBlockState(mutablePos);
		IBlockState otherAppearanceState = actualState(otherState, level, mutablePos);
		applies[0] = appliesOverlay(mutablePos, otherAppearanceState, otherState, level, pos, appearanceState, state, lightFace, quadSprite);
		return otherAppearanceState;
	}

	/*
	0:	CORNER D+R
	1:	D
	2:	CORNER L+D
	3:	D R
	4:	L D
	5:	L D R
	6:	L D U
	7:	R
	8:	L D R U
	9:	L
	10:	R U
	11:	L U
	12:	D R U
	13:	L R U
	14:	CORNER R+U
	15:	U
	16:	CORNER L+U
	 */
	@Nullable
	protected SpriteCollector getSprites(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, EnumFacing lightFace, TextureAtlasSprite quadSprite, EnumFacing[] directions, ProcessingDataProvider dataProvider) {
		BlockPos.MutableBlockPos mutablePos = dataProvider.getData(ProcessingDataKeys.MUTABLE_POS);
		boolean[] applies = getCollector(dataProvider).applies;

		// [up] | [right] | [down] | [left]
		//     8
		// 1   *   4
		//     2
		int applications = 0;

		IBlockState appearanceState0 = side(directions[0], applies, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
		if (applies[0]) {
			applications |= 0b0001;
		}
		IBlockState appearanceState1 = side(directions[1], applies, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
		if (applies[0]) {
			applications |= 0b0010;
		}
		IBlockState appearanceState2 = side(directions[2], applies, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
		if (applies[0]) {
			applications |= 0b0100;
		}
		IBlockState appearanceState3 = side(directions[3], applies, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
		if (applies[0]) {
			applications |= 0b1000;
		}

		return switch (applications) {
			case 0b1111 -> prepareCollector(getCollector(dataProvider), 8);
			case 0b0111 -> prepareCollector(getCollector(dataProvider), 5);
			case 0b1011 -> prepareCollector(getCollector(dataProvider), 6);
			case 0b1101 -> prepareCollector(getCollector(dataProvider), 13);
			case 0b1110 -> prepareCollector(getCollector(dataProvider), 12);
			//
			case 0b0101 -> prepareCollector(getCollector(dataProvider), 9, 7);
			case 0b1010 -> prepareCollector(getCollector(dataProvider), 1, 15);
			//
			case 0b0011 -> fromTwoSidesAdj(getCollector(dataProvider), appearanceState2, appearanceState3, directions[2], directions[3], 4, 14, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
			case 0b0110 -> fromTwoSidesAdj(getCollector(dataProvider), appearanceState3, appearanceState0, directions[3], directions[0], 3, 16, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
			case 0b1100 -> fromTwoSidesAdj(getCollector(dataProvider), appearanceState0, appearanceState1, directions[0], directions[1], 10, 2, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
			case 0b1001 -> fromTwoSidesAdj(getCollector(dataProvider), appearanceState1, appearanceState2, directions[1], directions[2], 11, 0, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
			//
			case 0b0001 -> fromOneSide(getCollector(dataProvider), appearanceState1, appearanceState2, appearanceState3, directions[1], directions[2], directions[3], 9, 0, 14, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
			case 0b0010 -> fromOneSide(getCollector(dataProvider), appearanceState2, appearanceState3, appearanceState0, directions[2], directions[3], directions[0], 1, 14, 16, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
			case 0b0100 -> fromOneSide(getCollector(dataProvider), appearanceState3, appearanceState0, appearanceState1, directions[3], directions[0], directions[1], 7, 16, 2, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
			case 0b1000 -> fromOneSide(getCollector(dataProvider), appearanceState0, appearanceState1, appearanceState2, directions[0], directions[1], directions[2], 15, 2, 0, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
			//
			case 0b0000 -> {
				boolean s0 = hasSameOverlay(appearanceState0, lightFace);
				boolean s1 = hasSameOverlay(appearanceState1, lightFace);
				boolean s2 = hasSameOverlay(appearanceState2, lightFace);
				boolean s3 = hasSameOverlay(appearanceState3, lightFace);

				boolean c01 = (s0 | s1) && appliesOverlayCorner(directions[0], directions[1], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
				boolean c12 = (s1 | s2) && appliesOverlayCorner(directions[1], directions[2], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
				boolean c23 = (s2 | s3) && appliesOverlayCorner(directions[2], directions[3], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);
				boolean c30 = (s3 | s0) && appliesOverlayCorner(directions[3], directions[0], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite);

				if (c01 | c12 | c23 | c30) {
					SpriteCollector collector = getCollector(dataProvider);
					if (c01) {
						add(collector, 2);
					}
					if (c12) {
						add(collector, 0);
					}
					if (c23) {
						add(collector, 14);
					}
					if (c30) {
						add(collector, 16);
					}
					yield collector;
				}

				yield null;
			}
			//
			default -> throw new IllegalStateException("Unexpected value: " + applications);
		};
	}

	/** Pooled per thread through {@link #SPRITE_COLLECTOR}: the (at most 4) tiles to emit, plus scratch. */
	public static class SpriteCollector {
		protected final int[] tiles = new int[4];
		protected int amount;
		/** Scratch out-parameter for the side reads. */
		protected final boolean[] applies = new boolean[1];

		public void add(int tile) {
			if (amount < tiles.length) {
				tiles[amount++] = tile;
			}
		}

		public int amount() {
			return amount;
		}

		public int tile(int i) {
			return tiles[i];
		}

		public void clear() {
			amount = 0;
		}
	}

	public static class Factory extends AbstractQuadProcessorFactory<StandardOverlayCtmProperties> {
		@Override
		public QuadProcessor createProcessor(StandardOverlayCtmProperties properties, TextureAtlasSprite[] sprites) {
			OverlayPropertiesSection overlaySection = properties.getOverlayPropertiesSection();
			return new StandardOverlayQuadProcessor(sprites, OverlayProcessingPredicate.fromProperties(properties), properties.getMatchTilesSet(), properties.getBlockStateFilter(), properties.getConnectTilesSet(), properties.getConnectBlocksPredicate(), properties.getConnectionPredicate(), overlaySection.getTintIndex(), overlaySection.getTintBlock(), overlaySection.getLayer());
		}

		@Override
		public int getSpriteAmount(StandardOverlayCtmProperties properties) {
			return TILES;
		}

		@Override
		public boolean supportsNullSprites(StandardOverlayCtmProperties properties) {
			return false;
		}
	}
}
