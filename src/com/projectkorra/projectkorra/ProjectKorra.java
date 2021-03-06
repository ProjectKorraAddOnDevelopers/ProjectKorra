package com.projectkorra.projectkorra;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.util.CollisionInitializer;
import com.projectkorra.projectkorra.ability.util.CollisionManager;
import com.projectkorra.projectkorra.ability.util.ComboManager;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.ability.util.PassiveManager;
import com.projectkorra.projectkorra.airbending.util.AirbendingManager;
import com.projectkorra.projectkorra.chiblocking.util.ChiblockingManager;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.earthbending.passive.Tremorsense;
import com.projectkorra.projectkorra.earthbending.util.EarthbendingManager;
import com.projectkorra.projectkorra.firebending.util.FirebendingManager;
import com.projectkorra.projectkorra.object.Preset;
import com.projectkorra.projectkorra.storage.DBConnection;
import com.projectkorra.projectkorra.util.MetricsLite;
import com.projectkorra.projectkorra.util.RevertChecker;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.util.Updater;
import com.projectkorra.projectkorra.util.logging.PKLogHandler;
import com.projectkorra.projectkorra.waterbending.util.WaterbendingManager;

public class ProjectKorra extends JavaPlugin {

	public static ProjectKorra plugin;
	public static Logger log;
	public static PKLogHandler handler;
	public static CollisionManager collisionManager;
	public static CollisionInitializer collisionInitializer;
	public static long time_step = 1;
	public Updater updater;

	@Override
	public void onEnable() {
		plugin = this;
		ProjectKorra.log = this.getLogger();
		try {
			File logFolder = new File(getDataFolder(), "Logs");
			if (!logFolder.exists()) {
				logFolder.mkdirs();
			}
			handler = new PKLogHandler(logFolder + File.separator + "ERROR.%g.log");
			log.getParent().addHandler(handler);
		}
		catch (SecurityException | IOException e) {
			e.printStackTrace();
		}

		new ConfigManager();
		new GeneralMethods(this);
		updater = new Updater(this, "http://projectkorra.com/forums/dev-builds.16/index.rss");
		new Commands(this);
		new MultiAbilityManager();
		new ComboManager();
		collisionManager = new CollisionManager();
		collisionInitializer = new CollisionInitializer(collisionManager);
		CoreAbility.registerAbilities();
		collisionInitializer.initializeDefaultCollisions(); // must be called after abilities have been registered
		collisionManager.startCollisionDetection();

		Preset.loadExternalPresets();

		DBConnection.host = getConfig().getString("Storage.MySQL.host");
		DBConnection.port = getConfig().getInt("Storage.MySQL.port");
		DBConnection.pass = getConfig().getString("Storage.MySQL.pass");
		DBConnection.db = getConfig().getString("Storage.MySQL.db");
		DBConnection.user = getConfig().getString("Storage.MySQL.user");
		DBConnection.init();
		if (DBConnection.isOpen() == false) {
			//Message is logged by DBConnection
			return;
		}

		getServer().getPluginManager().registerEvents(new PKListener(this), this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new BendingManager(), 0, 1);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new AirbendingManager(this), 0, 1);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new WaterbendingManager(this), 0, 1);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new EarthbendingManager(this), 0, 1);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new FirebendingManager(this), 0, 1);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new ChiblockingManager(this), 0, 1);
		//getServer().getScheduler().scheduleSyncRepeatingTask(this, new PassiveHandler(), 0, 1);
		getServer().getScheduler().runTaskTimerAsynchronously(this, new RevertChecker(this), 0, 200);
		TempBlock.startReversion();

		for (final Player player : Bukkit.getOnlinePlayers()) {
			PKListener.getJumpStatistics().put(player, player.getStatistic(Statistic.JUMP));

			GeneralMethods.createBendingPlayer(player.getUniqueId(), player.getName());
			GeneralMethods.removeUnusableAbilities(player.getName());
			Tremorsense.create(player);
			Bukkit.getScheduler().runTaskLater(ProjectKorra.plugin, new Runnable() {
				@Override
				public void run() {
					PassiveManager.registerPassives(player);
					GeneralMethods.removeUnusableAbilities(player.getName());
				}
			}, 5);
		}

		try {
			MetricsLite metrics = new MetricsLite(this);
			metrics.start();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		double cacheTime = ConfigManager.getConfig().getDouble("Properties.RegionProtection.CacheBlockTime");
		if (Bukkit.getPluginManager().getPlugin("Residence") != null)
			FlagPermissions.addFlag(ConfigManager.defaultConfig.get().getString("Properties.RegionProtection.Residence.Flag"));
		GeneralMethods.deserializeFile();
		GeneralMethods.startCacheCleaner(cacheTime);
		updater.checkUpdate();
	}

	@Override
	public void onDisable() {
		GeneralMethods.stopBending();
		for (Player player : Bukkit.getOnlinePlayers()) {
			Tremorsense.remove(player);
		}
		if (DBConnection.isOpen != false) {
			DBConnection.sql.close();
		}
		handler.close();
	}

	public static CollisionManager getCollisionManager() {
		return collisionManager;
	}

	public static void setCollisionManager(CollisionManager collisionManager) {
		ProjectKorra.collisionManager = collisionManager;
	}

	public static CollisionInitializer getCollisionInitializer() {
		return collisionInitializer;
	}

	public static void setCollisionInitializer(CollisionInitializer collisionInitializer) {
		ProjectKorra.collisionInitializer = collisionInitializer;
	}

}
