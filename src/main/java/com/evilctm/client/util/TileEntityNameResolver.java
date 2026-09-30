/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * The custom name of a tile entity for {@code name=} rules. Vanilla 1.12 does not sync {@code CustomName} of most
 * nameable tile entities (furnace, hopper, dispenser, dropper, brewing stand, enchanting table) to the client, so,
 * like OptiFine, an unnamed client tile entity is looked up on the integrated server. The lookup never blocks and
 * never touches server state off the server thread: it is scheduled on the server thread, its answer is cached per
 * client tile entity on the client thread, and a found name re-renders the block. On a dedicated server only tile
 * entities whose update tag carries the name (beacon, banner, shulker box, ...) can match.
 */
public final class TileEntityNameResolver {
	/** Where names the client does not have come from. */
	public interface Backend {
		boolean available();

		/** Looks the name up and calls {@code onClientThread} with it (or {@code null}) on the client thread. */
		void request(TileEntity clientTileEntity, Consumer<String> onClientThread);

		/** Rebuilds the section of a tile entity whose name was just found. */
		void rerender(TileEntity clientTileEntity);
	}

	private static final String NONE = "";
	private static final Map<TileEntity, String> NAMES = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Backend INTEGRATED_SERVER = new IntegratedServerBackend();

	private static volatile Backend backend = INTEGRATED_SERVER;

	private TileEntityNameResolver() {
	}

	/** The custom name of {@code tileEntity}, or {@code null} when it has none or it is not known yet. Never throws. */
	@Nullable
	public static String customName(@Nullable TileEntity tileEntity) {
		if (!(tileEntity instanceof IWorldNameable nameable)) {
			return null;
		}
		try {
			if (nameable.hasCustomName()) {
				return nameable.getDisplayName().getUnformattedText();
			}
			String cached = NAMES.get(tileEntity);
			if (cached != null) {
				return cached == NONE ? null : cached;
			}
			Backend b = backend;
			if (!b.available()) {
				return null;
			}
			if (NAMES.putIfAbsent(tileEntity, NONE) != null) {
				return null;
			}
			b.request(tileEntity, name -> {
				if (name != null && !name.isEmpty()) {
					NAMES.put(tileEntity, name);
					b.rerender(tileEntity);
				}
			});
			return null;
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	/** Test hook: replaces the backend ({@code null} = the integrated server) and forgets every cached name. */
	public static void setBackendForTests(@Nullable Backend testBackend) {
		backend = testBackend != null ? testBackend : INTEGRATED_SERVER;
		NAMES.clear();
	}

	private static final class IntegratedServerBackend implements Backend {
		@Override
		public boolean available() {
			Minecraft mc = Minecraft.getMinecraft();
			return mc != null && mc.getIntegratedServer() != null;
		}

		@Override
		public void request(TileEntity clientTileEntity, Consumer<String> onClientThread) {
			Minecraft mc = Minecraft.getMinecraft();
			IntegratedServer server = mc.getIntegratedServer();
			World world = clientTileEntity.getWorld();
			if (server == null || world == null) {
				return;
			}
			int dimension = world.provider.getDimension();
			BlockPos pos = clientTileEntity.getPos().toImmutable();
			server.addScheduledTask(() -> {
				String name = null;
				WorldServer serverWorld = server.getWorld(dimension);
				if (serverWorld != null && serverWorld.isBlockLoaded(pos)) {
					TileEntity serverTileEntity = serverWorld.getTileEntity(pos);
					if (serverTileEntity instanceof IWorldNameable nameable && nameable.hasCustomName()) {
						name = nameable.getDisplayName().getUnformattedText();
					}
				}
				String found = name;
				mc.addScheduledTask(() -> onClientThread.accept(found));
			});
		}

		@Override
		public void rerender(TileEntity clientTileEntity) {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc.world != null && clientTileEntity.getWorld() == mc.world) {
				BlockPos pos = clientTileEntity.getPos();
				mc.world.markBlockRangeForRenderUpdate(pos, pos);
			}
		}
	}
}
