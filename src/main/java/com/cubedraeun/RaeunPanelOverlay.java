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
	private static final Color GOOD = new Color(0x7B, 0xD3, 0x4E);
	private static final Color BAD = new Color(0xE0, 0x6A, 0x4E);
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
		panelComponent.setPreferredSize(new Dimension(168, 0));
		panelComponent.getChildren().add(TitleComponent.builder().text("Prison of Raeun").build());
		int tick = client.getTickCount();

		if (config.showLapTimes())
		{
			line("Lap", plugin.lapStartTick < 0 ? "not started" : RaeunPlugin.formatTicks(tick - plugin.lapStartTick), Color.WHITE);
			if (plugin.lastLapRoute != null)
			{
				boolean timed = plugin.lastLapRoute != RaeunPlugin.Route.PARTIAL;
				line("Last (" + plugin.lastLapRoute.label.toLowerCase() + ")",
					timed ? RaeunPlugin.formatTicks(plugin.lastLapTicks) : "not timed",
					plugin.lastLapWasBest ? GOOD : Color.WHITE);
			}
			if (plugin.timedLaps > 1)
			{
				line("Average", RaeunPlugin.formatTicks(plugin.timedLapTicks / plugin.timedLaps), Color.WHITE);
			}
			for (RaeunPlugin.Route r : RaeunPlugin.Route.values())
			{
				int best = plugin.best[r.ordinal()];
				if (best > 0 && r != RaeunPlugin.Route.PARTIAL)
				{
					line("Best " + r.label.toLowerCase(), RaeunPlugin.formatTicks(best), Color.WHITE);
				}
			}
		}

		if (config.showXp())
		{
			String lapCount = String.valueOf(plugin.laps);
			int fast = plugin.routeLaps[RaeunPlugin.Route.FAST.ordinal()];
			int slow = plugin.routeLaps[RaeunPlugin.Route.SLOW.ordinal()];
			if (fast > 0 && slow > 0)
			{
				lapCount += " (" + fast + " fast, " + slow + " slow)";
			}
			line("Laps", lapCount, Color.WHITE);
			if (plugin.laps > 0)
			{
				line("Laps/hr", String.format(Locale.US, "%.1f", plugin.perHour(plugin.laps)), Color.WHITE);
			}
			line("XP gained", NUM.format(plugin.sessionXp), Color.WHITE);
			line("XP/hr", NUM.format(plugin.xpPerHour()), Color.WHITE);
		}

		if (config.showGoal())
		{
			int left = plugin.lapsToLevel();
			if (left >= 0)
			{
				line("Laps to level", (plugin.timedLaps == 0 ? "~" : "") + NUM.format(left), Color.WHITE);
			}
		}

		if (config.showLoot() && (plugin.chestsOpened > 0 || plugin.treasure > 0))
		{
			line("Chests", String.valueOf(plugin.chestsOpened), Color.WHITE);
			line("Treasure", plugin.treasure + String.format(Locale.US, " (%.1f/hr)", plugin.perHour(plugin.treasure)), Color.WHITE);
			if (plugin.kits > 0)
			{
				line("Graceful kits", String.valueOf(plugin.kits), GOOD);
			}
			if (plugin.fabrics + plugin.ghostlyFabrics > 0)
			{
				line("Sail fabric", String.valueOf(plugin.fabrics + plugin.ghostlyFabrics), GOOD);
			}
		}

		if (config.highlightChest() && plugin.lapStartTick >= 0 && !plugin.chestLooted)
		{
			line("Chest", "not looted", config.chestColor());
		}

		RaeunPlugin.Charge charge = plugin.currentCharge();
		if (config.showClickCounter() && charge != null)
		{
			int need = plugin.clicksNeeded(charge.id, charge.key());
			line("Clicks", charge.clicks + (need > 0 ? " / " + need : ""), need > 0 && charge.clicks > need ? BAD : GOOD);
		}
		else if (config.showClickCounter() && plugin.lastJumpTick >= 0 && tick - plugin.lastJumpTick < 10 && plugin.lastJumpClicks > 1)
		{
			line("Last jump", plugin.lastJumpClicks + " clicks, " + plugin.lastJumpXp + " xp", plugin.lastJumpHalf ? BAD : Color.WHITE);
		}

		if (config.showSequence())
		{
			line("Jumps 1", config.firstSequence(), LABEL);
			line("Jumps 2", config.lastSequence(), LABEL);
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
