package com.cubedraeun;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
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
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Cubed's Raeun Agility",
	description = "Prison of Raeun agility course: obstacle outlines, charged jump click counts, lap timer, XP per hour and chest loot",
	tags = {"agility", "raeun", "graveyard", "prison", "course", "lap", "marks", "cubed"}
)
public class RaeunPlugin extends Plugin
{
	private static final int[][] OBSTACLES = {{63570, 63584}, {63589, 63589}, {63593, 63594}, {63596, 63597}, {63621, 63623}, {63636, 63636}};
	private static final int[] SLOW_GAPS = {63573, 63582};
	private static final int[][] CHARGED_GAPS = {{63585, 63588}, {63590, 63592}, {63616, 63617}, {63872, 63885}};
	private static final int FIRST_CHARGED_GAP = 63585;
	private static final int[] CHARGED_CLICKS = {2, 4, 3, 2, 0, 2, 4, 2};
	private static final int STEPPING_STONE = 63595;
	private static final int CHEST = 63481;

	private static final int[][] STONE_GRIDS = {
		{2683, 16930, 10544, 17, -1, 1},
		{2754, 16919, 10518, 11, 1, 0},
	};

	private static final int SUNKEN_TREASURE = 35033;
	private static final int INVENTORY = 93;
	private static final int CHARGE_WINDOW_TICKS = 10;
	private static final int CHARGE_STALE_TICKS = 40;
	private static final int LAP_FINISH_XP = 900;
	private static final int WHOLE_LAP_XP = 1500;
	private static final int MAX_XP_DROP = 5000;
	private static final int IDLE_TICKS = 300;
	private static final String BEST_KEY = "bestLap";

	enum Kind
	{
		OBSTACLE, SLOW, CHARGED, STONE, CHEST
	}

	static final class Tracked
	{
		final Kind kind;
		final int x;
		final int y;
		int rowVarbit;
		int column;
		boolean solidWhenPicked;
		boolean solid = true;

		Tracked(TileObject o, Kind kind)
		{
			this.kind = kind;
			WorldView wv = o.getWorldView();
			if (o instanceof GameObject)
			{
				net.runelite.api.Point min = ((GameObject) o).getSceneMinLocation();
				x = wv.getBaseX() + min.getX();
				y = wv.getBaseY() + min.getY();
			}
			else
			{
				x = wv.getBaseX() + o.getLocalLocation().getSceneX();
				y = wv.getBaseY() + o.getLocalLocation().getSceneY();
			}
		}
	}

	static final class Charge
	{
		final int id;
		final int x;
		final int y;
		int clicks;
		int soundId = -1;
		int lastClickTick;

		Charge(int id, int x, int y, int tick)
		{
			this.id = id;
			this.x = x;
			this.y = y;
			this.lastClickTick = tick;
		}
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
	private final Map<Integer, Kind> kinds = new HashMap<>();
	private final ArrayDeque<Charge> charges = new ArrayDeque<>();
	private boolean stonesInScene;

	private int lastAgilityXp;
	private int lastThievingXp;
	private int treasureHeld = -1;

	int sessionXp;
	int activeTicks;
	int lastXpTick = -1;
	int laps;
	int treasure;

	int lapStartTick = -1;
	int lapXp;
	boolean chestLooted;

	int lastLapTicks;
	boolean lastLapWasBest;
	int bestLapTicks;

	@Provides
	RaeunConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(RaeunConfig.class);
	}

	@Override
	protected void startUp()
	{
		for (int[] r : OBSTACLES)
		{
			for (int id = r[0]; id <= r[1]; id++)
			{
				kinds.put(id, Kind.OBSTACLE);
			}
		}
		for (int id : SLOW_GAPS)
		{
			kinds.put(id, Kind.SLOW);
		}
		for (int[] r : CHARGED_GAPS)
		{
			for (int id = r[0]; id <= r[1]; id++)
			{
				kinds.put(id, Kind.CHARGED);
			}
		}
		kinds.put(STEPPING_STONE, Kind.STONE);
		kinds.put(CHEST, Kind.CHEST);

		loadBest();
		resetSession();
		overlayManager.add(sceneOverlay);
		overlayManager.add(panelOverlay);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
				lastThievingXp = client.getSkillExperience(Skill.THIEVING);
				treasureHeld = countTreasure(client.getItemContainer(INVENTORY));
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
		kinds.clear();
	}

	void resetSession()
	{
		sessionXp = 0;
		activeTicks = 0;
		lastXpTick = -1;
		laps = 0;
		treasure = 0;
		lapStartTick = -1;
		lapXp = 0;
		chestLooted = false;
		lastLapTicks = 0;
		lastLapWasBest = false;
		charges.clear();
	}

	boolean onCourse()
	{
		return !objects.isEmpty();
	}

	static int clicksNeeded(int id)
	{
		int i = id - FIRST_CHARGED_GAP;
		return i >= 0 && i < CHARGED_CLICKS.length ? CHARGED_CLICKS[i] : 0;
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

	private void scanScene()
	{
		objects.clear();
		stonesInScene = false;
		WorldView wv = client.getTopLevelWorldView();
		Scene scene = wv == null ? null : wv.getScene();
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
		Kind kind = kinds.get(o.getId());
		if (kind == null)
		{
			return;
		}
		Tracked t = new Tracked(o, kind);
		if (kind == Kind.STONE)
		{
			placeOnGrid(o, t);
			stonesInScene = true;
		}
		objects.put(o, t);
	}

	private void placeOnGrid(TileObject stone, Tracked t)
	{
		WorldPoint p = WorldPoint.fromLocalInstance(client, stone.getLocalLocation());
		if (p == null)
		{
			return;
		}
		for (int[] g : STONE_GRIDS)
		{
			int dy = p.getY() - g[2];
			int row = dy / 2 * g[4];
			if (p.getX() >= g[0] && p.getX() < g[0] + 3 && dy % 2 == 0 && row >= 0 && row < g[3])
			{
				t.rowVarbit = g[1] + row;
				t.column = p.getX() - g[0];
				t.solidWhenPicked = g[5] == 1;
				return;
			}
		}
	}

	private void refreshStones()
	{
		for (Tracked t : objects.values())
		{
			if (t.rowVarbit > 0)
			{
				boolean picked = client.getVarbitValue(t.rowVarbit) == t.column;
				t.solid = picked == t.solidWhenPicked;
			}
		}
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
			lastAgilityXp = 0;
			lastThievingXp = 0;
			treasureHeld = -1;
			lapStartTick = -1;
			charges.clear();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		loadBest();
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
		Kind kind = kinds.get(id);
		if (kind == null || kind == Kind.CHEST)
		{
			return;
		}
		int tick = client.getTickCount();
		if (lapStartTick < 0)
		{
			startLap(tick);
		}
		if (kind != Kind.CHARGED)
		{
			return;
		}
		WorldView wv = client.getTopLevelWorldView();
		int x = wv.getBaseX() + e.getParam0();
		int y = wv.getBaseY() + e.getParam1();
		Charge last = charges.peekLast();
		if (last != null && last.id == id && last.x == x && last.y == y
			&& tick - last.lastClickTick <= CHARGE_WINDOW_TICKS)
		{
			last.lastClickTick = tick;
		}
		else
		{
			charges.addLast(new Charge(id, x, y, tick));
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed e)
	{
		Charge c = charges.peekLast();
		if (c == null || client.getTickCount() - c.lastClickTick > CHARGE_WINDOW_TICKS)
		{
			return;
		}
		if (c.soundId < 0)
		{
			c.soundId = e.getSoundId();
		}
		if (e.getSoundId() == c.soundId)
		{
			c.clicks++;
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
			int gained = xpGain(lastThievingXp, e.getXp());
			lastThievingXp = e.getXp();
			if (gained > 0 && onCourse())
			{
				chestLooted = true;
			}
			return;
		}
		if (e.getSkill() != Skill.AGILITY)
		{
			return;
		}
		int gained = xpGain(lastAgilityXp, e.getXp());
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
		charges.pollFirst();
		if (gained >= LAP_FINISH_XP)
		{
			finishLap(tick);
		}
	}

	private static int xpGain(int before, int now)
	{
		int gained = now - before;
		return before > 0 && gained > 0 && gained <= MAX_XP_DROP ? gained : 0;
	}

	private void finishLap(int tick)
	{
		int ticks = tick - lapStartTick;
		boolean whole = lapXp >= WHOLE_LAP_XP && ticks > 0;
		laps++;
		lastLapTicks = whole ? ticks : 0;
		lastLapWasBest = false;
		if (whole && (bestLapTicks <= 0 || ticks < bestLapTicks))
		{
			lastLapWasBest = bestLapTicks > 0;
			bestLapTicks = ticks;
			configManager.setRSProfileConfiguration(RaeunConfig.GROUP, BEST_KEY, ticks);
		}
		if (whole && config.lapChatMessage())
		{
			String text = "Raeun lap " + laps + ": " + formatTicks(ticks) + (lastLapWasBest ? ". New best." : ".");
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
		if (client.getTickCount() - lastXpTick <= IDLE_TICKS)
		{
			activeTicks++;
		}
		else
		{
			lapStartTick = -1;
			lapXp = 0;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() != INVENTORY)
		{
			return;
		}
		int held = countTreasure(e.getItemContainer());
		if (treasureHeld >= 0 && held > treasureHeld && onCourse())
		{
			treasure += held - treasureHeld;
		}
		treasureHeld = held;
	}

	private static int countTreasure(ItemContainer container)
	{
		if (container == null)
		{
			return -1;
		}
		int count = 0;
		for (Item item : container.getItems())
		{
			if (item.getId() == SUNKEN_TREASURE)
			{
				count += item.getQuantity();
			}
		}
		return count;
	}

	private double activeHours()
	{
		return activeTicks * 0.6 / 3600.0;
	}

	int xpPerHour()
	{
		double hours = activeHours();
		return hours < 0.002 ? 0 : (int) Math.round(sessionXp / hours);
	}

	double treasurePerHour()
	{
		double hours = activeHours();
		return hours < 0.002 ? 0 : treasure / hours;
	}

	static String formatTicks(int ticks)
	{
		int tenths = ticks * 6;
		int minutes = tenths / 600;
		int seconds = (tenths / 10) % 60;
		return minutes + ":" + (seconds < 10 ? "0" : "") + seconds + "." + (tenths % 10);
	}

	private void loadBest()
	{
		bestLapTicks = 0;
		String saved = configManager.getRSProfileConfiguration(RaeunConfig.GROUP, BEST_KEY);
		if (saved != null)
		{
			try
			{
				bestLapTicks = Integer.parseInt(saved.trim());
			}
			catch (NumberFormatException ex)
			{
				bestLapTicks = 0;
			}
		}
	}
}
