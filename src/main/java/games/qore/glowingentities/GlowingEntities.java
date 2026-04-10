package games.qore.glowingentities;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.channel.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * A utility to easily make entities glow.
 * <p>
 * Requires Paper 26.1 or above.
 *
 * @author Originally by SkytAsul, modified by qore
 */
public class GlowingEntities implements Listener {

	protected final @NotNull Plugin plugin;
	private Map<Player, PlayerData> glowing;
	boolean enabled = false;
	private int uid;

	/**
	 * Initializes the Glowing API.
	 *
	 * @param plugin plugin that will be used to register the events.
	 */
	public GlowingEntities(@NotNull Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
		enable();
	}

	/**
	 * Enables the Glowing API.
	 *
	 * @see #disable()
	 */
	public void enable() {
		if (enabled)
			throw new IllegalStateException("The Glowing Entities API has already been enabled.");

		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		glowing = new HashMap<>();
		uid = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
		enabled = true;
	}

	/**
	 * Disables the API.
	 *
	 * @see #enable()
	 */
	public void disable() {
		if (!enabled)
			return;
		HandlerList.unregisterAll(this);
		glowing.values().forEach(Packets::removePacketsHandler);
		glowing = null;
		uid = 0;
		enabled = false;
	}

	private void ensureEnabled() {
		if (!enabled)
			throw new IllegalStateException("The Glowing Entities API is not enabled.");
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		glowing.remove(event.getPlayer());
	}

	/**
	 * Make the {@link Entity} passed as a parameter glow with its default team color.
	 *
	 * @param entity   entity to make glow
	 * @param receiver player which will see the entity glowing
	 */
	public void setGlowing(@NotNull Entity entity, @NotNull Player receiver) {
		setGlowing(entity, receiver, null);
	}

	/**
	 * Make the {@link Entity} passed as a parameter glow with the specified color.
	 *
	 * @param entity   entity to make glow
	 * @param receiver player which will see the entity glowing
	 * @param color    color of the glowing effect, or {@code null} for default
	 */
	public void setGlowing(@NotNull Entity entity, @NotNull Player receiver, @Nullable ChatColor color) {
		String teamID = entity instanceof Player ? entity.getName() : entity.getUniqueId().toString();
		setGlowing(entity.getEntityId(), teamID, receiver, color, Packets.getEntityFlags(entity));
	}

	/**
	 * Make the entity with specified entity ID glow with its default team color.
	 *
	 * @param entityID entity id of the entity to make glow
	 * @param teamID   internal string used to add the entity to a team
	 * @param receiver player which will see the entity glowing
	 */
	public void setGlowing(int entityID, @NotNull String teamID, @NotNull Player receiver) {
		setGlowing(entityID, teamID, receiver, null, (byte) 0);
	}

	/**
	 * Make the entity with specified entity ID glow with the specified color.
	 *
	 * @param entityID entity id of the entity to make glow
	 * @param teamID   internal string used to add the entity to a team
	 * @param receiver player which will see the entity glowing
	 * @param color    color of the glowing effect, or {@code null} for default
	 */
	public void setGlowing(int entityID, @NotNull String teamID, @NotNull Player receiver,
			@Nullable ChatColor color) {
		setGlowing(entityID, teamID, receiver, color, (byte) 0);
	}

	/**
	 * Make the entity with specified entity ID glow with the specified color, and keep some flags.
	 *
	 * @param entityID   entity id of the entity to make glow
	 * @param teamID     internal string used to add the entity to a team
	 * @param receiver   player which will see the entity glowing
	 * @param color      color of the glowing effect, or {@code null} for default
	 * @param otherFlags internal flags that must be kept (on fire, crouching...).
	 *                   See <a href="https://wiki.vg/Entity_metadata#Entity">wiki.vg</a>.
	 */
	public void setGlowing(int entityID, @NotNull String teamID, @NotNull Player receiver,
			@Nullable ChatColor color, byte otherFlags) {
		ensureEnabled();
		if (color != null && !color.isColor())
			throw new IllegalArgumentException("ChatColor must be a color format");

		PlayerData playerData = glowing.get(receiver);
		if (playerData == null) {
			playerData = new PlayerData(this, receiver);
			Packets.addPacketsHandler(playerData);
			glowing.put(receiver, playerData);
		}

		GlowingData glowingData = playerData.glowingDatas.get(entityID);
		if (glowingData == null) {
			glowingData = new GlowingData(playerData, entityID, teamID, color, otherFlags);
			playerData.glowingDatas.put(entityID, glowingData);

			Packets.createGlowing(glowingData);
			if (color != null)
				Packets.setGlowingColor(glowingData);
		} else {
			if (Objects.equals(glowingData.color, color))
				return;

			if (color == null) {
				Packets.removeGlowingColor(glowingData);
				glowingData.color = null;
			} else {
				glowingData.color = color;
				Packets.setGlowingColor(glowingData);
			}
		}
	}

	/**
	 * Make the {@link Entity} passed as a parameter lose its custom glowing effect.
	 * <p>
	 * This has <b>no effect</b> on glowing status given by another plugin or vanilla behavior.
	 *
	 * @param entity   entity to remove glowing effect from
	 * @param receiver player which will no longer see the glowing effect
	 */
	public void unsetGlowing(@NotNull Entity entity, @NotNull Player receiver) {
		unsetGlowing(entity.getEntityId(), receiver);
	}

	/**
	 * Make the entity with specified entity ID lose its custom glowing effect.
	 * <p>
	 * This has <b>no effect</b> on glowing status given by another plugin or vanilla behavior.
	 *
	 * @param entityID entity id of the entity to remove glowing effect from
	 * @param receiver player which will no longer see the glowing effect
	 */
	public void unsetGlowing(int entityID, @NotNull Player receiver) {
		ensureEnabled();
		PlayerData playerData = glowing.get(receiver);
		if (playerData == null)
			return;

		GlowingData glowingData = playerData.glowingDatas.remove(entityID);
		if (glowingData == null)
			return;

		Packets.removeGlowing(glowingData);

		if (glowingData.color != null)
			Packets.removeGlowingColor(glowingData);
	}

	/* ---- Internal data classes ---- */

	private static class PlayerData {
		final GlowingEntities instance;
		final Player player;
		final Map<Integer, GlowingData> glowingDatas;
		ChannelHandler packetsHandler;
		EnumSet<ChatColor> sentColors;

		PlayerData(GlowingEntities instance, Player player) {
			this.instance = instance;
			this.player = player;
			this.glowingDatas = new HashMap<>();
		}
	}

	private static class GlowingData {
		final PlayerData player;
		final int entityID;
		final String teamID;
		ChatColor color;
		byte otherFlags;
		boolean enabled;

		GlowingData(PlayerData player, int entityID, String teamID, ChatColor color, byte otherFlags) {
			this.player = player;
			this.entityID = entityID;
			this.teamID = teamID;
			this.color = color;
			this.otherFlags = otherFlags;
			this.enabled = true;
		}
	}

	/* ---- NMS Packets ---- */

	protected static class Packets {

		private static final byte GLOWING_FLAG = 1 << 6;

		private static final Cache<ClientboundSetEntityDataPacket, Object> sentPackets =
				CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();
		private static final Object SENTINEL = new Object();

		private static final Scoreboard DUMMY_SCOREBOARD = new Scoreboard();
		private static final EnumMap<ChatColor, TeamData> teams = new EnumMap<>(ChatColor.class);

		protected static final EntityType<?> shulkerEntityType = EntityType.SHULKER;

		@SuppressWarnings("unchecked")
		private static final EntityDataAccessor<Byte> SHARED_FLAGS_ACCESSOR;
		static {
			try {
				var field = net.minecraft.world.entity.Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
				field.setAccessible(true);
				SHARED_FLAGS_ACCESSOR = (EntityDataAccessor<Byte>) field.get(null);
			} catch (ReflectiveOperationException e) {
				throw new ExceptionInInitializerError(e);
			}
		}

		/* ---- Packet sending ---- */

		public static void sendPackets(@NotNull Player player, @NotNull Packet<?> @NotNull ... pkts) {
			var connection = ((CraftPlayer) player).getHandle().connection;
			for (Packet<?> packet : pkts) {
				if (packet != null)
					connection.send(packet);
			}
		}

		/* ---- Entity flags ---- */

		public static byte getEntityFlags(@NotNull Entity entity) {
			var nmsEntity = ((CraftEntity) entity).getHandle();
			return nmsEntity.getEntityData().get(SHARED_FLAGS_ACCESSOR);
		}

		private static byte computeFlags(GlowingData data) {
			byte flags = data.otherFlags;
			if (data.enabled)
				flags |= GLOWING_FLAG;
			else
				flags &= ~GLOWING_FLAG;
			return flags;
		}

		private static SynchedEntityData.DataValue<Byte> createFlagsDataValue(byte flags) {
			return SynchedEntityData.DataValue.create(
					SHARED_FLAGS_ACCESSOR, flags);
		}

		/* ---- Glowing state ---- */

		public static void createGlowing(GlowingData data) {
			setMetadata(data.player.player, data.entityID, computeFlags(data), true);
		}

		public static void removeGlowing(GlowingData data) {
			setMetadata(data.player.player, data.entityID, data.otherFlags, true);
		}

		public static void updateGlowingState(GlowingData data) {
			if (data.enabled)
				createGlowing(data);
			else
				removeGlowing(data);
		}

		public static void setMetadata(@NotNull Player player, int entityId, byte flags, boolean ignore) {
			List<SynchedEntityData.DataValue<?>> items = new ArrayList<>(1);
			items.add(createFlagsDataValue(flags));
			var packet = new ClientboundSetEntityDataPacket(entityId, items);
			if (ignore)
				sentPackets.put(packet, SENTINEL);
			sendPackets(player, packet);
		}

		/* ---- Team colors ---- */

		public static void setGlowingColor(GlowingData data) {
			boolean sendCreation = false;
			if (data.player.sentColors == null) {
				data.player.sentColors = EnumSet.of(data.color);
				sendCreation = true;
			} else if (data.player.sentColors.add(data.color)) {
				sendCreation = true;
			}

			TeamData team = teams.get(data.color);
			if (team == null) {
				team = new TeamData(data.player.instance.uid, data.color);
				teams.put(data.color, team);
			}

			ClientboundSetPlayerTeamPacket addPacket = team.getEntityAddPacket(data.teamID);
			if (sendCreation)
				sendPackets(data.player.player, team.creationPacket, addPacket);
			else
				sendPackets(data.player.player, addPacket);
		}

		public static void removeGlowingColor(GlowingData data) {
			TeamData team = teams.get(data.color);
			if (team != null)
				sendPackets(data.player.player, team.getEntityRemovePacket(data.teamID));
		}

		/* ---- Entity management ---- */

		public static void createEntity(@NotNull Player player, int entityId, @NotNull UUID entityUuid,
				@NotNull EntityType<?> entityType, @NotNull Location location) {
			var packet = new ClientboundAddEntityPacket(
					entityId, entityUuid,
					location.getX(), location.getY(), location.getZ(),
					location.getPitch(), location.getYaw(),
					entityType, 0, Vec3.ZERO, 0d);
			sendPackets(player, packet);
		}

		public static void removeEntities(@NotNull Player player, int... entityIds) {
			sendPackets(player, new ClientboundRemoveEntitiesPacket(entityIds));
		}

		/* ---- Channel packet handler ---- */

		private static Channel getChannel(@NotNull Player player) {
			ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
			return nmsPlayer.connection.connection.channel;
		}

		public static void addPacketsHandler(PlayerData playerData) {
			playerData.packetsHandler = new ChannelDuplexHandler() {

				@Override
				public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
					if (msg instanceof ClientboundSetEntityDataPacket metadataPacket
							&& sentPackets.asMap().remove(metadataPacket) == null) {
						if (interceptMetadataPacket(metadataPacket))
							return; // replaced — do not forward original
					} else if (msg instanceof BundlePacket<?> bundlePacket) {
						interceptBundlePacket(bundlePacket);
					}
					super.write(ctx, msg, promise);
				}

				private boolean interceptMetadataPacket(ClientboundSetEntityDataPacket packet) {
					int entityID = packet.id();
					GlowingData glowingData = playerData.glowingDatas.get(entityID);
					if (glowingData == null)
						return false;

					List<SynchedEntityData.DataValue<?>> items = packet.packedItems();
					if (items == null)
						return false;

					boolean containsFlags = false;
					boolean edited = false;

					EntityDataAccessor<Byte> sharedFlagsAccessor = SHARED_FLAGS_ACCESSOR;

					for (int i = 0; i < items.size(); i++) {
						SynchedEntityData.DataValue<?> item = items.get(i);
						var accessor = item.serializer().createAccessor(item.id());

						if (accessor.equals(sharedFlagsAccessor)) {
							containsFlags = true;
							byte flags = (Byte) item.value();
							glowingData.otherFlags = flags;
							byte newFlags = computeFlags(glowingData);
							if (newFlags != flags) {
								edited = true;
								items = new ArrayList<>(items);
								items.set(i, createFlagsDataValue(newFlags));
								break;
							}
						}
					}

					if (!edited && !containsFlags) {
						byte flags = computeFlags(glowingData);
						if (flags != 0) {
							edited = true;
							items = new ArrayList<>(items);
							items.add(createFlagsDataValue(flags));
						}
					}

					if (edited) {
						var newPacket = new ClientboundSetEntityDataPacket(entityID, items);
						sentPackets.put(newPacket, SENTINEL);
						sendPackets(playerData.player, newPacket);
						return true;
					}
					return false;
				}

				private void interceptBundlePacket(BundlePacket<?> bundle) {
					for (Packet<?> packet : bundle.subPackets()) {
						if (packet instanceof ClientboundSetEntityDataPacket metadataPacket) {
							int entityID = metadataPacket.id();
							GlowingData glowingData = playerData.glowingDatas.get(entityID);
							if (glowingData != null) {
								Bukkit.getScheduler().runTaskLaterAsynchronously(
										playerData.instance.plugin,
										() -> updateGlowingState(glowingData), 1L);
								return;
							}
						}
					}
				}
			};

			getChannel(playerData.player).pipeline().addBefore("packet_handler", null, playerData.packetsHandler);
		}

		public static void removePacketsHandler(PlayerData playerData) {
			if (playerData.packetsHandler != null)
				getChannel(playerData.player).pipeline().remove(playerData.packetsHandler);
		}

		/* ---- Team data ---- */

		private static class TeamData {

			private final PlayerTeam team;
			private final ClientboundSetPlayerTeamPacket creationPacket;

			private final Cache<String, ClientboundSetPlayerTeamPacket> addPackets =
					CacheBuilder.newBuilder().expireAfterAccess(3, TimeUnit.MINUTES).build();
			private final Cache<String, ClientboundSetPlayerTeamPacket> removePackets =
					CacheBuilder.newBuilder().expireAfterAccess(3, TimeUnit.MINUTES).build();

			TeamData(int uid, ChatColor color) {
				if (!color.isColor())
					throw new IllegalArgumentException();
				String id = "glow-" + uid + color.getChar();
				team = new PlayerTeam(DUMMY_SCOREBOARD, id);
				team.setCollisionRule(Team.CollisionRule.NEVER);
				team.setColor(Objects.requireNonNull(ChatFormatting.getByCode(color.getChar())));
				creationPacket = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
			}

			ClientboundSetPlayerTeamPacket getEntityAddPacket(String teamID) {
				ClientboundSetPlayerTeamPacket packet = addPackets.getIfPresent(teamID);
				if (packet == null) {
					packet = ClientboundSetPlayerTeamPacket.createPlayerPacket(
							team, teamID, ClientboundSetPlayerTeamPacket.Action.ADD);
					addPackets.put(teamID, packet);
				}
				return packet;
			}

			ClientboundSetPlayerTeamPacket getEntityRemovePacket(String teamID) {
				ClientboundSetPlayerTeamPacket packet = removePackets.getIfPresent(teamID);
				if (packet == null) {
					packet = ClientboundSetPlayerTeamPacket.createPlayerPacket(
							team, teamID, ClientboundSetPlayerTeamPacket.Action.REMOVE);
					removePackets.put(teamID, packet);
				}
				return packet;
			}

		}

	}

}


