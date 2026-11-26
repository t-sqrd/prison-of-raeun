package com.prisonofraeun;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RaeunPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RaeunPlugin.class);
		RuneLite.main(args);
	}
}
