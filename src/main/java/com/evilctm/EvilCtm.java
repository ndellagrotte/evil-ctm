/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm;

import com.evilctm.proxy.IProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Mod entry. Server safe: touches only the proxy and {@link Reference}; all client work is behind ClientProxy. */
@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION,
		dependencies = "required-after:demonica", clientSideOnly = true, acceptableRemoteVersions = "*",
		guiFactory = "com.evilctm.client.config.EvilCtmGuiFactory")
public class EvilCtm {
	@SidedProxy(modId = Reference.MOD_ID, clientSide = "com.evilctm.proxy.ClientProxy", serverSide = "com.evilctm.proxy.CommonProxy")
	public static IProxy proxy;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		proxy.preInit(event);
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		proxy.init(event);
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		proxy.postInit(event);
	}
}
