/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;

import com.evilctm.api.client.ProcessingDataKey;
import com.evilctm.api.client.ProcessingDataKeyRegistry;
import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.processor.simple.CtmSpriteProvider;
import com.evilctm.client.properties.CompactConnectingCtmProperties;
import com.evilctm.client.util.QuadUtil;
import com.evilctm.client.util.RenderUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * {@code method=ctm_compact}: five tiles (0 unconnected, 1 fully connected, 2 up/down, 3 left/right, 4 corners
 * missing) are chosen per quadrant of the face, and the quad is split along the UV midlines where neighbouring
 * quadrants choose different tiles.
 */
public class CompactCtmQuadProcessor extends AbstractQuadProcessor {
	protected static final int[][] QUADRANT_INDEX_MAPS = new int[8][];

	static {
		int[][] map = QUADRANT_INDEX_MAPS;

		map[0] = new int[] { 0, 1, 2, 3 };
		map[1] = map[0].clone();
		ArrayUtils.shift(map[1], 1);
		map[2] = map[1].clone();
		ArrayUtils.shift(map[2], 1);
		map[3] = map[2].clone();
		ArrayUtils.shift(map[3], 1);

		map[4] = map[0].clone();
		ArrayUtils.reverse(map[4]);
		map[5] = map[4].clone();
		ArrayUtils.shift(map[5], 1);
		map[6] = map[5].clone();
		ArrayUtils.shift(map[6], 1);
		map[7] = map[6].clone();
		ArrayUtils.shift(map[7], 1);
	}

	/** Scratch vertices; per-thread through the processing context. */
	public static final ProcessingDataKey<VertexContainer> VERTEX_CONTAINER = ProcessingDataKeyRegistry.get().registerKey(EvilCtmClient.asId("compact_vertex_container"), VertexContainer::new);

	protected ConnectionPredicate connectionPredicate;
	protected boolean innerSeams;
	protected OrientationMode orientationMode;
	@Nullable
	protected TextureAtlasSprite[] replacementSprites;

	public CompactCtmQuadProcessor(TextureAtlasSprite[] sprites, ProcessingPredicate processingPredicate, ConnectionPredicate connectionPredicate, boolean innerSeams, OrientationMode orientationMode, @Nullable TextureAtlasSprite[] replacementSprites) {
		super(sprites, processingPredicate);
		this.connectionPredicate = connectionPredicate;
		this.innerSeams = innerSeams;
		this.orientationMode = orientationMode;
		this.replacementSprites = replacementSprites;
	}

	@Override
	public ProcessingResult processQuadInner(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, int pass, ProcessingContext context) {
		EnumFacing face = quad.getFace();
		if (face == null || !quad.getFormat().hasUvOffset(0)) {
			return ProcessingResult.NEXT_PROCESSOR;
		}

		int orientation = orientationMode.getOrientation(quad, appearanceState);
		EnumFacing[] directions = DirectionMaps.getMap(face)[orientation];
		BlockPos.MutableBlockPos mutablePos = context.getData(ProcessingDataKeys.MUTABLE_POS);
		int connections = CtmSpriteProvider.getConnections(directions, connectionPredicate, innerSeams, mutablePos, level, pos, appearanceState, state, face, sprite);

		if (replacementSprites != null) {
			TextureAtlasSprite replacementSprite = replacementSprites[CtmSpriteProvider.SPRITE_INDEX_MAP[connections]];
			if (replacementSprite != null) {
				if (!RenderUtil.isMissingSprite(replacementSprite)) {
					context.replaceQuad(QuadUtil.retexture(quad, replacementSprite));
				}
				return ProcessingResult.NEXT_PASS;
			}
		}

		// UVs normalized to the sprite and centred on its middle
		float[] un = new float[4];
		float[] vn = new float[4];
		float minU = sprite.getMinU();
		float maxU = sprite.getMaxU();
		float minV = sprite.getMinV();
		float maxV = sprite.getMaxV();
		for (int i = 0; i < 4; i++) {
			un[i] = inverseLerp(QuadUtil.getU(quad, i), minU, maxU) - 0.5f;
			vn[i] = inverseLerp(QuadUtil.getV(quad, i), minV, maxV) - 0.5f;
		}
		return split(quad, sprite, context, orientation, connections, un, vn);
	}

	private ProcessingResult split(BakedQuad quad, TextureAtlasSprite sprite, ProcessingContext context, int orientation, int connections, float[] un, float[] vn) {
		float un0 = un[0];
		float un1 = un[1];
		float un2 = un[2];
		float un3 = un[3];
		float vn0 = vn[0];
		float vn1 = vn[1];
		float vn2 = vn[2];
		float vn3 = vn[3];

		// Which side of the splitting line each U or V coordinate lies on
		int uSignum0 = (int) Math.signum(un0);
		int vSignum0 = (int) Math.signum(vn0);
		int uSignum1 = (int) Math.signum(un1);
		int vSignum1 = (int) Math.signum(vn1);
		int uSignum2 = (int) Math.signum(un2);
		int vSignum2 = (int) Math.signum(vn2);
		int uSignum3 = (int) Math.signum(un3);
		int vSignum3 = (int) Math.signum(vn3);

		boolean uSplit01 = shouldSplitUV(uSignum0, uSignum1);
		boolean vSplit01 = shouldSplitUV(vSignum0, vSignum1);
		boolean uSplit12 = shouldSplitUV(uSignum1, uSignum2);
		boolean vSplit12 = shouldSplitUV(vSignum1, vSignum2);
		boolean uSplit23 = shouldSplitUV(uSignum2, uSignum3);
		boolean vSplit23 = shouldSplitUV(vSignum2, vSignum3);
		boolean uSplit30 = shouldSplitUV(uSignum3, uSignum0);
		boolean vSplit30 = shouldSplitUV(vSignum3, vSignum0);

		// Cannot split across U and V at the same time
		if (uSplit01 & vSplit01 | uSplit12 & vSplit12 | uSplit23 & vSplit23 | uSplit30 & vSplit30) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		// Cannot split across U twice in a row
		if (uSplit01 & uSplit12 | uSplit12 & uSplit23 | uSplit23 & uSplit30 | uSplit30 & uSplit01) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		// Cannot split across V twice in a row
		if (vSplit01 & vSplit12 | vSplit12 & vSplit23 | vSplit23 & vSplit30 | vSplit30 & vSplit01) {
			return ProcessingResult.NEXT_PROCESSOR;
		}

		boolean uSplit = uSplit01 & uSplit23 | uSplit12 & uSplit30;
		boolean vSplit = vSplit01 & vSplit23 | vSplit12 & vSplit30;

		if (uSplit & vSplit) {
			int[] quadrantIndexMap = QUADRANT_INDEX_MAPS[orientation];

			int spriteIndex0 = getSpriteIndex(quadrantIndexMap[0], connections);
			int spriteIndex1 = getSpriteIndex(quadrantIndexMap[1], connections);
			int spriteIndex2 = getSpriteIndex(quadrantIndexMap[2], connections);
			int spriteIndex3 = getSpriteIndex(quadrantIndexMap[3], connections);

			boolean split01 = spriteIndex0 != spriteIndex1;
			boolean split12 = spriteIndex1 != spriteIndex2;
			boolean split23 = spriteIndex2 != spriteIndex3;
			boolean split30 = spriteIndex3 != spriteIndex0;

			if (!(split01 | split12 | split23 | split30)) {
				return retextureWhole(quad, context, spriteIndex0);
			}

			VertexContainer vc = context.getData(VERTEX_CONTAINER);
			vc.fill(quad);
			List<BakedQuad> out = context.getExtraQuads();

			if (split01 & split12 & split23 & split30) {
				float delta01;
				float delta23;
				float delta12;
				float delta30;
				float delta4;
				if (uSplit01) {
					delta01 = inverseLerp(0, un0, un1);
					delta23 = inverseLerp(0, un2, un3);
					delta12 = inverseLerp(0, vn1, vn2);
					delta30 = inverseLerp(0, vn3, vn0);
					delta4 = inverseLerp(0, lerp(delta01, vn0, vn1), lerp(delta23, vn2, vn3));
				} else {
					delta01 = inverseLerp(0, vn0, vn1);
					delta23 = inverseLerp(0, vn2, vn3);
					delta12 = inverseLerp(0, un1, un2);
					delta30 = inverseLerp(0, un3, un0);
					delta4 = inverseLerp(0, lerp(delta01, un0, un1), lerp(delta23, un2, un3));
				}

				vc.lerp(VertexContainer.V01, delta01, 0, 1);
				vc.lerp(VertexContainer.V12, delta12, 1, 2);
				vc.lerp(VertexContainer.V23, delta23, 2, 3);
				vc.lerp(VertexContainer.V30, delta30, 3, 0);
				vc.lerp(VertexContainer.V4, delta4, VertexContainer.V01, VertexContainer.V23);

				splitQuadrant(quad, sprite, vc, 0, out, spriteIndex0);
				splitQuadrant(quad, sprite, vc, 1, out, spriteIndex1);
				splitQuadrant(quad, sprite, vc, 2, out, spriteIndex2);
				splitQuadrant(quad, sprite, vc, 3, out, spriteIndex3);
			} else {
				if (!(split01 | split12)) {
					split12 = true;
				} else if (!(split12 | split23)) {
					split23 = true;
				} else if (!(split23 | split30)) {
					split30 = true;
				} else if (!(split30 | split01)) {
					split01 = true;
				}

				int splits = (split01 ? 1 : 0) + (split12 ? 1 : 0) + (split23 ? 1 : 0) + (split30 ? 1 : 0);
				if (splits == 2) {
					if (split01) {
						float delta01;
						float delta23;
						if (uSplit01) {
							delta01 = inverseLerp(0, un0, un1);
							delta23 = inverseLerp(0, un2, un3);
						} else {
							delta01 = inverseLerp(0, vn0, vn1);
							delta23 = inverseLerp(0, vn2, vn3);
						}

						vc.lerp(VertexContainer.V01, delta01, 0, 1);
						vc.lerp(VertexContainer.V23, delta23, 2, 3);

						splitHalf(quad, sprite, vc, 1, out, spriteIndex1);
						splitHalf(quad, sprite, vc, 3, out, spriteIndex3);
					} else {
						float delta12;
						float delta30;
						if (uSplit01) {
							delta12 = inverseLerp(0, vn1, vn2);
							delta30 = inverseLerp(0, vn3, vn0);
						} else {
							delta12 = inverseLerp(0, un1, un2);
							delta30 = inverseLerp(0, un3, un0);
						}

						vc.lerp(VertexContainer.V12, delta12, 1, 2);
						vc.lerp(VertexContainer.V30, delta30, 3, 0);

						splitHalf(quad, sprite, vc, 0, out, spriteIndex0);
						splitHalf(quad, sprite, vc, 2, out, spriteIndex2);
					}
				} else if (!split01) {
					float delta23;
					float delta12;
					float delta30;
					float delta4;
					if (uSplit01) {
						delta23 = inverseLerp(0, un2, un3);
						delta12 = inverseLerp(0, vn1, vn2);
						delta30 = inverseLerp(0, vn3, vn0);
						delta4 = inverseLerp(0, lerp(delta12, un1, un2), lerp(delta30, un3, un0));
					} else {
						delta23 = inverseLerp(0, vn2, vn3);
						delta12 = inverseLerp(0, un1, un2);
						delta30 = inverseLerp(0, un3, un0);
						delta4 = inverseLerp(0, lerp(delta12, vn1, vn2), lerp(delta30, vn3, vn0));
					}

					vc.lerp(VertexContainer.V23, delta23, 2, 3);
					vc.lerp(VertexContainer.V12, delta12, 1, 2);
					vc.lerp(VertexContainer.V30, delta30, 3, 0);
					vc.lerp(VertexContainer.V4, delta4, VertexContainer.V12, VertexContainer.V30);

					splitHalf(quad, sprite, vc, 0, out, spriteIndex0);
					splitQuadrant(quad, sprite, vc, 2, out, spriteIndex2);
					splitQuadrant(quad, sprite, vc, 3, out, spriteIndex3);
				} else if (!split12) {
					float delta01;
					float delta23;
					float delta30;
					float delta4;
					if (uSplit01) {
						delta01 = inverseLerp(0, un0, un1);
						delta23 = inverseLerp(0, un2, un3);
						delta30 = inverseLerp(0, vn3, vn0);
						delta4 = inverseLerp(0, lerp(delta01, vn0, vn1), lerp(delta23, vn2, vn3));
					} else {
						delta01 = inverseLerp(0, vn0, vn1);
						delta23 = inverseLerp(0, vn2, vn3);
						delta30 = inverseLerp(0, un3, un0);
						delta4 = inverseLerp(0, lerp(delta01, un0, un1), lerp(delta23, un2, un3));
					}

					vc.lerp(VertexContainer.V01, delta01, 0, 1);
					vc.lerp(VertexContainer.V23, delta23, 2, 3);
					vc.lerp(VertexContainer.V30, delta30, 3, 0);
					vc.lerp(VertexContainer.V4, delta4, VertexContainer.V01, VertexContainer.V23);

					splitQuadrant(quad, sprite, vc, 0, out, spriteIndex0);
					splitHalf(quad, sprite, vc, 1, out, spriteIndex1);
					splitQuadrant(quad, sprite, vc, 3, out, spriteIndex3);
				} else if (!split23) {
					float delta01;
					float delta12;
					float delta30;
					float delta4;
					if (uSplit01) {
						delta01 = inverseLerp(0, un0, un1);
						delta12 = inverseLerp(0, vn1, vn2);
						delta30 = inverseLerp(0, vn3, vn0);
						delta4 = inverseLerp(0, lerp(delta12, un1, un2), lerp(delta30, un3, un0));
					} else {
						delta01 = inverseLerp(0, vn0, vn1);
						delta12 = inverseLerp(0, un1, un2);
						delta30 = inverseLerp(0, un3, un0);
						delta4 = inverseLerp(0, lerp(delta12, vn1, vn2), lerp(delta30, vn3, vn0));
					}

					vc.lerp(VertexContainer.V01, delta01, 0, 1);
					vc.lerp(VertexContainer.V12, delta12, 1, 2);
					vc.lerp(VertexContainer.V30, delta30, 3, 0);
					vc.lerp(VertexContainer.V4, delta4, VertexContainer.V12, VertexContainer.V30);

					splitQuadrant(quad, sprite, vc, 0, out, spriteIndex0);
					splitQuadrant(quad, sprite, vc, 1, out, spriteIndex1);
					splitHalf(quad, sprite, vc, 2, out, spriteIndex2);
				} else {
					float delta01;
					float delta23;
					float delta12;
					float delta4;
					if (uSplit01) {
						delta01 = inverseLerp(0, un0, un1);
						delta23 = inverseLerp(0, un2, un3);
						delta12 = inverseLerp(0, vn1, vn2);
						delta4 = inverseLerp(0, lerp(delta01, vn0, vn1), lerp(delta23, vn2, vn3));
					} else {
						delta01 = inverseLerp(0, vn0, vn1);
						delta23 = inverseLerp(0, vn2, vn3);
						delta12 = inverseLerp(0, un1, un2);
						delta4 = inverseLerp(0, lerp(delta01, un0, un1), lerp(delta23, un2, un3));
					}

					vc.lerp(VertexContainer.V01, delta01, 0, 1);
					vc.lerp(VertexContainer.V12, delta12, 1, 2);
					vc.lerp(VertexContainer.V23, delta23, 2, 3);
					vc.lerp(VertexContainer.V4, delta4, VertexContainer.V01, VertexContainer.V23);

					splitHalf(quad, sprite, vc, 3, out, spriteIndex3);
					splitQuadrant(quad, sprite, vc, 1, out, spriteIndex1);
					splitQuadrant(quad, sprite, vc, 2, out, spriteIndex2);
				}
			}

			return ProcessingResult.DISCARD;
		} else if (uSplit | vSplit) {
			boolean firstSplit;
			boolean swapAB;
			int spriteIndexA;
			int spriteIndexB;
			if (uSplit) {
				firstSplit = uSplit01;
				swapAB = orientation == 2 || orientation == 3 || orientation == 4 || orientation == 5;
				if ((vSignum0 + vSignum1 + vSignum2 + vSignum3) <= 0) {
					spriteIndexA = getSpriteIndex(0, connections);
					spriteIndexB = getSpriteIndex(3, connections);
				} else {
					spriteIndexA = getSpriteIndex(1, connections);
					spriteIndexB = getSpriteIndex(2, connections);
				}
			} else {
				firstSplit = vSplit01;
				swapAB = orientation == 1 || orientation == 2 || orientation == 5 || orientation == 6;
				if ((uSignum0 + uSignum1 + uSignum2 + uSignum3) <= 0) {
					spriteIndexA = getSpriteIndex(1, connections);
					spriteIndexB = getSpriteIndex(0, connections);
				} else {
					spriteIndexA = getSpriteIndex(2, connections);
					spriteIndexB = getSpriteIndex(3, connections);
				}
			}

			if (spriteIndexA == spriteIndexB) {
				return retextureWhole(quad, context, spriteIndexA);
			}

			if (swapAB) {
				int temp = spriteIndexA;
				spriteIndexA = spriteIndexB;
				spriteIndexB = temp;
			}

			VertexContainer vc = context.getData(VERTEX_CONTAINER);
			vc.fill(quad);
			List<BakedQuad> out = context.getExtraQuads();

			if (firstSplit) {
				float delta01;
				float delta23;
				if (uSplit) {
					delta01 = inverseLerp(0, un0, un1);
					delta23 = inverseLerp(0, un2, un3);
				} else {
					delta01 = inverseLerp(0, vn0, vn1);
					delta23 = inverseLerp(0, vn2, vn3);
				}

				vc.lerp(VertexContainer.V01, delta01, 0, 1);
				vc.lerp(VertexContainer.V23, delta23, 2, 3);

				splitHalf(quad, sprite, vc, 1, out, spriteIndexA);
				splitHalf(quad, sprite, vc, 3, out, spriteIndexB);
			} else {
				float delta12;
				float delta30;
				if (uSplit) {
					delta12 = inverseLerp(0, un1, un2);
					delta30 = inverseLerp(0, un3, un0);
				} else {
					delta12 = inverseLerp(0, vn1, vn2);
					delta30 = inverseLerp(0, vn3, vn0);
				}

				vc.lerp(VertexContainer.V12, delta12, 1, 2);
				vc.lerp(VertexContainer.V30, delta30, 3, 0);

				splitHalf(quad, sprite, vc, 0, out, spriteIndexA);
				splitHalf(quad, sprite, vc, 2, out, spriteIndexB);
			}

			return ProcessingResult.DISCARD;
		} else {
			int quadrant;
			if ((uSignum0 + uSignum1 + uSignum2 + uSignum3) <= 0) {
				quadrant = (vSignum0 + vSignum1 + vSignum2 + vSignum3) <= 0 ? 0 : 1;
			} else {
				quadrant = (vSignum0 + vSignum1 + vSignum2 + vSignum3) <= 0 ? 3 : 2;
			}
			return retextureWhole(quad, context, getSpriteIndex(quadrant, connections));
		}
	}

	/** True if and only if one argument is 1 and the other is -1. */
	protected static boolean shouldSplitUV(int signumA, int signumB) {
		return (signumA ^ signumB) == -2;
	}

	/*
	0 - Unconnected
	1 - Fully connected
	2 - Up and down / vertical
	3 - Left and right / horizontal
	4 - Unconnected corners
	 */
	protected int getSpriteIndex(int quadrantIndex, int connections) {
		int index1 = quadrantIndex;
		int index2 = (quadrantIndex + 3) % 4;
		boolean connected1 = ((connections >>> index1 * 2) & 1) == 1;
		boolean connected2 = ((connections >>> index2 * 2) & 1) == 1;
		if (connected1 & connected2) {
			if (((connections >>> (index2 * 2 + 1)) & 1) == 1) {
				return 1;
			}
			return 4;
		}
		if (connected1) {
			return 3 - quadrantIndex % 2;
		}
		if (connected2) {
			return 2 + quadrantIndex % 2;
		}
		return 0;
	}

	protected ProcessingResult retextureWhole(BakedQuad quad, ProcessingContext context, int spriteIndex) {
		TextureAtlasSprite newSprite = sprites[spriteIndex];
		if (newSprite != null && !RenderUtil.isMissingSprite(newSprite)) {
			context.replaceQuad(QuadUtil.retexture(quad, newSprite));
		}
		return ProcessingResult.STOP;
	}

	protected void emit(BakedQuad quad, TextureAtlasSprite oldSprite, int[] data, int spriteIndex, List<BakedQuad> out) {
		BakedQuad piece = new BakedQuad(data, quad.getTintIndex(), quad.getFace(), oldSprite, quad.shouldApplyDiffuseLighting(), quad.getFormat());
		TextureAtlasSprite newSprite = sprites[spriteIndex];
		if (newSprite != null && !RenderUtil.isMissingSprite(newSprite)) {
			piece = QuadUtil.retexture(piece, newSprite);
		}
		out.add(piece);
	}

	protected void splitHalf(BakedQuad quad, TextureAtlasSprite sprite, VertexContainer vc, int id, List<BakedQuad> out, int spriteIndex) {
		int[] data = quad.getVertexData().clone();
		vc.write(VertexContainer.V01 + (id + 1) % 4, data, (id + 2) % 4);
		int id3 = (id + 3) % 4;
		vc.write(VertexContainer.V01 + id3, data, id3);
		emit(quad, sprite, data, spriteIndex, out);
	}

	protected void splitQuadrant(BakedQuad quad, TextureAtlasSprite sprite, VertexContainer vc, int id, List<BakedQuad> out, int spriteIndex) {
		int[] data = quad.getVertexData().clone();
		vc.write(VertexContainer.V01 + id, data, (id + 1) % 4);
		vc.write(VertexContainer.V4, data, (id + 2) % 4);
		int id3 = (id + 3) % 4;
		vc.write(VertexContainer.V01 + id3, data, id3);
		emit(quad, sprite, data, spriteIndex, out);
	}

	private static float inverseLerp(float value, float start, float end) {
		return (value - start) / (end - start);
	}

	private static float lerp(float delta, float start, float end) {
		return start + delta * (end - start);
	}

	/**
	 * Nine vertices as raw integer vertex data: the four corners, the four edge midpoints ({@link #V01} to
	 * {@link #V30}) and the centre ({@link #V4}). Lerping is done per {@link VertexFormat} element.
	 */
	public static final class VertexContainer {
		public static final int V01 = 4;
		public static final int V12 = 5;
		public static final int V23 = 6;
		public static final int V30 = 7;
		public static final int V4 = 8;

		private int[][] vertices = new int[9][8];
		private VertexFormat format;
		private int stride;

		public void fill(BakedQuad quad) {
			format = quad.getFormat();
			stride = format.getIntegerSize();
			int[] data = quad.getVertexData();
			for (int i = 0; i < 9; i++) {
				if (vertices[i].length < stride) {
					vertices[i] = new int[stride];
				}
			}
			for (int i = 0; i < 4; i++) {
				System.arraycopy(data, i * stride, vertices[i], 0, stride);
			}
		}

		public void write(int vertex, int[] data, int slot) {
			System.arraycopy(vertices[vertex], 0, data, slot * stride, stride);
		}

		public void lerp(int target, float delta, int a, int b) {
			int[] va = vertices[a];
			int[] vb = vertices[b];
			int[] out = vertices[target];
			System.arraycopy(va, 0, out, 0, stride);
			for (int e = 0, n = format.getElementCount(); e < n; e++) {
				VertexFormatElement element = format.getElement(e);
				int offset = format.getOffset(e);
				int count = element.getElementCount();
				VertexFormatElement.EnumType type = element.getType();
				switch (element.getUsage()) {
					case POSITION -> lerpFloats(out, va, vb, offset, count, delta, type);
					case UV -> {
						if (type == VertexFormatElement.EnumType.FLOAT) {
							lerpFloats(out, va, vb, offset, count, delta, type);
						} else {
							lerpIntegers(out, va, vb, offset, count, delta, type);
						}
					}
					case COLOR -> lerpIntegers(out, va, vb, offset, count, delta, type);
					case NORMAL -> lerpNormal(out, va, vb, offset, count, delta, type);
					default -> {
					}
				}
			}
		}

		private static void lerpFloats(int[] out, int[] a, int[] b, int offset, int count, float delta, VertexFormatElement.EnumType type) {
			if (type != VertexFormatElement.EnumType.FLOAT || (offset & 3) != 0) {
				return;
			}
			for (int c = 0; c < count; c++) {
				int i = (offset >> 2) + c;
				float fa = Float.intBitsToFloat(a[i]);
				float fb = Float.intBitsToFloat(b[i]);
				out[i] = Float.floatToRawIntBits(fa + delta * (fb - fa));
			}
		}

		private static void lerpIntegers(int[] out, int[] a, int[] b, int offset, int count, float delta, VertexFormatElement.EnumType type) {
			int size = type.getSize();
			if (size > 2 || type == VertexFormatElement.EnumType.FLOAT) {
				return;
			}
			boolean signed = type == VertexFormatElement.EnumType.BYTE || type == VertexFormatElement.EnumType.SHORT;
			for (int c = 0; c < count; c++) {
				int byteOffset = offset + c * size;
				int va = read(a, byteOffset, size, signed);
				int vb = read(b, byteOffset, size, signed);
				write(out, byteOffset, size, Math.round(va + delta * (vb - va)));
			}
		}

		private static void lerpNormal(int[] out, int[] a, int[] b, int offset, int count, float delta, VertexFormatElement.EnumType type) {
			if (type != VertexFormatElement.EnumType.BYTE || count < 3) {
				return;
			}
			float[] n = new float[3];
			float sq = 0;
			for (int c = 0; c < 3; c++) {
				float va = read(a, offset + c, 1, true);
				float vb = read(b, offset + c, 1, true);
				n[c] = va + delta * (vb - va);
				sq += n[c] * n[c];
			}
			float scale = sq == 0 ? 1 : 127f / (float) Math.sqrt(sq);
			for (int c = 0; c < 3; c++) {
				write(out, offset + c, 1, Math.round(Math.max(-127, Math.min(127, n[c] * scale))));
			}
		}

		private static int read(int[] data, int byteOffset, int size, boolean signed) {
			int shift = (byteOffset & 3) * 8;
			int value = data[byteOffset >> 2] >>> shift;
			if (size == 1) {
				value &= 0xFF;
				return signed ? (byte) value : value;
			}
			value &= 0xFFFF;
			return signed ? (short) value : value;
		}

		private static void write(int[] data, int byteOffset, int size, int value) {
			int shift = (byteOffset & 3) * 8;
			int mask = (size == 1 ? 0xFF : 0xFFFF) << shift;
			data[byteOffset >> 2] = (data[byteOffset >> 2] & ~mask) | ((value << shift) & mask);
		}
	}

	public static class Factory implements QuadProcessor.Factory<CompactConnectingCtmProperties> {
		@Override
		public QuadProcessor createProcessor(CompactConnectingCtmProperties properties, Function<ResourceLocation, TextureAtlasSprite> spriteGetter) {
			int spriteAmount = getSpriteAmount(properties);
			List<ResourceLocation> spriteIds = properties.getSpriteIds();
			int provided = spriteIds.size();
			int max = provided;

			TextureAtlasSprite[] replacementSprites = null;
			Int2IntMap replacementMap = properties.getTileReplacementMap();
			if (replacementMap != null) {
				int replacementSpriteAmount = getReplacementSpriteAmount(properties);
				replacementSprites = new TextureAtlasSprite[replacementSpriteAmount];
				ObjectIterator<Int2IntMap.Entry> entryIterator = Int2IntMaps.fastIterator(replacementMap);
				while (entryIterator.hasNext()) {
					Int2IntMap.Entry entry = entryIterator.next();
					int key = entry.getIntKey();
					if (key < replacementSpriteAmount) {
						int value = entry.getIntValue();
						if (value < provided) {
							replacementSprites[key] = spriteGetter.apply(spriteIds.get(value));
						} else {
							EvilCtmClient.LOGGER.warn("Cannot replace tile " + key + " with tile " + value + " as only " + provided + " tiles were provided in file '" + properties.getResourceId() + "' in pack '" + properties.getPackId() + "'");
						}
					} else {
						EvilCtmClient.LOGGER.warn("Cannot replace tile " + key + " as method '" + properties.getMethod() + "' only supports " + replacementSpriteAmount + " replacement tiles in file '" + properties.getResourceId() + "' in pack '" + properties.getPackId() + "'");
					}
				}
			}

			if (provided > spriteAmount) {
				if (replacementSprites == null) {
					EvilCtmClient.LOGGER.warn("Method '" + properties.getMethod() + "' requires " + spriteAmount + " tiles but " + provided + " were provided in file '" + properties.getResourceId() + "' in pack '" + properties.getPackId() + "'");
				}
				max = spriteAmount;
			}

			TextureAtlasSprite[] sprites = new TextureAtlasSprite[spriteAmount];
			TextureAtlasSprite missingSprite = spriteGetter.apply(TextureMap.LOCATION_MISSING_TEXTURE);
			for (int i = 0; i < max; i++) {
				ResourceLocation spriteId = spriteIds.get(i);
				sprites[i] = spriteId.equals(com.evilctm.client.properties.BaseCtmProperties.SPECIAL_SKIP_ID) || spriteId.equals(com.evilctm.client.properties.BaseCtmProperties.SPECIAL_DEFAULT_ID)
						? missingSprite : spriteGetter.apply(spriteId);
			}

			if (provided < spriteAmount) {
				EvilCtmClient.LOGGER.error("Method '" + properties.getMethod() + "' requires at least " + spriteAmount + " tiles but only " + provided + " were provided in file '" + properties.getResourceId() + "' in pack '" + properties.getPackId() + "'");
				for (int i = provided; i < spriteAmount; i++) {
					sprites[i] = missingSprite;
				}
			}

			return new CompactCtmQuadProcessor(sprites, BaseProcessingPredicate.fromProperties(properties), properties.getConnectionPredicate(), properties.getInnerSeams(), properties.getOrientationMode(), replacementSprites);
		}

		public int getSpriteAmount(CompactConnectingCtmProperties properties) {
			return 5;
		}

		public int getReplacementSpriteAmount(CompactConnectingCtmProperties properties) {
			return 47;
		}
	}
}
