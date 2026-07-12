package me.neznamy.tab.platforms.bukkit;

import me.neznamy.tab.shared.platform.TabList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight reader for NamServerCore's signed skin cache.
 */
public final class NamServerCoreSkinCache {

    private static final long RELOAD_INTERVAL_MS = 5_000L;

    private long nextReloadAt;
    private long lastModified = -1L;
    @NotNull private Map<UUID, TabList.Skin> skinsByUuid = Collections.emptyMap();
    @NotNull private Map<String, UUID> uuidByName = Collections.emptyMap();

    @Nullable
    public TabList.Skin getSkin(@NotNull UUID uniqueId, @NotNull String name) {
        reloadIfNeeded();
        TabList.Skin skin = skinsByUuid.get(uniqueId);
        if (skin != null) return skin;
        UUID mapped = uuidByName.get(name.toLowerCase(Locale.ROOT));
        return mapped == null ? null : skinsByUuid.get(mapped);
    }

    private void reloadIfNeeded() {
        long now = System.currentTimeMillis();
        if (now < nextReloadAt) return;
        nextReloadAt = now + RELOAD_INTERVAL_MS;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("NamServerCore");
        if (plugin == null) return;
        File file = new File(plugin.getDataFolder(), "skin-cache.yml");
        if (!file.isFile()) return;
        long modified = file.lastModified();
        if (modified == lastModified) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<UUID, TabList.Skin> loadedSkins = new HashMap<>();
        ConfigurationSection skins = config.getConfigurationSection("skins");
        if (skins != null) {
            for (String key : skins.getKeys(false)) {
                try {
                    String value = skins.getString(key + ".value", "");
                    if (value.trim().isEmpty()) continue;
                    String signature = skins.getString(key + ".signature", "");
                    loadedSkins.put(UUID.fromString(key), new TabList.Skin(value, signature));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed cache entries from external state.
                }
            }
        }

        Map<String, UUID> loadedNames = new HashMap<>();
        ConfigurationSection names = config.getConfigurationSection("names");
        if (names != null) {
            for (String key : names.getKeys(false)) {
                try {
                    loadedNames.put(key.toLowerCase(Locale.ROOT), UUID.fromString(names.getString(key, "")));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed cache entries from external state.
                }
            }
        }

        skinsByUuid = loadedSkins;
        uuidByName = loadedNames;
        lastModified = modified;
    }
}
