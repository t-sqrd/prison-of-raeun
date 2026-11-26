package com.cubedraeun;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class RaeunPanelOverlay extends OverlayPanel
{
	private static final Color LABEL = new Color(0xC9, 0xC4, 0xB4);
	private static final Color GOOD = new Color(0x5E, 0xE0, 0x7A);
	private static final Color BAD = new Color(0xFF, 0x6B, 0x5C);
	private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);

	private final Client client;
	private final RaeunPlugin plugin;
	private final RaeunConfig config;

	@Inject
	RaeunPanelOverlay(Client client, RaeunPlugin plugin, RaeunConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "Raeun session", e -> plugin.resetSession());
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showPanel() || !plugin.onCourse())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Prison of Raeun").build());

		int tick = client.getTickCount();
		line("Lap", plugin.lapStartTick < 0 ? "-" : RaeunPlugin.formatTicks(tick - plugin.lapStartTick), Color.WHITE);
		if (plugin.lastLapTicks > 0)
		{
			line("Last lap", RaeunPlugin.formatTicks(plugin.lastLapTicks), plugin.lastLapWasBest ? GOOD : Color.WHITE);
		}
		if (plugin.bestLapTicks > 0)
		{
			line("Best lap", RaeunPlugin.formatTicks(plugin.bestLapTicks), Color.WHITE);
		}
		line("Laps", String.valueOf(plugin.laps), Color.WHITE);
		line("XP/hr", NUM.format(plugin.xpPerHour()), Color.WHITE);
		if (plugin.treasure > 0)
		{
			line("Treasure", plugin.treasure + String.format(Locale.US, " (%.0f/hr)", plugin.treasurePerHour()), Color.WHITE);
		}
		if (config.highlightChest() && plugin.lapStartTick >= 0 && !plugin.chestLooted)
		{
			line("Chest", "not looted", config.chestColor());
		}

		RaeunPlugin.Charge charge = plugin.currentCharge();
		if (charge != null)
		{
			int need = RaeunPlugin.clicksNeeded(charge.id);
			line("Clicks", charge.clicks + (need > 0 ? " / " + need : ""), need > 0 && charge.clicks > need ? BAD : GOOD);
		}
		return super.render(g);
	}

	private void line(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left).leftColor(LABEL)
			.right(right).rightColor(rightColor)
			.build());
	}
}
