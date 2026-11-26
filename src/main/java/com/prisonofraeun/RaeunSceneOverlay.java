package com.prisonofraeun;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class RaeunSceneOverlay extends Overlay
{
	private static final BasicStroke THIN = new BasicStroke(1.5f);
	private static final BasicStroke THICK = new BasicStroke(2.5f);
	private static final BasicStroke HEAVY = new BasicStroke(4f);
	private static final int FILL = 35;
	private static final int CHEST_FILL = 110;
	private static final int REACH = 30;
	private static final int CHEST_REACH = 60;
	private static final int CHEST_THIEVING = 65;

	private final Client client;
	private final RaeunPlugin plugin;
	private final RaeunConfig config;

	@Inject
	RaeunSceneOverlay(Client client, RaeunPlugin plugin, RaeunConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Player me = client.getLocalPlayer();
		if (me == null || plugin.objects.isEmpty())
		{
			return null;
		}
		boolean obstacles = config.highlightObstacles();
		boolean chestWanted = config.highlightChest() && !plugin.chestLooted && plugin.lapStartTick >= 0
			&& client.getRealSkillLevel(Skill.THIEVING) >= CHEST_THIEVING;
		if (!obstacles && !chestWanted)
		{
			return null;
		}
		WorldPoint here = me.getWorldLocation();
		Point mouse = client.getMouseCanvasPosition();

		for (Map.Entry<TileObject, RaeunPlugin.Tracked> en : plugin.objects.entrySet())
		{
			TileObject o = en.getKey();
			RaeunPlugin.Tracked t = en.getValue();
			boolean chest = t.kind == RaeunPlugin.Kind.CHEST;
			if (chest ? !chestWanted : !obstacles)
			{
				continue;
			}
			if (o.getWorldLocation().distanceTo2D(here) > (chest ? CHEST_REACH : REACH))
			{
				continue;
			}

			Color color;
			String label = null;
			BasicStroke stroke = THIN;
			switch (t.kind)
			{
				case CHEST:
					if (!plugin.chestOpenable(o))
					{
						continue;
					}
					color = config.chestColor();
					label = "Loot chest";
					stroke = HEAVY;
					break;
				case CHARGED:
					int need = RaeunPlugin.clicksNeeded(o.getId());
					color = config.chargedColor();
					label = need > 0 ? "x" + need : null;
					break;
				case STONE:
					if (!t.solid)
					{
						continue;
					}
					color = config.fastColor();
					break;
				case SLOW:
					color = config.slowColor();
					break;
				default:
					color = config.obstacleColor();
					break;
			}

			Shape box = o.getClickbox();
			if (box == null)
			{
				box = o.getCanvasTilePoly();
			}
			if (box == null)
			{
				continue;
			}
			boolean hover = mouse != null && box.contains(mouse.getX(), mouse.getY());
			int alpha = chest ? CHEST_FILL : hover ? FILL * 2 : FILL;
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
			g.fill(box);
			g.setColor(color);
			g.setStroke(hover && stroke == THIN ? THICK : stroke);
			g.draw(box);

			if (label != null)
			{
				Point p = o.getCanvasTextLocation(g, label, 40);
				if (p != null)
				{
					OverlayUtil.renderTextLocation(g, p, label, color);
				}
			}
		}
		return null;
	}
}
