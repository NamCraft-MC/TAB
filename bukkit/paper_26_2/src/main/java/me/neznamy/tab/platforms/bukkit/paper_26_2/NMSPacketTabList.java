package me.neznamy.tab.platforms.bukkit.paper_26_2;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import lombok.NonNull;
import me.neznamy.tab.platforms.bukkit.NamServerCoreSkinCache;
import me.neznamy.tab.platforms.bukkit.BukkitTabPlayer;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.chat.component.TabComponent;
import me.neznamy.tab.shared.platform.decorators.TrackedTabList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.world.level.GameType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TabList implementation using direct mojang-mapped code.
 */
public class NMSPacketTabList extends TrackedTabList<BukkitTabPlayer> {

    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> addPlayer = EnumSet.allOf(ClientboundPlayerInfoUpdatePacket.Action.class);
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> updateDisplayName = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> updateLatency = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY);
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> updateGameMode = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> updateListed = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED);
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> updateListOrder = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> updateHat = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT);
    private final Map<UUID, UUID> fakeEntries = new HashMap<>();
    private final NamServerCoreSkinCache namServerCoreSkinCache = new NamServerCoreSkinCache();

    /**
     * Constructs new instance.
     *
     * @param   player
     *          Player this tablist will belong to
     */
    public NMSPacketTabList(@NotNull BukkitTabPlayer player) {
        super(player);
    }

    @Override
    public void removeEntry(@NonNull UUID entry) {
        sendPacket(new ClientboundPlayerInfoRemovePacket(removeIds(entry)));
    }

    @Override
    public void updateDisplayName0(@NonNull UUID entry, @Nullable TabComponent displayName) {
        sendPacket(updateDisplayName, entry, "", null, false, 0, 0, displayName, 0, false);
    }

    @Override
    public void updateLatency(@NonNull UUID entry, int latency) {
        sendPacket(updateLatency, entry, "", null, false, latency, 0, null, 0, false);
    }

    @Override
    public void updateGameMode(@NonNull UUID entry, int gameMode) {
        sendPacket(updateGameMode, entry, "", null, false, 0, gameMode, null, 0, false);
    }

    @Override
    public void updateListed(@NonNull UUID entry, boolean listed) {
        sendPacket(updateListed, entry, "", null, listed, 0, 0, null, 0, false);
    }

    @Override
    public void updateListOrder(@NonNull UUID entry, int listOrder) {
        sendPacket(updateListOrder, entry, "", null, false, 0, 0, null, listOrder, false);
    }

    @Override
    public void updateHat(@NonNull UUID entry, boolean showHat) {
        sendPacket(updateHat, entry, "", null, false, 0, 0, null, 0, nativeHeadVisible(showHat));
    }

    @Override
    public void addEntry0(@NonNull Entry entry) {
        sendPacket(addPlayer, entry.getUniqueId(), entry.getName(), entry.getSkin(), entry.isListed(), entry.getLatency(),
                entry.getGameMode(), entry.getDisplayName(), entry.getListOrder(), nativeHeadVisible(entry.isShowHat()));
    }

    @Override
    public void setPlayerListHeaderFooter0(@NonNull TabComponent header, @NonNull TabComponent footer) {
        sendPacket(new ClientboundTabListPacket(header.convert(), footer.convert()));
    }

    @Override
    @Nullable
    public Skin getSkin() {
        Collection<Property> properties = ((CraftPlayer)player.getPlayer()).getProfile().properties().get(TEXTURES_PROPERTY);
        if (properties.isEmpty()) return null; // Offline mode
        Property property = properties.iterator().next();
        return new Skin(property.value(), property.signature());
    }

    @Override
    @NotNull
    public Object onPacketSend(@NonNull Object packet) {
        if (packet instanceof ClientboundTabListPacket tablist) {
            if (header == null || footer == null) return packet;
            if (tablist.header() != header.convert() || tablist.footer() != footer.convert()) {
                return new ClientboundTabListPacket(header.convert(), footer.convert());
            }
        }
        if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
            if (fakePlayerlistEntries()) {
                List<UUID> ids = new ArrayList<>();
                boolean rewritePacket = false;
                for (UUID id : remove.profileIds()) {
                    ids.add(id);
                    UUID fake = fakeEntries.remove(id);
                    if (fake != null) {
                        ids.add(fake);
                        rewritePacket = true;
                    }
                }
                if (rewritePacket) return new ClientboundPlayerInfoRemovePacket(ids);
            }
        }
        if (packet instanceof ClientboundPlayerInfoUpdatePacket info) {
            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = info.actions();
            List<ClientboundPlayerInfoUpdatePacket.Entry> updatedList = new ArrayList<>();
            boolean rewritePacket = false;
            for (ClientboundPlayerInfoUpdatePacket.Entry nmsData : info.entries()) {
                boolean rewriteEntry = false;
                Component displayName = nmsData.displayName();
                int latency = nmsData.latency();
                int gameMode = nmsData.gameMode().getId();
                boolean listed = nmsData.listed();
                boolean showHat = nmsData.showHat();
                if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) {
                    TabComponent forcedDisplayName = getForcedDisplayNames().get(nmsData.profileId());
                    if (forcedDisplayName != null && forcedDisplayName.convert() != displayName) {
                        displayName = forcedDisplayName.convert();
                        rewriteEntry = rewritePacket = true;
                    }
                }
                if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) {
                    if (getBlockedSpectators().contains(nmsData.profileId()) && gameMode == 3) {
                        gameMode = 0;
                        rewriteEntry = rewritePacket = true;
                    }
                }
                if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY)) {
                    if (getForcedLatency() != null) {
                        latency = getForcedLatency();
                        rewriteEntry = rewritePacket = true;
                    }
                }
                if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED)) {
                    if (allPlayersHidden && nmsData.profileId().getMostSignificantBits() != 0) { // Filter out layout entries
                        listed = false;
                        rewriteEntry = rewritePacket = true;
                    }
                }
                if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                    TAB.getInstance().getFeatureManager().onEntryAdd(player, nmsData.profileId(), nmsData.profile().name());
                }
                if (hideNativePlayerlistHeads()
                        && (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
                        || actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT))
                        && showHat) {
                    showHat = false;
                    rewriteEntry = rewritePacket = true;
                }
                if (shouldReplaceWithFakeEntry(nmsData.profileId())) {
                    addRealAndFakeEntries(updatedList, actions, nmsData.profileId(), nmsData.profile(), listed, latency,
                            gameMode, displayName, showHat, nmsData.listOrder(), nmsData.chatSession());
                    rewritePacket = true;
                    continue;
                }
                updatedList.add(rewriteEntry ? new ClientboundPlayerInfoUpdatePacket.Entry(
                        nmsData.profileId(), nmsData.profile(), listed, latency, GameType.byId(gameMode), displayName,
                        showHat, nmsData.listOrder(), nmsData.chatSession()
                ) : nmsData);
            }
            if (rewritePacket) return new ClientboundPlayerInfoUpdatePacket(actions, updatedList);
        }
        return packet;
    }

    private void sendPacket(@NonNull EnumSet<ClientboundPlayerInfoUpdatePacket.Action> action, @NonNull UUID id, @NonNull String name, @Nullable Skin skin,
                            boolean listed, int latency, int gameMode, @Nullable TabComponent displayName, int listOrder, boolean showHat) {
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>();
        if (shouldReplaceWithFakeEntry(id)) {
            addRealAndFakeEntries(entries, action, id, action.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER) ? createProfile(id, name, skin) : null,
                    listed, latency, gameMode, displayName == null ? null : displayName.convert(), showHat, listOrder, null);
        } else {
            entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                    id,
                    action.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER) ? createProfile(id, name, skin) : null,
                    listed,
                    latency,
                    GameType.byId(gameMode),
                    displayName == null ? null : displayName.convert(),
                    showHat,
                    listOrder,
                    null
            ));
        }
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(action, entries);
        sendPacket(packet);
    }

    /**
     * Creates GameProfile from given parameters.
     *
     * @param   id
     *          Profile ID
     * @param   name
     *          Profile name
     * @param   skin
     *          Player skin
     * @return  GameProfile from given parameters
     */
    @NotNull
    private GameProfile createProfile(@NonNull UUID id, @NonNull String name, @Nullable Skin skin) {
        ImmutableMultimap.Builder<String, Property> builder = ImmutableMultimap.builder();
        if (skin != null) {
            builder.put(TEXTURES_PROPERTY, new Property(TEXTURES_PROPERTY, skin.getValue(), skin.getSignature()));
        }
        return new GameProfile(id, name, new PropertyMap(builder.build()));
    }

    private void addRealAndFakeEntries(@NonNull List<ClientboundPlayerInfoUpdatePacket.Entry> list,
                                       @NonNull EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions,
                                       @NonNull UUID realId,
                                       @Nullable GameProfile realProfile,
                                       boolean listed,
                                       int latency,
                                       int gameMode,
                                       @Nullable Component displayName,
                                       boolean showHat,
                                       int listOrder,
                                       @Nullable net.minecraft.network.chat.RemoteChatSession.Data chatSession) {
        boolean updatesListed = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED)
                || actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
        UUID fakeId = fakeEntryId(realId);
        list.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                realId,
                realProfile,
                updatesListed ? false : listed,
                latency,
                GameType.byId(gameMode),
                displayName,
                showHat,
                listOrder,
                chatSession
        ));
        list.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                fakeId,
                actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
                        ? createProfile(fakeId, fakeEntryName(fakeId), fakeEntrySkin(realId, realProfile))
                        : null,
                listed,
                latency,
                GameType.byId(gameMode),
                displayName,
                showHat,
                listOrder,
                null
        ));
    }

    private boolean shouldReplaceWithFakeEntry(@NonNull UUID id) {
        return fakePlayerlistEntries() && (fakeEntries.containsKey(id) || TAB.getInstance().getPlayerByTabListUUID(id) != null);
    }

    private List<UUID> removeIds(@NonNull UUID id) {
        UUID fake = fakeEntries.remove(id);
        if (fakePlayerlistEntries() && fake != null) {
            return Arrays.asList(id, fake);
        }
        return Collections.singletonList(id);
    }

    @NonNull
    private UUID fakeEntryId(@NonNull UUID realId) {
        return fakeEntries.computeIfAbsent(realId, id -> UUID.nameUUIDFromBytes(("namcraft-tab-fake:" + id).getBytes(StandardCharsets.UTF_8)));
    }

    @NonNull
    private String fakeEntryName(@NonNull UUID fakeId) {
        return "nt" + fakeId.toString().replace("-", "").substring(0, 14);
    }

    @Nullable
    private Skin fakeEntrySkin(@NonNull UUID realId, @Nullable GameProfile realProfile) {
        Skin profileSkin = realProfile == null ? null : skinFromProfile(realProfile);
        if (profileSkin != null) return profileSkin;
        if (!useNamServerCoreSkins()) return null;
        String name = realProfile == null ? "" : realProfile.name();
        return namServerCoreSkinCache.getSkin(realId, name);
    }

    @Nullable
    private Skin skinFromProfile(@NonNull GameProfile profile) {
        Collection<Property> properties = profile.properties().get(TEXTURES_PROPERTY);
        if (properties.isEmpty()) return null;
        Property property = properties.iterator().next();
        return new Skin(property.value(), property.signature());
    }

    /**
     * Sends the packet to the player.
     *
     * @param   packet
     *          Packet to send
     */
    private void sendPacket(@NotNull Packet<?> packet) {
        ((CraftPlayer)player.getPlayer()).getHandle().connection.send(packet);
    }

    private boolean nativeHeadVisible(boolean requested) {
        return requested && !hideNativePlayerlistHeads();
    }

    private boolean hideNativePlayerlistHeads() {
        return TAB.getInstance().getConfiguration().getConfig().getComponents().isDisableNativePlayerlistHeads();
    }

    private boolean fakePlayerlistEntries() {
        return TAB.getInstance().getConfiguration().getConfig().getComponents().isFakePlayerlistEntries();
    }

    private boolean useNamServerCoreSkins() {
        return TAB.getInstance().getConfiguration().getConfig().getComponents().isFakePlayerlistUseNamServerCoreSkins();
    }
}
