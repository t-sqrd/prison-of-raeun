package com.prisonofraeun;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(RaeunConfig.GROUP)
public interface RaeunConfig extends Config
{
	String GROUP = "prisonofraeun";

	@ConfigSection(name = "Colours", description = "Outline colours", position = 10)
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

	@ConfigItem(keyName = "showPanel", name = "Show info box", description = "Lap timer, best lap, XP per hour and sunken treasure", position = 2)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(keyName = "lapChatMessage", name = "Lap time in chat", description = "Post each lap time to the chat box", position = 3)
	default boolean lapChatMessage()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "obstacleColor", name = "Obstacle", description = "Gaps every lap passes", section = colours, position = 0)
	default Color obstacleColor()
	{
		return new Color(0xFF, 0xC8, 0x3D);
	}

	@Alpha
	@ConfigItem(keyName = "chargedColor", name = "Charged jump", description = "Stones that need more than one click", section = colours, position = 1)
	default Color chargedColor()
	{
		return new Color(0x5C, 0xE1, 0xFF);
	}

	@Alpha
	@ConfigItem(keyName = "fastColor", name = "Fast route", description = "The one real stone in each row of three", section = colours, position = 2)
	default Color fastColor()
	{
		return new Color(0x5E, 0xE0, 0x7A);
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
