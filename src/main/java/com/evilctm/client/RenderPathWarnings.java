/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client;

import com.evilctm.client.compat.demonica.RenderPathStatus;
import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.layer.LayerRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Tells the player, once per world, when Evil CTM cannot render (chat and log). */
public class RenderPathWarnings {
	private static final int NOT_INVOKED_TICKS = 200;

	private boolean warnedThisWorld;
	private int ticksInWorld;

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc.world == null) {
				warnedThisWorld = false;
				ticksInWorld = 0;
				return;
			}
			if (mc.player == null || warnedThisWorld) {
				return;
			}
			ticksInWorld++;
			RenderPathStatus.Problem problem = RenderPathStatus.evaluate();
			if (problem == RenderPathStatus.Problem.OK) {
				if (ticksInWorld < NOT_INVOKED_TICKS || RenderPathStatus.invoked() || !LayerRouter.gate().fastPathActive()) {
					return;
				}
				problem = RenderPathStatus.Problem.NOT_INVOKED;
			}
			warnedThisWorld = true;
			EvilCtmClient.LOGGER.warn("Evil CTM render path problem: {}", problem);
			if (EvilCtmConfig.INSTANCE.renderPathWarnings.get()) {
				TextComponentTranslation message = new TextComponentTranslation(problem.translationKey());
				message.getStyle().setColor(TextFormatting.RED);
				mc.ingameGUI.getChatGUI().printChatMessage(message);
				TextComponentTranslation hint = new TextComponentTranslation("evilctm.warning.hint");
				hint.getStyle().setColor(TextFormatting.RED);
				mc.ingameGUI.getChatGUI().printChatMessage(hint);
			}
		} catch (RuntimeException | LinkageError e) {
			warnedThisWorld = true;
			EvilCtmClient.LOGGER.error("Could not evaluate the Evil CTM render path", e);
		}
	}
}
