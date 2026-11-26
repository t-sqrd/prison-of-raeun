package com.cubedraeun;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(RaeunConfig.GROUP)
public interface RaeunConfig extends Config
{
	String GROUP = "cubedraeun";

	@ConfigSection(name = "Obstacles", description = "What gets outlined on the course", position = 0)
	String obstacles = "obstacles";

	@ConfigSection(name = "Charged jumps", description = "Click counter and learned click counts", position = 1)
	String charged = "charged";

	@ConfigSection(name = "Info box", description = "Lap times, XP and loot", position = 2)
	String panel = "panel";

	@ConfigSection(name = "Advanced", description = "Lap detection and object ids", position = 3, closedByDefault = true)
	String advanced = "advanced";

	@ConfigItem(keyName = "highlightObstacles", name = "Outline obstacles", description = "Outline the clickable part of every gap, mast and ledge", section = obstacles, position = 0)
	default boolean highlightObstacles()
	{
		return true;
	}

	@Range(min = 5, max = 60)
	@ConfigItem(keyName = "drawDistance", name = "Draw distance", description = "Only outline obstacles within this many tiles", section = obstacles, position = 1)
	default int drawDistance()
	{
		return 28;
	}

	@Alpha
	@ConfigItem(keyName = "obstacleColor", name = "Obstacle", description = "Outline colour for every obstacle on the course", section = obstacles, position = 2)
	default Color obstacleColor()
	{
		return new Color(0xFF, 0xC8, 0x3D);
	}

	@Alpha
	@ConfigItem(keyName = "chargedColor", name = "Charged jump", description = "Outline colour for the stones that need a charged jump", section = obstacles, position = 3)
	default Color chargedColor()
	{
		return new Color(0x5C, 0xE1, 0xFF);
	}

	@Alpha
	@ConfigItem(keyName = "fastColor", name = "Fast route stones", description = "Outline colour for the one real stone in each row of three", section = obstacles, position = 4)
	default Color fastColor()
	{
		return new Color(0x5E, 0xE0, 0x7A);
	}

	@Alpha
	@ConfigItem(keyName = "slowColor", name = "Slow route", description = "Outline colour for the gaps that start the slow way round, drawn dashed", section = obstacles, position = 4)
	default Color slowColor()
	{
		return new Color(0xFF, 0x6B, 0x5C);
	}

	@Alpha
	@ConfigItem(keyName = "chargingColor", name = "Gap being charged", description = "Outline colour for the charged jump you are clicking", section = obstacles, position = 5)
	default Color chargingColor()
	{
		return Color.WHITE;
	}

	@Range(min = 0, max = 120)
	@ConfigItem(keyName = "fillOpacity", name = "Fill opacity", description = "0 draws the outline only", section = obstacles, position = 6)
	default int fillOpacity()
	{
		return 35;
	}

	@ConfigItem(keyName = "highlightChest", name = "Outline chest until looted", description = "Outline the reward chest during a lap until you have looted it (needs 65 Thieving)", section = obstacles, position = 7)
	default boolean highlightChest()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "chestColor", name = "Chest", description = "Outline colour for the reward chest", section = obstacles, position = 8)
	default Color chestColor()
	{
		return new Color(0xFF, 0x5F, 0xD2);
	}

	@ConfigItem(keyName = "showClickCounter", name = "Count clicks on charged jumps", description = "Show how many times you have clicked the charged jump, next to how many it needs", section = charged, position = 0)
	default boolean showClickCounter()
	{
		return true;
	}

	@ConfigItem(keyName = "learnGaps", name = "Learn click counts", description = "Remember, per gap, the click count that gave full XP and label the gap with it", section = charged, position = 1)
	default boolean learnGaps()
	{
		return true;
	}

	@ConfigItem(keyName = "showSequence", name = "Show wiki click order", description = "Show the click order from the wiki in the info box", section = charged, position = 2)
	default boolean showSequence()
	{
		return true;
	}

	@ConfigItem(keyName = "firstSequence", name = "First jump section", description = "Clicks per gap, in order", section = charged, position = 3)
	default String firstSequence()
	{
		return "2-4-3-2";
	}

	@ConfigItem(keyName = "lastSequence", name = "Last jump section", description = "Clicks per gap, in order", section = charged, position = 4)
	default String lastSequence()
	{
		return "2-4-2";
	}

	@ConfigItem(keyName = "showPanel", name = "Show info box", description = "Show the info box while you are on the course", section = panel, position = 0)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(keyName = "showLapTimes", name = "Lap times", description = "Current lap, last lap and your best for each route", section = panel, position = 1)
	default boolean showLapTimes()
	{
		return true;
	}

	@ConfigItem(keyName = "showXp", name = "XP rates", description = "XP gained, XP per hour and laps per hour", section = panel, position = 2)
	default boolean showXp()
	{
		return true;
	}

	@ConfigItem(keyName = "showGoal", name = "Laps to next level", description = "Laps left until your next Agility level, at your session average", section = panel, position = 3)
	default boolean showGoal()
	{
		return true;
	}

	@ConfigItem(keyName = "showLoot", name = "Chest loot", description = "Sunken treasure (1 mark of grace each) and other chest drops", section = panel, position = 4)
	default boolean showLoot()
	{
		return true;
	}

	@ConfigItem(keyName = "lapChatMessage", name = "Lap time in chat", description = "Post each lap time to the chat box", section = panel, position = 5)
	default boolean lapChatMessage()
	{
		return true;
	}

	@Range(min = 1, max = 30)
	@ConfigItem(keyName = "idleMinutes", name = "Pause after idle (min)", description = "Stop the per-hour clock after this many minutes without Agility XP", section = panel, position = 6)
	default int idleMinutes()
	{
		return 3;
	}

	@ConfigItem(keyName = "lapFinishXp", name = "Lap finish XP", description = "A single Agility XP drop of at least this much ends the lap (the final gap gives 948)", section = advanced, position = 0)
	default int lapFinishXp()
	{
		return 900;
	}

	@ConfigItem(keyName = "fastMaxXp", name = "Fast lap max XP", description = "Laps worth this much XP or less count as the fast route (all jumps is 2,298)", section = advanced, position = 1)
	default int fastMaxXp()
	{
		return 2400;
	}

	@ConfigItem(keyName = "slowMinXp", name = "Slow lap min XP", description = "Laps worth this much XP or more count as the slow route (no jumps is 2,960)", section = advanced, position = 2)
	default int slowMinXp()
	{
		return 2900;
	}

	@ConfigItem(keyName = "extraIds", name = "Extra obstacle ids", description = "Object ids to outline as well, comma separated", section = advanced, position = 3)
	default String extraIds()
	{
		return "";
	}

	@ConfigItem(keyName = "ignoreIds", name = "Ignored ids", description = "Object ids never to outline, comma separated", section = advanced, position = 4)
	default String ignoreIds()
	{
		return "";
	}

	@ConfigItem(keyName = "swapStones", name = "Swap stepping stones", description = "Tick this if the outline lands on the loose planks and misses the real stones", section = advanced, position = 6)
	default boolean swapStones()
	{
		return false;
	}

	@ConfigItem(keyName = "forgetGaps", name = "Forget learned gaps", description = "Tick to clear every learned click count", section = advanced, position = 5)
	default boolean forgetGaps()
	{
		return false;
	}
}
