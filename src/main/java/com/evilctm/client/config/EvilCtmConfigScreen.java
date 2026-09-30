/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.config;

import java.util.List;

import com.evilctm.client.compat.demonica.RenderPathStatus;

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
	private RenderPathStatus.Problem status = RenderPathStatus.Problem.OK;

	public EvilCtmConfigScreen(GuiScreen parent, EvilCtmConfig config) {
		this.parent = parent;
		this.config = config;
	}

	@Override
	public void initGui() {
		buttonList.clear();
		toggles.clear();
		status = RenderPathStatus.evaluate();
		for (Option<?> option : config.getOptionMapView().values()) {
			if (option instanceof Option.BooleanOption booleanOption) {
				toggles.add(booleanOption);
			}
		}
		int top = Math.max(80, height / 2 - (toggles.size() + 1) * 12 + 20);
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
		int y = 42;
		if (status == RenderPathStatus.Problem.OK) {
			drawCenteredString(fontRenderer, I18n.format("evilctm.status.ok"), width / 2, y, 0x55FF55);
		} else {
			List<String> lines = new ObjectArrayList<>(fontRenderer.listFormattedStringToWidth(I18n.format(status.translationKey()), width - 40));
			lines.addAll(fontRenderer.listFormattedStringToWidth(I18n.format("evilctm.warning.hint"), width - 40));
			for (String line : lines) {
				drawCenteredString(fontRenderer, line, width / 2, y, 0xFF5555);
				y += 10;
			}
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
		for (GuiButton button : buttonList) {
			if (button.id >= 0 && button.id < toggles.size() && button.isMouseOver()) {
				String key = "options.evilctm." + toggles.get(button.id).getKey() + ".tooltip";
				if (I18n.hasKey(key)) {
					drawHoveringText(fontRenderer.listFormattedStringToWidth(I18n.format(key), 200), mouseX, mouseY);
				}
			}
		}
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
