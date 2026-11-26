package com.cubedraeun;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Cubed's Raeun Agility",
	description = "Prison of Raeun agility course: obstacle outlines, charged jump click counter, lap times for the fast and slow routes, XP per hour and chest loot",
	tags = {"agility", "raeun", "graveyard", "prison", "course", "lap", "marks", "cubed"}
)
public class RaeunPlugin extends Plugin
{
	private static final int[][] CHARGED_GAPS = {{63608, 63613}, {63616, 63617}, {63872, 63885}};
	private static final int[] CHARGED_CLICKS = {2, 4, 3, 2, 2, 4};
	private static final int FIRST_CHARGED_GAP = 63608;
	private static final int[] SLOW_GAPS = {63574, 63575, 63576, 63577, 63584};
	private static final int[][] SHARED_GAPS = {{63570, 63573}, {63578, 63583}, {63585, 63594}, {63596, 63597}, {63614, 63615}, {63621, 63623}, {63636, 63636}};
	private static final int STEPPING_STONE = 63595;
	private static final int SOLID_STONE = 63484;
	private static final int CHEST = 63481;
	private static final int STONE_LOOKUP_TRIES = 5;

	static final int SUNKEN_TREASURE = 35033;
	static final int GRACEFUL_KIT = 35027;
	static final int FREMENNIK_FABRIC = 35029;
	static final int GHOSTLY_FABRIC = 35031;
	private static final int[] LOOT_IDS = {SUNKEN_TREASURE, GRACEFUL_KIT, FREMENNIK_FABRIC, GHOSTLY_FABRIC};

	private static final int INVENTORY = 93;
	private static final int CHARGE_WINDOW_TICKS = 10;
	private static final int CHARGE_STALE_TICKS = 40;
	private static final int DEFAULT_LAP_XP = 2298;

	enum Route
	{
		FAST("Fast"), MIXED("Mixed"), SLOW("Slow"), PARTIAL("Partial");

		final String label;

		Route(String label)
		{
			this.label = label;
		}
	}

	static final class Charge
	{
		final int id;
		final int x;
		final int y;
		final int plane;
		int clicks = 1;
		int lastClickTick;

		Charge(int id, int x, int y, int plane, int tick)
		{
			this.id = id;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.lastClickTick = tick;
		}

		String key()
		{
			return x + "," + y + "," + plane;
		}
	}

	enum Kind
	{
		FAST, SLOW, SHARED, CHEST
	}

	static final class Tracked
	{
		final Kind kind;
		final boolean stone;
		final int sx;
		final int sy;
		final int x;
		final int y;
		final String key;
		boolean solid = true;
		int stoneId = -1;
		int lookups;

		Tracked(TileObject o, Kind kind, boolean stone)
		{
			this.kind = kind;
			this.stone = stone;
			WorldView wv = o.getWorldView();
			if (o instanceof GameObject)
			{
				net.runelite.api.Point min = ((GameObject) o).getSceneMinLocation();
				sx = min.getX();
				sy = min.getY();
			}
			else
			{
				sx = o.getLocalLocation().getSceneX();
				sy = o.getLocalLocation().getSceneY();
			}
			x = wv.getBaseX() + sx;
			y = wv.getBaseY() + sy;
			key = x + "," + y + "," + o.getPlane();
		}
	}

	static final class Learned
	{
		int xp;
		int clicks;
		boolean confirmed;
	}

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private RaeunConfig config;
	@Inject
	private RaeunSceneOverlay sceneOverlay;
	@Inject
	private RaeunPanelOverlay panelOverlay;

	final Map<TileObject, Tracked> objects = new IdentityHashMap<>();
	final Map<String, Learned> learned = new HashMap<>();
	final ArrayDeque<Charge> charges = new ArrayDeque<>();
	private final Set<Integer> obstacleIds = new HashSet<>();
	private final Map<Integer, Kind> kinds = new HashMap<>();
	private boolean stonesInScene;
	private final Map<Integer, Integer> inventoryCounts = new HashMap<>();
	private boolean inventoryKnown;

	private int lastAgilityXp = -1;
	private int lastThievingXp = -1;

	int sessionXp;
	int activeTicks;
	int lastXpTick = -1;
	int laps;
	final int[] routeLaps = new int[Route.values().length];
	int timedLaps;
	int timedLapTicks;
	int timedLapXp;
	int chestsOpened;
	int treasure;
	int kits;
	int fabrics;
	int ghostlyFabrics;

	int lapStartTick = -1;
	int lapXp;
	boolean chestLooted;

	int lastLapTicks = -1;
	Route lastLapRoute;
	boolean lastLapWasBest;
	int lastJumpClicks;
	int lastJumpXp;
	boolean lastJumpHalf;
	int lastJumpTick = -1;

	final int[] best = new int[Route.values().length];

	@Provides
	RaeunConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(RaeunConfig.class);
	}

	@Override
	protected void startUp()
	{
		rebuildIds();
		loadLearned();
		loadBests();
		resetSession();
		overlayManager.add(sceneOverlay);
		overlayManager.add(panelOverlay);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
				lastThievingXp = client.getSkillExperience(Skill.THIEVING);
				readInventory(client.getItemContainer(INVENTORY));
				scanScene();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(panelOverlay);
		objects.clear();
		charges.clear();
	}

	void resetSession()
	{
		sessionXp = 0;
		activeTicks = 0;
		lastXpTick = -1;
		laps = 0;
		java.util.Arrays.fill(routeLaps, 0);
		timedLaps = 0;
		timedLapTicks = 0;
		timedLapXp = 0;
		chestsOpened = 0;
		treasure = 0;
		kits = 0;
		fabrics = 0;
		ghostlyFabrics = 0;
		lapStartTick = -1;
		lapXp = 0;
		chestLooted = false;
		lastLapTicks = -1;
		lastLapRoute = null;
		lastLapWasBest = false;
		lastJumpTick = -1;
		charges.clear();
	}

	boolean onCourse()
	{
		return !objects.isEmpty();
	}


	private void rebuildIds()
	{
		kinds.clear();
		for (int id : parseIds(config.extraIds()))
		{
			kinds.put(id, Kind.SHARED);
		}
		for (int[] r : SHARED_GAPS)
		{
			for (int i = r[0]; i <= r[1]; i++)
			{
				kinds.put(i, Kind.SHARED);
			}
		}
		for (int id : SLOW_GAPS)
		{
			kinds.put(id, Kind.SLOW);
		}
		kinds.put(STEPPING_STONE, Kind.SLOW);
		for (int[] r : CHARGED_GAPS)
		{
			for (int i = r[0]; i <= r[1]; i++)
			{
				kinds.put(i, Kind.FAST);
			}
		}
		kinds.put(CHEST, Kind.CHEST);
		kinds.keySet().removeAll(parseIds(config.ignoreIds()));
		obstacleIds.clear();
		obstacleIds.addAll(kinds.keySet());
		obstacleIds.remove(CHEST);
	}

	static int wikiClicks(int id)
	{
		int i = id - FIRST_CHARGED_GAP;
		return i >= 0 && i < CHARGED_CLICKS.length ? CHARGED_CLICKS[i] : 0;
	}

	int clicksNeeded(int id, String key)
	{
		Learned l = config.learnGaps() ? learned.get(key) : null;
		return l != null && l.clicks > 1 ? l.clicks : wikiClicks(id);
	}

	boolean clicksConfirmed(String key)
	{
		Learned l = config.learnGaps() ? learned.get(key) : null;
		return l != null && l.confirmed;
	}

	private void refreshStones()
	{
		boolean swap = config.swapStones();
		for (Map.Entry<TileObject, Tracked> en : objects.entrySet())
		{
			Tracked t = en.getValue();
			if (!t.stone)
			{
				continue;
			}
			if (t.stoneId < 0 && t.lookups < STONE_LOOKUP_TRIES)
			{
				t.lookups++;
				t.stoneId = findStoneBelow(en.getKey(), t);
			}
			if (t.stoneId < 0)
			{
				continue;
			}
			ObjectComposition shown = client.getObjectDefinition(t.stoneId).getImpostor();
			t.solid = shown == null || (shown.getId() == SOLID_STONE) != swap;
		}
	}

	private int findStoneBelow(TileObject gap, Tracked t)
	{
		Tile[][][] tiles = gap.getWorldView().getScene().getTiles();
		for (int plane = gap.getPlane() - 1; plane >= 0; plane--)
		{
			Tile tile = tiles[plane][t.sx][t.sy];
			if (tile == null || tile.getGameObjects() == null)
			{
				continue;
			}
			for (GameObject below : tile.getGameObjects())
			{
				if (below == null)
				{
					continue;
				}
				int[] variants = client.getObjectDefinition(below.getId()).getImpostorIds();
				if (variants == null)
				{
					continue;
				}
				for (int v : variants)
				{
					if (v == SOLID_STONE)
					{
						return below.getId();
					}
				}
			}
		}
		return -1;
	}

	private static Set<Integer> parseIds(String text)
	{
		Set<Integer> out = new HashSet<>();
		if (text == null)
		{
			return out;
		}
		for (String part : text.split("[^0-9]+"))
		{
			if (!part.isEmpty() && part.length() < 8)
			{
				out.add(Integer.parseInt(part));
			}
		}
		return out;
	}

	private void scanScene()
	{
		objects.clear();
		stonesInScene = false;
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		Scene scene = wv.getScene();
		if (scene == null)
		{
			return;
		}
		for (Tile[][] plane : scene.getTiles())
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						for (GameObject o : gameObjects)
						{
							track(o);
						}
					}
					track(tile.getGroundObject());
					track(tile.getDecorativeObject());
					track(tile.getWallObject());
				}
			}
		}
	}

	private void track(TileObject o)
	{
		if (o == null)
		{
			return;
		}
		int id = o.getId();
		Kind kind = kinds.get(id);
		if (kind != null)
		{
			boolean stone = id == STEPPING_STONE;
			objects.put(o, new Tracked(o, kind, stone));
			stonesInScene |= stone;
		}
	}

	boolean chestOpenable(TileObject o)
	{
		ObjectComposition comp = client.getObjectDefinition(o.getId());
		if (comp != null && comp.getImpostorIds() != null)
		{
			comp = comp.getImpostor();
		}
		if (comp == null || comp.getActions() == null)
		{
			return false;
		}
		for (String action : comp.getActions())
		{
			if ("Open".equals(action))
			{
				return true;
			}
		}
		return false;
	}

	Charge currentCharge()
	{
		Charge c = charges.peekLast();
		if (c == null || client.getTickCount() - c.lastClickTick > CHARGE_STALE_TICKS)
		{
			return null;
		}
		return c;
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		track(e.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		objects.remove(e.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned e)
	{
		track(e.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned e)
	{
		objects.remove(e.getGroundObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned e)
	{
		track(e.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned e)
	{
		objects.remove(e.getDecorativeObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned e)
	{
		track(e.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned e)
	{
		objects.remove(e.getWallObject());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState state = e.getGameState();
		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			objects.clear();
			stonesInScene = false;
		}
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			lastAgilityXp = -1;
			lastThievingXp = -1;
			inventoryKnown = false;
			lapStartTick = -1;
			charges.clear();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		loadBests();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!RaeunConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		String key = e.getKey();
		if ("extraIds".equals(key) || "ignoreIds".equals(key))
		{
			rebuildIds();
			clientThread.invoke(() ->
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					scanScene();
				}
			});
		}
		else if ("forgetGaps".equals(key) && config.forgetGaps())
		{
			learned.clear();
			saveLearned();
			configManager.setConfiguration(RaeunConfig.GROUP, "forgetGaps", "false");
		}
	}


	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		MenuAction action = e.getMenuAction();
		if (action != MenuAction.GAME_OBJECT_FIRST_OPTION && action != MenuAction.GAME_OBJECT_SECOND_OPTION
			&& action != MenuAction.GAME_OBJECT_THIRD_OPTION && action != MenuAction.GAME_OBJECT_FOURTH_OPTION
			&& action != MenuAction.GAME_OBJECT_FIFTH_OPTION)
		{
			return;
		}
		int id = e.getId();
		if (!obstacleIds.contains(id))
		{
			return;
		}
		int tick = client.getTickCount();
		if (lapStartTick < 0)
		{
			startLap(tick);
		}
		if (kinds.get(id) != Kind.FAST)
		{
			return;
		}
		WorldView wv = client.getTopLevelWorldView();
		int x = wv.getBaseX() + e.getParam0();
		int y = wv.getBaseY() + e.getParam1();
		int plane = wv.getPlane();
		Charge last = charges.peekLast();
		if (last != null && last.id == id && last.x == x && last.y == y && last.plane == plane
			&& tick - last.lastClickTick <= CHARGE_WINDOW_TICKS)
		{
			last.clicks++;
			last.lastClickTick = tick;
		}
		else
		{
			charges.addLast(new Charge(id, x, y, plane, tick));
		}
	}

	private void startLap(int tick)
	{
		lapStartTick = tick;
		lapXp = 0;
		chestLooted = false;
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		if (e.getSkill() == Skill.THIEVING)
		{
			int gained = lastThievingXp < 0 ? 0 : e.getXp() - lastThievingXp;
			lastThievingXp = e.getXp();
			if (gained > 0 && onCourse())
			{
				chestLooted = true;
				chestsOpened++;
			}
			return;
		}
		if (e.getSkill() != Skill.AGILITY)
		{
			return;
		}
		int gained = lastAgilityXp < 0 ? 0 : e.getXp() - lastAgilityXp;
		lastAgilityXp = e.getXp();
		if (gained <= 0 || !onCourse())
		{
			return;
		}
		int tick = client.getTickCount();
		sessionXp += gained;
		lastXpTick = tick;
		if (lapStartTick < 0)
		{
			startLap(tick);
		}
		lapXp += gained;

		boolean finish = gained >= config.lapFinishXp();
		creditJump(gained, tick, finish);
		if (finish)
		{
			finishLap(tick);
		}
	}

	private void creditJump(int gained, int tick, boolean finish)
	{
		Charge c = null;
		while (!charges.isEmpty())
		{
			Charge head = charges.pollFirst();
			if (tick - head.lastClickTick <= CHARGE_STALE_TICKS)
			{
				c = head;
				break;
			}
		}
		if (c == null || finish)
		{
			return;
		}
		Player me = client.getLocalPlayer();
		if (me == null || me.getWorldLocation().distanceTo(new WorldPoint(c.x, c.y, c.plane)) > 10)
		{
			return;
		}
		lastJumpClicks = c.clicks;
		lastJumpXp = gained;
		lastJumpTick = tick;
		lastJumpHalf = false;

		Learned l = learned.get(c.key());
		if (l == null)
		{
			l = new Learned();
			l.xp = gained;
			l.clicks = c.clicks;
			learned.put(c.key(), l);
		}
		else if (gained > l.xp)
		{
			l.xp = gained;
			l.clicks = c.clicks;
			l.confirmed = true;
		}
		else if (gained < l.xp)
		{
			l.confirmed = true;
			lastJumpHalf = true;
		}
		else if (c.clicks < l.clicks)
		{
			l.clicks = c.clicks;
		}
		else
		{
			return;
		}
		if (config.learnGaps())
		{
			saveLearned();
		}
	}

	private void finishLap(int tick)
	{
		int ticks = tick - lapStartTick;
		Route route;
		if (lapXp < config.lapFinishXp() + 600)
		{
			route = Route.PARTIAL;
		}
		else if (lapXp <= config.fastMaxXp())
		{
			route = Route.FAST;
		}
		else if (lapXp >= config.slowMinXp())
		{
			route = Route.SLOW;
		}
		else
		{
			route = Route.MIXED;
		}
		laps++;
		routeLaps[route.ordinal()]++;
		lastLapRoute = route;
		lastLapTicks = ticks;
		lastLapWasBest = false;
		if (route != Route.PARTIAL)
		{
			timedLaps++;
			timedLapTicks += ticks;
			timedLapXp += lapXp;
			int old = best[route.ordinal()];
			if (old <= 0 || ticks < old)
			{
				best[route.ordinal()] = ticks;
				lastLapWasBest = old > 0;
				configManager.setRSProfileConfiguration(RaeunConfig.GROUP, "best" + route.label, ticks);
			}
		}
		if (config.lapChatMessage())
		{
			String text = "Raeun lap " + laps + " (" + route.label.toLowerCase() + "): "
				+ (route == Route.PARTIAL ? "not timed, started part way" : formatTicks(ticks) + ", " + lapXp + " xp")
				+ (lastLapWasBest ? ". New best" : "") + ".";
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", text, null);
		}
		lapStartTick = -1;
		lapXp = 0;
		chestLooted = false;
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		if (stonesInScene)
		{
			refreshStones();
		}
		if (lastXpTick < 0)
		{
			return;
		}
		int idle = client.getTickCount() - lastXpTick;
		int limit = config.idleMinutes() * 100;
		if (idle <= limit)
		{
			activeTicks++;
		}
		else if (lapStartTick >= 0)
		{
			lapStartTick = -1;
			lapXp = 0;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() == INVENTORY)
		{
			readInventory(e.getItemContainer());
		}
	}

	private void readInventory(ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		Map<Integer, Integer> now = new HashMap<>();
		for (Item item : container.getItems())
		{
			for (int id : LOOT_IDS)
			{
				if (item.getId() == id)
				{
					now.merge(id, item.getQuantity(), Integer::sum);
				}
			}
		}
		if (inventoryKnown && onCourse())
		{
			treasure += gain(now, SUNKEN_TREASURE);
			kits += gain(now, GRACEFUL_KIT);
			fabrics += gain(now, FREMENNIK_FABRIC);
			ghostlyFabrics += gain(now, GHOSTLY_FABRIC);
		}
		inventoryCounts.clear();
		inventoryCounts.putAll(now);
		inventoryKnown = true;
	}

	private int gain(Map<Integer, Integer> now, int id)
	{
		return Math.max(0, now.getOrDefault(id, 0) - inventoryCounts.getOrDefault(id, 0));
	}


	double activeHours()
	{
		return activeTicks * 0.6 / 3600.0;
	}

	int xpPerHour()
	{
		double h = activeHours();
		return h < 0.002 ? 0 : (int) Math.round(sessionXp / h);
	}

	double perHour(int count)
	{
		double h = activeHours();
		return h < 0.002 ? 0 : count / h;
	}

	int averageLapXp()
	{
		return timedLaps == 0 ? DEFAULT_LAP_XP : timedLapXp / timedLaps;
	}

	int lapsToLevel()
	{
		int xp = client.getSkillExperience(Skill.AGILITY);
		int level = Experience.getLevelForXp(xp);
		if (level >= 99)
		{
			return -1;
		}
		int left = Experience.getXpForLevel(level + 1) - xp;
		return (int) Math.ceil(left / (double) averageLapXp());
	}

	static String formatTicks(int ticks)
	{
		int tenths = ticks * 6;
		int minutes = tenths / 600;
		int seconds = (tenths / 10) % 60;
		return minutes + ":" + (seconds < 10 ? "0" : "") + seconds + "." + (tenths % 10);
	}


	private void loadBests()
	{
		for (Route r : Route.values())
		{
			best[r.ordinal()] = 0;
			String v = configManager.getRSProfileConfiguration(RaeunConfig.GROUP, "best" + r.label);
			if (v != null)
			{
				try
				{
					best[r.ordinal()] = Integer.parseInt(v.trim());
				}
				catch (NumberFormatException ignored)
				{
					best[r.ordinal()] = 0;
				}
			}
		}
	}

	private void loadLearned()
	{
		learned.clear();
		String raw = configManager.getConfiguration(RaeunConfig.GROUP, "gapData");
		if (raw == null)
		{
			return;
		}
		for (String entry : raw.split(";"))
		{
			String[] p = entry.split(":");
			if (p.length != 4)
			{
				continue;
			}
			try
			{
				Learned l = new Learned();
				l.xp = Integer.parseInt(p[1]);
				l.clicks = Integer.parseInt(p[2]);
				l.confirmed = "1".equals(p[3]);
				learned.put(p[0], l);
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	private void saveLearned()
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Learned> en : learned.entrySet())
		{
			Learned l = en.getValue();
			sb.append(en.getKey()).append(':').append(l.xp).append(':').append(l.clicks)
				.append(':').append(l.confirmed ? '1' : '0').append(';');
		}
		configManager.setConfiguration(RaeunConfig.GROUP, "gapData", sb.toString());
	}
}
