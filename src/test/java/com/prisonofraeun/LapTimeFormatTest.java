package com.prisonofraeun;

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

	@Test
	public void parsesGameLapTimes()
	{
		assertEquals(23520, RaeunPlugin.parseTime("3:55.20"));
		assertEquals(10920, RaeunPlugin.parseTime("1:49.20."));
		assertEquals(372300, RaeunPlugin.parseTime("1:02:03.00"));
		assertEquals("3:57.20", RaeunPlugin.formatCs(RaeunPlugin.parseTime("3:57.20")));
		assertEquals("3:00.00", RaeunPlugin.formatCs(RaeunPlugin.parseTime("3:00.00")));
		assertEquals("0:05.04", RaeunPlugin.formatCs(504));
		assertEquals(0, RaeunPlugin.parseTime("abc"));
	}
}
