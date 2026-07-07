package me.neznamy.tab.shared.features.nametags;

import lombok.Getter;
import lombok.NonNull;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.cpu.ThreadExecutor;
import me.neznamy.tab.shared.cpu.TimedCaughtTask;
import me.neznamy.tab.shared.features.types.CustomThreaded;
import me.neznamy.tab.shared.features.types.JoinListener;
import me.neznamy.tab.shared.features.types.Loadable;
import me.neznamy.tab.shared.features.types.RefreshableFeature;
import me.neznamy.tab.shared.placeholders.conditions.Condition;
import me.neznamy.tab.shared.platform.Scoreboard;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Sub-feature for NameTag feature that manages nametag visibility.
 */
public class VisibilityManager extends RefreshableFeature implements JoinListener, Loadable, CustomThreaded {

    /** The main feature */
    @NotNull private final NameTag nameTags;

    /** Configured condition for invisible nametags */
    @Getter
    @NotNull
    private final Condition invisibleCondition;

    /**
     * Constructs new instance.
     *
     * @param   nameTags
     *          Parent feature
     */
    public VisibilityManager(@NotNull NameTag nameTags) {
        this.nameTags = nameTags;
        invisibleCondition = TAB.getInstance().getPlaceholderManager().getConditionManager().getByNameOrExpression(nameTags.getConfiguration().getInvisibleNameTags());
    }

    @Override
    public void load() {
        TAB.getInstance().getPlaceholderManager().registerPlayerPlaceholder(TabConstants.Placeholder.INVISIBLE, p -> {
            TabPlayer player = (TabPlayer) p;
            boolean newInvisibility = invisibleCondition.isMet((TabPlayer) p);
            if (newInvisibility) {
                player.teamData.hideNametag(NameTagInvisibilityReason.MEETING_CONFIGURED_CONDITION);
            } else {
                player.teamData.showNametag(NameTagInvisibilityReason.MEETING_CONFIGURED_CONDITION);
            }
            if (player.hasInvisibilityPotion()) {
                newInvisibility = true;
            }
            return Boolean.toString(newInvisibility);
        });
        addUsedPlaceholder(TabConstants.Placeholder.INVISIBLE);
        for (TabPlayer all : nameTags.getOnlinePlayers().getPlayers()) {
            onJoin(all);
        }
        getCustomThread().repeatTask(new TimedCaughtTask(TAB.getInstance().getCpu(),
                this::requestSolidOcclusionRefresh, getFeatureName(), "Updating solid nametag occlusion"), 250);
    }

    @Override
    public void onJoin(@NotNull TabPlayer connectedPlayer) {
        if (invisibleCondition.isMet(connectedPlayer)) {
            connectedPlayer.teamData.hideNametag(NameTagInvisibilityReason.MEETING_CONFIGURED_CONDITION);
            updateVisibility(connectedPlayer);
        }
    }

    @NotNull
    @Override
    public String getRefreshDisplayName() {
        return "Updating NameTag visibility";
    }

    @Override
    public void refresh(@NotNull TabPlayer p, boolean force) {
        if (p.teamData.isDisabled()) return;
        if (!nameTags.getOnlinePlayers().contains(p)) return; // player is not loaded by this feature yet
        updateVisibility(p);
    }

    @Override
    @NotNull
    public ThreadExecutor getCustomThread() {
        return nameTags.getCustomThread();
    }

    @NotNull
    @Override
    public String getFeatureName() {
        return nameTags.getFeatureName();
    }

    /**
     * Updates visibility of a player for everyone.
     *
     * @param   player
     *          Player to update visibility of
     */
    public void updateVisibility(@NonNull TabPlayer player) {
        for (TabPlayer viewer : nameTags.getOnlinePlayers().getPlayers()) {
            if (viewer.teamData.hasTeamRegistered(player)) {
                viewer.getScoreboard().updateTeam(
                        player.teamData.teamName,
                        player.teamData.getTeamVisibility(viewer) ? Scoreboard.NameVisibility.ALWAYS : Scoreboard.NameVisibility.NEVER
                );
            }
        }
        nameTags.getProxyHandler().sendProxyMessage(player);
    }

    /**
     * Updates visibility of a player for specified player.
     *
     * @param   player
     *          Player to update visibility of
     * @param   viewer
     *          Viewer to send update to
     */
    public void updateVisibility(@NonNull TabPlayer player, @NonNull TabPlayer viewer) {
        if (viewer.teamData.hasTeamRegistered(player)) {
            viewer.getScoreboard().updateTeam(
                    player.teamData.teamName,
                    player.teamData.getTeamVisibility(viewer) ? Scoreboard.NameVisibility.ALWAYS : Scoreboard.NameVisibility.NEVER
            );
        }
    }

    public void hideNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                            boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            clearSolidNameTagMode(player, null);
            if (player.teamData.hideNametag(reason)) {
                updateVisibility(player);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
        }, getFeatureName(), cpuReason));
    }

    public void hideNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                            @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            clearSolidNameTagMode(player, viewer);
            if (player.teamData.hideNametag(viewer, reason)) {
                updateVisibility(player, viewer);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
        }, getFeatureName(), cpuReason));
    }

    public void showNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                            boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            clearSolidNameTagMode(player, null);
            if (player.teamData.showNametag(reason)) {
                updateVisibility(player);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
        }, getFeatureName(), cpuReason));
    }

    public void showNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                            @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            clearSolidNameTagMode(player, viewer);
            if (player.teamData.showNametag(viewer, reason)) {
                updateVisibility(player, viewer);
            }
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
        }, getFeatureName(), cpuReason));
    }

    public void toggleNameTag(@NonNull TabPlayer player, @NonNull NameTagInvisibilityReason reason, @NonNull String cpuReason,
                              boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            clearSolidNameTagMode(player, null);
            if (player.teamData.hasHiddenNametag(reason)) {
                player.teamData.showNametag(reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
            } else {
                player.teamData.hideNametag(reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
            }
            updateVisibility(player);
        }, getFeatureName(), cpuReason));
    }

    public void toggleNameTag(@NonNull TabPlayer player, @NonNull TabPlayer viewer, @NonNull NameTagInvisibilityReason reason,
                              @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            clearSolidNameTagMode(player, viewer);
            if (player.teamData.hasHiddenNametag(viewer, reason)) {
                player.teamData.showNametag(viewer, reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
            } else {
                player.teamData.hideNametag(viewer, reason);
                if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetHidden());
            }
            updateVisibility(player, viewer);
        }, getFeatureName(), cpuReason));
    }

    public void setSolidNameTag(@NonNull TabPlayer player, @Nullable TabPlayer viewer, @NonNull String cpuReason,
                                boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            player.teamData.setSolidNametagMode(viewer, true);
            if (viewer != null) {
                player.teamData.showNametag(viewer, NameTagInvisibilityReason.HIDE_COMMAND);
                player.teamData.showNametag(viewer, NameTagInvisibilityReason.SOLID_OCCLUSION);
                updateVisibility(player, viewer);
            } else {
                player.teamData.showNametag(NameTagInvisibilityReason.HIDE_COMMAND);
                for (TabPlayer currentViewer : nameTags.getOnlinePlayers().getPlayers()) {
                    player.teamData.showNametag(currentViewer, NameTagInvisibilityReason.SOLID_OCCLUSION);
                }
                updateVisibility(player);
            }
            requestSolidOcclusionRefresh();
            if (sendMessage) player.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagTargetShown());
        }, getFeatureName(), cpuReason));
    }

    public void setSolidNameTagView(@NonNull TabPlayer viewer, @NonNull String cpuReason, boolean sendMessage) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            viewer.teamData.invisibleNameTagView = false;
            viewer.expansionData.setNameTagVisibility(true);
            for (TabPlayer player : nameTags.getOnlinePlayers().getPlayers()) {
                player.teamData.setSolidNametagMode(viewer, true);
                player.teamData.showNametag(viewer, NameTagInvisibilityReason.HIDE_COMMAND);
                player.teamData.showNametag(viewer, NameTagInvisibilityReason.SOLID_OCCLUSION);
                updateVisibility(player, viewer);
            }
            requestSolidOcclusionRefresh();
            if (sendMessage) viewer.sendMessage(TAB.getInstance().getConfiguration().getMessages().getNameTagViewShown());
        }, getFeatureName(), cpuReason));
    }

    public void clearSolidNameTagView(@NonNull TabPlayer viewer, @NonNull String cpuReason) {
        ensureActive();
        getCustomThread().execute(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            for (TabPlayer player : nameTags.getOnlinePlayers().getPlayers()) {
                clearSolidNameTagMode(player, viewer);
            }
        }, getFeatureName(), cpuReason));
    }

    private void requestSolidOcclusionRefresh() {
        TAB.getInstance().getPlatform().runSyncGlobal(this::refreshSolidOcclusion);
    }

    private void refreshSolidOcclusion() {
        for (TabPlayer player : nameTags.getOnlinePlayers().getPlayers()) {
            if (!player.teamData.hasSolidNametagMode() || player.teamData.isDisabled()) continue;
            for (TabPlayer viewer : nameTags.getOnlinePlayers().getPlayers()) {
                if (!player.teamData.isSolidNametagMode(viewer)) {
                    if (player.teamData.showNametag(viewer, NameTagInvisibilityReason.SOLID_OCCLUSION)) {
                        updateVisibility(player, viewer);
                    }
                    continue;
                }
                boolean visible = viewer == player || hasLineOfSight(viewer, player);
                boolean changed = visible
                        ? player.teamData.showNametag(viewer, NameTagInvisibilityReason.SOLID_OCCLUSION)
                        : player.teamData.hideNametag(viewer, NameTagInvisibilityReason.SOLID_OCCLUSION);
                if (changed) updateVisibility(player, viewer);
            }
        }
    }

    private void clearSolidNameTagMode(@NonNull TabPlayer player, @Nullable TabPlayer viewer) {
        player.teamData.setSolidNametagMode(viewer, false);
        if (viewer != null) {
            if (player.teamData.showNametag(viewer, NameTagInvisibilityReason.SOLID_OCCLUSION)) {
                updateVisibility(player, viewer);
            }
        } else {
            for (TabPlayer currentViewer : nameTags.getOnlinePlayers().getPlayers()) {
                if (player.teamData.showNametag(currentViewer, NameTagInvisibilityReason.SOLID_OCCLUSION)) {
                    updateVisibility(player, currentViewer);
                }
            }
        }
    }

    private boolean hasLineOfSight(@NonNull TabPlayer viewer, @NonNull TabPlayer target) {
        if (!viewer.server.canSee(target.server)) return false;
        if (viewer.world != target.world) return false;
        if (!viewer.canSee(target)) return false;
        Object viewerObject = viewer.getPlayer();
        Object targetObject = target.getPlayer();
        try {
            for (Method method : viewerObject.getClass().getMethods()) {
                if (!method.getName().equals("hasLineOfSight") || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isInstance(targetObject)) continue;
                Object result = method.invoke(viewerObject, targetObject);
                return result instanceof Boolean ? (Boolean) result : true;
            }
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
        return true;
    }
}
