/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.config;

import java.util.List;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/** One toggle per boolean option (keys {@code options.evilctm.<key>}), then Done. */
public class EvilCtmConfigScreen extends GuiScreen {
	private static final int DONE_ID = 1000;

	private final GuiScreen parent;
	private final EvilCtmConfig config;
	private final List<Option.BooleanOption> toggles = new ObjectArrayList<>();

	public EvilCtmConfigScreen(GuiScreen parent, EvilCtmConfig config) {
		this.parent = parent;
		this.config = config;
	}

	@Override
	public void initGui() {
		buttonList.clear();
		toggles.clear();
		for (Option<?> option : config.getOptionMapView().values()) {
			if (option instanceof Option.BooleanOption booleanOption) {
				toggles.add(booleanOption);
			}
		}
		int top = Math.max(50, height / 2 - (toggles.size() + 1) * 12);
		for (int i = 0; i < toggles.size(); i++) {
			Option.BooleanOption option = toggles.get(i);
			buttonList.add(new GuiButton(i, width / 2 - 100, top + i * 24, 200, 20, optionText(option)));
		}
		buttonList.add(new GuiButton(DONE_ID, width / 2 - 100, top + toggles.size() * 24 + 8, 200, 20, I18n.format("gui.done")));
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button.id == DONE_ID) {
			config.save();
			mc.displayGuiScreen(parent);
			return;
		}
		if (button.id >= 0 && button.id < toggles.size()) {
			Option.BooleanOption option = toggles.get(button.id);
			option.set(!option.get());
			config.save();
			button.displayString = optionText(option);
			reloadRenderers();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		drawCenteredString(fontRenderer, I18n.format("options.evilctm.title"), width / 2, 30, 0xFFFFFF);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private static String optionText(Option.BooleanOption option) {
		return I18n.format("options.evilctm." + option.getKey()) + ": " + I18n.format(option.get() ? "options.on" : "options.off");
	}

	private static void reloadRenderers() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.renderGlobal != null) {
			mc.renderGlobal.loadRenderers();
		}
	}
}
