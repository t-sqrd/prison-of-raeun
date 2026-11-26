package com.prisonofraeun;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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
	private static final Color FAKE_STONE = new Color(170, 170, 170, 110);
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
		boolean obstacles = config.highlightObstacles();
		boolean chestWanted = config.highlightChest() && !plugin.chestLooted && plugin.lapStartTick >= 0
			&& client.getRealSkillLevel(Skill.THIEVING) >= RaeunPlugin.CHEST_THIEVING;
		if (!obstacles && !chestWanted)
		{
			return null;
		}
		WorldPoint here = me.getWorldLocation();
		Point mouse = client.getMouseCanvasPosition();
		// Hovered outlines get a little thicker, and the chest is always the boldest thing on screen.
		int width = Math.max(1, config.outlineWidth());
		final BasicStroke THIN = new BasicStroke(width);
		final BasicStroke THICK = new BasicStroke(width + 1f);
		final BasicStroke HEAVY = new BasicStroke(width + 2f);
		final int FILL = Math.max(0, Math.min(255, config.fillOpacity()));
		final int CHEST_FILL = Math.min(255, FILL * 2);
		int reach = Math.max(1, config.highlightDistance());
		g.setFont(config.labelSize().font());

		for (Map.Entry<TileObject, RaeunPlugin.Tracked> en : plugin.objects.entrySet())
		{
			TileObject o = en.getKey();
			RaeunPlugin.Tracked t = en.getValue();
			boolean chest = t.kind == RaeunPlugin.Kind.CHEST;
			if (chest ? !chestWanted : !obstacles)
			{
				continue;
			}
			if (o.getWorldLocation().distanceTo2D(here) > (chest ? CHEST_REACH : reach))
			{
				continue;
			}

			Color color;
			String label = null;
			boolean faint = false;
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
					label = need > 0 ? String.valueOf(need) : null;
					break;
				case STONE:
					if (!t.solid)
					{
						if (!config.showFakeStones())
						{
							continue;
						}
						// Fake stones: a faint grey outline, so the real one still stands out.
						faint = true;
						color = FAKE_STONE;
						break;
					}
					color = config.fastColor();
					break;
				case FAST:
					color = config.fastColor();
					break;
				case SLOW:
					if (!config.showSlowRoute())
					{
						continue;
					}
					color = config.slowColor();
					break;
				default:
					color = config.obstacleColor();
					if (o.getId() == RaeunPlugin.START && plugin.lapStartTick < 0)
					{
						label = "Start";
					}
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
			boolean hover = !faint && mouse != null && box.contains(mouse.getX(), mouse.getY());
			if (!faint)
			{
				int alpha = chest || hover ? CHEST_FILL : FILL;
				g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
				g.fill(box);
			}
			g.setColor(color);
			g.setStroke(hover && stroke != HEAVY ? THICK : stroke);
			g.draw(box);

			boolean labelWanted = t.kind == RaeunPlugin.Kind.CHARGED ? config.showClickCounts() : config.showLabels();
			if (label != null && labelWanted)
			{
				if (t.kind == RaeunPlugin.Kind.CHARGED)
				{
					Rectangle b = box.getBounds();
					FontMetrics fm = g.getFontMetrics();
					int x = b.x + (b.width - fm.stringWidth(label)) / 2;
					int y = b.y + (b.height + fm.getAscent() - fm.getDescent()) / 2;
					OverlayUtil.renderTextLocation(g, new Point(x, y), label, Color.WHITE);
					continue;
				}
				Point p = o.getCanvasTextLocation(g, label, 40);
				if (p != null)
				{
					// Chest text sits on its own filled box, so white reads better than pink on pink.
					OverlayUtil.renderTextLocation(g, p, label, chest ? Color.WHITE : color);
				}
			}
		}
		return null;
	}
}
