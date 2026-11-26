package com.cubedraeun;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LapTimeFormatTest
{
	@Test
	public void formatsTicksAsMinutesSecondsTenths()
	{
		assertEquals("0:00.0", RaeunPlugin.formatTicks(0));
		assertEquals("0:00.6", RaeunPlugin.formatTicks(1));
		assertEquals("0:59.4", RaeunPlugin.formatTicks(99));
		assertEquals("1:00.0", RaeunPlugin.formatTicks(100));
		assertEquals("1:39.6", RaeunPlugin.formatTicks(166));
		assertEquals("3:45.0", RaeunPlugin.formatTicks(375));
	}
}
