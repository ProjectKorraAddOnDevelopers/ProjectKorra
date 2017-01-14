package com.projectkorra.projectkorra.earthbending.passive;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_10_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.server.v1_10_R1.DataWatcher;
import net.minecraft.server.v1_10_R1.DataWatcher.Item;
import net.minecraft.server.v1_10_R1.PacketPlayOutEntityMetadata;

public class Tremorsense extends EarthAbility implements PassiveAbility {

	public static final Map<UUID, Map<LivingEntity, Double>> ENTITIES = new ConcurrentHashMap<>();

	// Configurable variables
	private double darknessRange;
	private double blindnessRange;
	private byte lightThreshold;
	private boolean detectPlayersOnly;

	// Instance related variables
	private Block block;

	public Tremorsense(Player player) {
		super(player);
		setFields();
	}

	public void setFields() {
		this.darknessRange = ConfigManager.getConfig().getDouble("Abilities.Earth.Passive.Tremorsense.DarknessRange");
		this.blindnessRange = ConfigManager.getConfig().getDouble("Abilities.Earth.Passive.Tremorsense.BlindnessRange");
		this.lightThreshold = (byte) ConfigManager.getConfig().getInt("Abilities.Earth.Passive.Tremorsense.LightThreshold");
		this.detectPlayersOnly = ConfigManager.getConfig().getBoolean("Abilities.Earth.Passive.Tremorsense.DetectPlayersOnly");
	}

	@Override
	public void progress() {
		byte lightLevel = player.getLocation().getBlock().getLightLevel();
		if (lightLevel < lightThreshold || player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
			if (isEarthbendable(player.getLocation().clone().add(0, -1, 0).getBlock())) {
				double range = player.hasPotionEffect(PotionEffectType.BLINDNESS) ? blindnessRange : darknessRange;
				for (Entity entity : GeneralMethods.getEntitiesAroundPoint(player.getLocation(), range)) {
					if (entity instanceof LivingEntity) {
						if (detectPlayersOnly && !(entity instanceof Player)) {
							continue;
						} else if (entity.getUniqueId() == player.getUniqueId()) {
							continue;
						}
						LivingEntity le = (LivingEntity) entity;
						if (!ENTITIES.containsKey(player.getUniqueId())) {
							ENTITIES.put(player.getUniqueId(), new ConcurrentHashMap<>());
						}
						ENTITIES.get(player.getUniqueId()).put(le, range);
						le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false));
					}
				}
			}
			setGlowBlock();
		}

	}

	public static void updateGlow() {
		for (UUID uuid : ENTITIES.keySet()) {
			Player player = ProjectKorra.plugin.getServer().getPlayer(uuid);
			for (LivingEntity le : ENTITIES.get(uuid).keySet()) {
				/*
				 * CHECK THAT THE PLAYER IS ONLINE
				 * CHECK THAT THE PLAYER IS IN THE SAME WORLD AS THE ENTITY
				 */
				if (player.getLocation().distance(le.getLocation()) > ENTITIES.get(uuid).get(le) || !EarthAbility.isEarth(player.getLocation().clone().add(0, -1, 0).getBlock())) {
					ENTITIES.get(uuid).remove(le);
					le.removePotionEffect(PotionEffectType.GLOWING);
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void setGlowBlock() {
		Block standBlock = player.getLocation().clone().add(0, -1, 0).getBlock();

		if (isEarthbendable(standBlock) && block == null) {
			block = standBlock;
			player.sendBlockChange(block.getLocation(), 169, (byte) 1);
		} else if (isEarthbendable(standBlock) && !block.equals(standBlock)) {
			revertGlowBlock();
			block = standBlock;
			player.sendBlockChange(block.getLocation(), 169, (byte) 1);
		} else if (block == null) {
			return;
		} else if (!player.getWorld().equals(block.getWorld())) {
			remove();
			return;
		} else if (!isEarthbendable(standBlock)) {
			revertGlowBlock();
			return;
		}
	}

	@SuppressWarnings("deprecation")
	public void revertGlowBlock() {
		if (block != null) {
			player.sendBlockChange(block.getLocation(), block.getTypeId(), block.getData());
			block = null;
		}
	}

	@SuppressWarnings("unchecked")
	private static List<DataWatcher.Item<?>> get(PacketPlayOutEntityMetadata packet) {
		try {
			Field field = packet.getClass().getDeclaredField("b");
			field.setAccessible(true);
			List<DataWatcher.Item<?>> items = (List<DataWatcher.Item<?>>) field.get(packet);
			return items;
		}
		catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static void setByte(DataWatcher.Item<?> item, byte value) {
		try {
			Field field = item.getClass().getDeclaredField("b");
			field.setAccessible(true);
			field.set(item, value);
		}
		catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	private static int getId(PacketPlayOutEntityMetadata packet) {
		try {
			Field field = packet.getClass().getDeclaredField("a");
			field.setAccessible(true);
			return (int) field.get(packet);
		}
		catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return -1;
	}

	public static void create(Player player) {
		ChannelDuplexHandler channelDuplexHandler = new ChannelDuplexHandler() {
			@Override
			public void channelRead(ChannelHandlerContext context, Object packet) throws Exception {
				super.channelRead(context, packet);
			}

			@Override
			public void write(ChannelHandlerContext context, Object packet, ChannelPromise channelPromise) throws Exception { // Listen to packets being written
				if (packet instanceof PacketPlayOutEntityMetadata) { // If the packet is PacketPlayOutEntityMetadata (the packet with glow) then do stuff
					PacketPlayOutEntityMetadata metadata = (PacketPlayOutEntityMetadata) packet;
					DataWatcher.Item<?> item = (Item<?>) get(metadata).get(0);
					try {
						byte value = Byte.parseByte(item.b().toString());
						if (value >= 64) {

							for (UUID uuid : ENTITIES.keySet()) { // Loop through all players actively using tremorsense
								Player user = ProjectKorra.plugin.getServer().getPlayer(uuid);
								for (LivingEntity livingEntity : ENTITIES.get(uuid).keySet()) { // Loop through all the entities currently visible for that user
									if (livingEntity.getEntityId() != getId(metadata)) { // If the entity being checked is not the entity with the glow packet, then stop.
										continue;
									} else if (user.getUniqueId() == player.getUniqueId()) { // If the tremorsense user is the player receiving the packet, then stop.
										continue;
									} else if (ENTITIES.containsKey(player.getUniqueId()) && ENTITIES.get(player.getUniqueId()).containsKey(livingEntity)) { // If the player receiving the packet is actively using tremorsense, and they have the entity in their map, then stop.
										continue;
									} else {
										setByte(item, (byte) (value - 64));
									}
								}
							}

						}
					}
					catch (Exception e) {

					}
				}
				super.write(context, packet, channelPromise);
			}
		};

		ChannelPipeline pipeline = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel.pipeline();
		pipeline.addBefore("packet_handler", player.getName(), channelDuplexHandler);
	}

	public static void remove(Player player) {
		Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
		channel.eventLoop().submit(() -> {
			channel.pipeline().remove(player.getName());
			return null;
		});
	}

	@Override
	public void remove() {
		super.remove();
		revertGlowBlock();
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public boolean isHarmlessAbility() {
		return true;
	}

	@Override
	public long getCooldown() {
		return 0;
	}

	@Override
	public String getName() {
		return "NotTremorsense";
	}

	@Override
	public String getDescription() {
		return "TEST";
	}

	@Override
	public Location getLocation() {
		return null;
	}

	@Override
	public boolean isInstantiable() {
		return true;
	}

}
