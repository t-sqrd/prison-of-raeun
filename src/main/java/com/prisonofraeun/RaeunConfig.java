package com.prisonofraeun;

import java.awt.Color;
import java.awt.Font;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.ui.FontManager;

@ConfigGroup(RaeunConfig.GROUP)
public interface RaeunConfig extends Config
{
	String GROUP = "prisonofraeun";

	enum LabelSize
	{
		SMALL("Small"), REGULAR("Regular"), BOLD("Bold");

		private final String name;

		LabelSize(String name)
		{
			this.name = name;
		}

		Font font()
		{
			switch (this)
			{
				case SMALL:
					return FontManager.getRunescapeSmallFont();
				case BOLD:
					return FontManager.getRunescapeBoldFont();
				default:
					return FontManager.getRunescapeFont();
			}
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigSection(name = "Info box", description = "Choose what the info box shows", position = 10)
	String infoBox = "infoBox";

	@ConfigSection(name = "Colours", description = "Outline colours", position = 20)
	String colours = "colours";

	@ConfigItem(keyName = "highlightObstacles", name = "Outline obstacles", description = "Outline every obstacle you can click, and show the clicks each charged jump needs", position = 0)
	default boolean highlightObstacles()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightChest", name = "Outline chest until looted", description = "Outline the reward chest during a lap until you have looted it (needs 65 Thieving)", position = 1)
	default boolean highlightChest()
	{
		return true;
	}

	@ConfigItem(keyName = "showSlowRoute", name = "Outline slow route", description = "Also outline the gaps that start the slow way round", position = 2)
	default boolean showSlowRoute()
	{
		return true;
	}

	@ConfigItem(keyName = "showClickCounts", name = "Show click counts", description = "Show how many clicks each charged jump needs (x2, x3, x4)", position = 3)
	default boolean showClickCounts()
	{
		return true;
	}

	@ConfigItem(keyName = "showLabels", name = "Show text labels", description = "Show the Start and Loot chest labels. Turn off for outlines only", position = 4)
	default boolean showLabels()
	{
		return true;
	}

	@Range(min = 1, max = 6)
	@ConfigItem(keyName = "outlineWidth", name = "Outline width", description = "How thick the outlines are", position = 5)
	default int outlineWidth()
	{
		return 2;
	}

	@Range(max = 255)
	@ConfigItem(keyName = "fillOpacity", name = "Fill opacity", description = "How solid the colour inside each outline is. 0 for outlines only, 255 for solid", position = 6)
	default int fillOpacity()
	{
		return 35;
	}

	@ConfigItem(keyName = "labelSize", name = "Label size", description = "Text size for the click counts and labels", position = 7)
	default LabelSize labelSize()
	{
		return LabelSize.REGULAR;
	}

	@Range(min = 5, max = 60)
	@ConfigItem(keyName = "highlightDistance", name = "Highlight distance", description = "How many tiles away obstacles are outlined", position = 8)
	default int highlightDistance()
	{
		return 30;
	}

	@ConfigItem(keyName = "showFakeStones", name = "Show fake stones faintly", description = "Give the fake stepping stones a faint grey outline instead of hiding them", position = 9)
	default boolean showFakeStones()
	{
		return false;
	}

	@ConfigItem(keyName = "hideJumpUntilLooted", name = "Hide final jump until looted", description = "Remove the Jump option on the last obstacle until you have looted the chest this lap (needs 65 Thieving)", position = 10)
	default boolean hideJumpUntilLooted()
	{
		return false;
	}

	@ConfigItem(keyName = "showPanel", name = "Show info box", description = "Turn the whole info box on or off", section = infoBox, position = 0)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(keyName = "showTitle", name = "Title", description = "The \"Prison of Raeun\" heading", section = infoBox, position = 1)
	default boolean showTitle()
	{
		return true;
	}

	@ConfigItem(keyName = "showLap", name = "Current lap", description = "Time on the lap you are running", section = infoBox, position = 2)
	default boolean showLap()
	{
		return true;
	}

	@ConfigItem(keyName = "showLastLap", name = "Last lap", description = "Time of your last finished lap", section = infoBox, position = 3)
	default boolean showLastLap()
	{
		return true;
	}

	@ConfigItem(keyName = "showBestLap", name = "Best lap", description = "Your personal best", section = infoBox, position = 4)
	default boolean showBestLap()
	{
		return true;
	}

	@ConfigItem(keyName = "showLaps", name = "Laps", description = "Laps finished this session", section = infoBox, position = 5)
	default boolean showLaps()
	{
		return true;
	}

	@ConfigItem(keyName = "showXpHr", name = "XP/hr", description = "Agility XP per hour this session", section = infoBox, position = 6)
	default boolean showXpHr()
	{
		return true;
	}

	@ConfigItem(keyName = "showTreasure", name = "Treasure", description = "Sunken treasure picked up this session, and per hour", section = infoBox, position = 7)
	default boolean showTreasure()
	{
		return true;
	}

	@ConfigItem(keyName = "showChest", name = "Chest", description = "Reminder while the chest is still unlooted this lap", section = infoBox, position = 8)
	default boolean showChest()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "obstacleColor", name = "Shared", description = "Obstacles every lap passes", section = colours, position = 0)
	default Color obstacleColor()
	{
		return new Color(0xFF, 0xC8, 0x3D);
	}

	@Alpha
	@ConfigItem(keyName = "fastColor", name = "Fast route", description = "Fast route obstacles, and the one real stone in each row of three", section = colours, position = 1)
	default Color fastColor()
	{
		return new Color(0x5E, 0xE0, 0x7A);
	}

	@Alpha
	@ConfigItem(keyName = "chargedColor", name = "Charged jump", description = "Stones that need more than one click", section = colours, position = 2)
	default Color chargedColor()
	{
		return new Color(0x5C, 0xE1, 0xFF);
	}

	@Alpha
	@ConfigItem(keyName = "slowColor", name = "Slow route", description = "Gaps that start the slow way round", section = colours, position = 3)
	default Color slowColor()
	{
		return new Color(0xFF, 0x4D, 0x4D);
	}

	@Alpha
	@ConfigItem(keyName = "chestColor", name = "Chest", description = "The reward chest", section = colours, position = 4)
	default Color chestColor()
	{
		return new Color(0xFF, 0x5F, 0xD2);
	}
}
