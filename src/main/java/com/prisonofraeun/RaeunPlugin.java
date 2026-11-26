package com.prisonofraeun;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
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
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Prison of Raeun",
	description = "Shows you the quickest way through the Prison of Raeun, tells you how many clicks each charged jump needs, and keeps track of your laps, XP and loot",
	tags = {"agility", "raeun", "graveyard", "prison", "course", "lap", "marks"}
)
public class RaeunPlugin extends Plugin
{
	private static final Logger LOG = LoggerFactory.getLogger(RaeunPlugin.class);
	private static final int[][] OBSTACLES = {{63570, 63584}, {63589, 63589}, {63593, 63594}, {63596, 63597}, {63621, 63623}, {63636, 63636}};
	private static final int[] SLOW_GAPS = {63573, 63577, 63581, 63582};
	// Obstacles only the fast route uses.
	private static final int[] FAST_GAPS = {63584, 63589, 63593, 63594};
	static final int START = 63570;
	private static final Pattern LAP_DURATION = Pattern.compile("Lap duration: ([\\d:.]+)");
	private static final Pattern PERSONAL_BEST = Pattern.compile("Personal best: ([\\d:.]+)");
	private static final int[][] CHARGED_GAPS = {{63585, 63588}, {63590, 63592}, {63616, 63617}, {63872, 63885}};
	private static final int FIRST_CHARGED_GAP = 63585;
	private static final int[] CHARGED_CLICKS = {2, 4, 3, 2, 0, 2, 4, 2};
	private static final int STEPPING_STONE = 63595;
	private static final int CHEST = 63481;
	private static final int FINAL_JUMP = 63583;
	static final int CHEST_THIEVING = 65;

	private static final int[][] STONE_GRIDS = {
		{2683, 16930, 10544, 17, -1, 1},
		{2754, 16919, 10518, 11, 1, 0},
	};

	private static final int SUNKEN_TREASURE = 35033;
	private static final int INVENTORY = 93;
	private static final int LAP_FINISH_XP = 900;
	private static final int WHOLE_LAP_XP = 1500;
	private static final int MAX_XP_DROP = 5000;
	private static final int IDLE_TICKS = 300;
	// Old key held game ticks; the new one holds hundredths of a second.
	private static final String BEST_KEY = "bestLap";
	private static final String BEST_CS_KEY = "bestLapCs";

	enum Kind
	{
		OBSTACLE, FAST, SLOW, CHARGED, STONE, CHEST
	}

	static final class Tracked
	{
		final Kind kind;
		int rowVarbit;
		int column;
		boolean solidWhenPicked;
		boolean solid = true;

		Tracked(Kind kind)
		{
			this.kind = kind;
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

	int lastLapCs;
	boolean lastLapWasBest;
	int bestLapCs;

	private int gameLapTick = -1;
	private int ownFinishTick = -1;
	private int bestBeforeFinishCs;
	private int gameLapCs;
	private int gameBestCs;

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
		for (int id : FAST_GAPS)
		{
			kinds.put(id, Kind.FAST);
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
		lastLapCs = 0;
		lastLapWasBest = false;
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
		Tracked t = new Tracked(kind);
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
		Kind kind = kinds.get(e.getId());
		if (kind == null || kind == Kind.CHEST)
		{
			return;
		}
		LOG.debug("Raeun click: id={} kind={} option={} tick={}", e.getId(), kind, e.getMenuOption(), client.getTickCount());
		if (lapStartTick < 0)
		{
			startLap(client.getTickCount());
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		if (e.getIdentifier() == FINAL_JUMP && "Jump".equals(e.getOption()) && hideFinalJump())
		{
			client.getMenu().removeMenuEntry(e.getMenuEntry());
		}
	}

	// Only hide the jump when the chest can actually be looted, so a missed XP drop can't leave you stuck.
	private boolean hideFinalJump()
	{
		if (!config.hideJumpUntilLooted() || chestLooted || lapStartTick < 0
			|| client.getRealSkillLevel(Skill.THIEVING) < CHEST_THIEVING)
		{
			return false;
		}
		for (Map.Entry<TileObject, Tracked> en : objects.entrySet())
		{
			if (en.getValue().kind == Kind.CHEST && chestOpenable(en.getKey()))
			{
				return true;
			}
		}
		return false;
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
		// Our own count, in hundredths of a second. Replaced by the game's exact time when its message arrives.
		int cs = ticks * 60;
		bestBeforeFinishCs = bestLapCs;
		ownFinishTick = tick;
		lastLapCs = whole ? cs : 0;
		lastLapWasBest = false;
		if (whole && (bestLapCs <= 0 || cs < bestLapCs))
		{
			lastLapWasBest = bestLapCs > 0;
			saveBest(cs);
		}
		// If the game's lap message already arrived for this finish, it wins over our count.
		if (gameLapTick >= 0 && tick - gameLapTick <= 2)
		{
			applyGameLap();
		}
		lapStartTick = -1;
		lapXp = 0;
		chestLooted = false;
	}

	// The game posts the exact lap time and personal best when a lap ends, so prefer those over our own count.
	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if ((e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM) || !onCourse())
		{
			return;
		}
		String text = Text.removeTags(e.getMessage());
		Matcher duration = LAP_DURATION.matcher(text);
		if (!duration.find())
		{
			return;
		}
		gameLapCs = parseTime(duration.group(1));
		Matcher best = PERSONAL_BEST.matcher(text);
		gameBestCs = best.find() ? parseTime(best.group(1)) : 0;
		gameLapTick = client.getTickCount();
		applyGameLap();
	}

	private void applyGameLap()
	{
		if (gameLapCs <= 0)
		{
			return;
		}
		lastLapCs = gameLapCs;
		// Ignore a best that our own, less exact count set for this same finish.
		boolean ownCountRan = ownFinishTick >= 0 && Math.abs(client.getTickCount() - ownFinishTick) <= 2;
		int known = ownCountRan ? bestBeforeFinishCs : bestLapCs;
		int best;
		if (gameBestCs > 0)
		{
			// The game's personal best is the real one, even if it is older than this plugin.
			best = Math.min(gameBestCs, gameLapCs);
			lastLapWasBest = gameLapCs <= gameBestCs;
		}
		else
		{
			// No old best in the message: either a new personal best or the first lap ever.
			lastLapWasBest = known <= 0 || gameLapCs <= known;
			best = lastLapWasBest ? gameLapCs : known;
		}
		if (best != bestLapCs)
		{
			saveBest(best);
		}
	}

	// "3:55.20" or "1:02:03.40" to hundredths of a second.
	static int parseTime(String s)
	{
		try
		{
			String[] parts = s.replaceAll("\\.$", "").split(":");
			double seconds = 0;
			for (String part : parts)
			{
				seconds = seconds * 60 + Double.parseDouble(part);
			}
			return (int) Math.round(seconds * 100);
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	private void saveBest(int cs)
	{
		bestLapCs = cs;
		configManager.setRSProfileConfiguration(RaeunConfig.GROUP, BEST_CS_KEY, cs);
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

	static String formatCs(int cs)
	{
		int minutes = cs / 6000;
		int seconds = (cs / 100) % 60;
		int hundredths = cs % 100;
		return minutes + ":" + (seconds < 10 ? "0" : "") + seconds + "." + (hundredths < 10 ? "0" : "") + hundredths;
	}

	private void loadBest()
	{
		bestLapCs = savedInt(BEST_CS_KEY);
		if (bestLapCs <= 0)
		{
			bestLapCs = savedInt(BEST_KEY) * 60;
		}
	}

	private int savedInt(String key)
	{
		String saved = configManager.getRSProfileConfiguration(RaeunConfig.GROUP, key);
		if (saved == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(saved.trim());
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}
}
