package com.prisonofraeun;

import com.prisonofraeun.RaeunConfig.Highlight;
import com.prisonofraeun.RaeunPlugin.Kind;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HighlightTest
{
	private static final int SHARED_GAP = 63575;
	private static final int SLOW_GAP = 63577;
	private static final int BOARDS = 63584;
	private static final int CHARGED = 63586;
	private static final int PILLAR_SHORTCUT = 63589;
	private static final int MAST = 63616;
	private static final int STONE = 63595;
	private static final int LINE_STONE = 63596;
	private static final int LINE_END = 63572;

	@Test
	public void allOutlinesEverything()
	{
		for (Kind kind : Kind.values())
		{
			assertTrue(RaeunPlugin.outlined(Highlight.ALL, kind, SHARED_GAP, false));
		}
	}

	@Test
	public void fastHidesOnlyTheSlowGaps()
	{
		assertFalse(RaeunPlugin.outlined(Highlight.FAST, Kind.SLOW, SLOW_GAP, false));
		assertTrue(RaeunPlugin.outlined(Highlight.FAST, Kind.FAST, BOARDS, false));
		assertTrue(RaeunPlugin.outlined(Highlight.FAST, Kind.CHARGED, CHARGED, false));
		assertTrue(RaeunPlugin.outlined(Highlight.FAST, Kind.OBSTACLE, SHARED_GAP, false));
		assertTrue(RaeunPlugin.outlined(Highlight.FAST, Kind.STONE, STONE, false));
	}

	@Test
	public void slowKeepsSharedAndSlowOnly()
	{
		assertTrue(RaeunPlugin.outlined(Highlight.SLOW, Kind.SLOW, SLOW_GAP, false));
		assertTrue(RaeunPlugin.outlined(Highlight.SLOW, Kind.OBSTACLE, SHARED_GAP, false));
		// Only the first stone of the line needs a click on the slow route.
		assertTrue(RaeunPlugin.outlined(Highlight.SLOW, Kind.OBSTACLE, LINE_STONE, true));
		assertFalse(RaeunPlugin.outlined(Highlight.SLOW, Kind.OBSTACLE, LINE_STONE, false));
		assertFalse(RaeunPlugin.outlined(Highlight.SLOW, Kind.OBSTACLE, LINE_END, false));
		assertTrue(RaeunPlugin.outlined(Highlight.FAST, Kind.OBSTACLE, LINE_STONE, false));
		assertTrue(RaeunPlugin.outlined(Highlight.ALL, Kind.OBSTACLE, LINE_END, false));
		assertFalse(RaeunPlugin.outlined(Highlight.SLOW, Kind.STONE, STONE, false));
		assertFalse(RaeunPlugin.outlined(Highlight.SLOW, Kind.FAST, BOARDS, false));
		assertFalse(RaeunPlugin.outlined(Highlight.SLOW, Kind.FAST, PILLAR_SHORTCUT, false));
		assertFalse(RaeunPlugin.outlined(Highlight.SLOW, Kind.CHARGED, CHARGED, false));
		// Charged jumps away from the course are not part of either route.
		assertTrue(RaeunPlugin.outlined(Highlight.SLOW, Kind.CHARGED, MAST, false));
	}

	@Test
	public void anUnknownSavedChoiceFallsBackToAll()
	{
		assertTrue(RaeunPlugin.outlined(null, Kind.SLOW, SLOW_GAP, false));
	}
}
