package com.cubedraeun;

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
	private static final int CHEST_FILL = 110;
	private static final int CHEST_REACH = 60;

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
		WorldPoint here = me.getWorldLocation();
		Point mouse = client.getMouseCanvasPosition();
		RaeunPlugin.Charge charge = plugin.currentCharge();
		boolean canThieve = client.getRealSkillLevel(Skill.THIEVING) >= 65;
		int reach = config.drawDistance();

		boolean counter = config.showClickCounter();
		boolean outline = config.highlightObstacles();
		boolean chestWanted = config.highlightChest() && canThieve && !plugin.chestLooted && plugin.lapStartTick >= 0;
		int fill = config.fillOpacity();

		for (Map.Entry<TileObject, RaeunPlugin.Tracked> en : plugin.objects.entrySet())
		{
			TileObject o = en.getKey();
			RaeunPlugin.Tracked t = en.getValue();
			boolean chest = t.kind == RaeunPlugin.Kind.CHEST;
			if (o.getWorldLocation().distanceTo2D(here) > (chest ? CHEST_REACH : reach))
			{
				continue;
			}
			Color color;
			String label = null;
			if (chest)
			{
				if (!chestWanted || !plugin.chestOpenable(o))
				{
					continue;
				}
				color = config.chestColor();
				label = "Loot chest";
			}
			else if (t.kind == RaeunPlugin.Kind.FAST)
			{
				int need = plugin.clicksNeeded(o.getId(), t.key);
				boolean charging = counter && charge != null && charge.id == o.getId() && charge.x == t.x && charge.y == t.y;
				if (charging)
				{
					color = config.chargingColor();
					label = need > 0 ? charge.clicks + " / " + need : String.valueOf(charge.clicks);
				}
				else
				{
					color = config.chargedColor();
					if (need > 0)
					{
						label = "x" + need + (plugin.clicksConfirmed(o.getId(), t.key) ? "" : "?");
					}
				}
				if (!outline && label == null)
				{
					continue;
				}
			}
			else
			{
				if (!outline || (t.stone && !t.solid))
				{
					continue;
				}
				color = config.obstacleColor();
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
			int alpha = chest ? CHEST_FILL : Math.min(255, hover ? fill * 2 : fill);
			if (alpha > 0)
			{
				g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
				g.fill(box);
			}
			g.setColor(color);
			g.setStroke(chest ? HEAVY : hover ? THICK : THIN);
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
