# Cubed's Raeun Agility

A RuneLite plugin for the Prison of Raeun agility course on The Graveyard (65 Agility, released 25 November 2026).

## What it shows

**On the course**

- An outline on every obstacle, coloured by route: green for the charged jumps (fast), yellow for the stepping stones and boats that skip them (slow), orange for obstacles every lap passes.
- Only the stepping stones that hold your weight are outlined. The loose planks beside them are left alone.
- On each charged jump, the clicks it needs and a counter while you charge it, for example `2 / 4`.
- The reward chest, outlined during a lap until you loot it. Needs 65 Thieving.

**In the info box**

- Current lap, last lap, session average and your best time for each route.
- Laps, laps per hour, XP gained and XP per hour. The clock pauses after 3 minutes without Agility XP.
- Laps left to your next Agility level.
- Chests opened and sunken treasure per hour (Volf Olafson trades each one for a mark of grace), plus graceful kits and sail fabric.
- The click order for both charged jump sections: 2-4-3-2, then 2-4-2.

Shift + right-click the info box to reset the session.

## Fast and slow routes

A lap is sorted by the Agility XP it gave. Taking every charged jump is worth 2,298 XP and counts as fast. Skipping all of them is worth 2,960 XP and counts as slow. Anything between is mixed. Each route keeps its own best time, saved per account.

A lap you joined part way through is counted but not timed.

## Charged jumps

Each charged jump starts with the click count from the wiki, shown as `x4?`. A wrong count still completes the jump, for half the XP, and the plugin uses that to check the number: it remembers the click count that paid the most at that gap. The `?` goes once the gap has paid two different amounts, which is when the plugin knows which count was right.

Learned counts are shared between accounts. "Forget learned gaps" in the Advanced settings clears them.

## Settings worth knowing

- **Lap finish XP** (default 900). A single Agility drop this large ends the lap. The last gap gives 948.
- **Swap stepping stones.** Tick it if the outline lands on the loose planks.
- **Extra obstacle ids / Ignored ids.** Add or remove object ids if an obstacle is missed or something is outlined by mistake.
