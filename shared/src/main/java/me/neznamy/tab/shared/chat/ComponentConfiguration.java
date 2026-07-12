package me.neznamy.tab.shared.chat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.shared.config.file.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Class holding configuration for chat components.
 */
@Getter
@RequiredArgsConstructor
public class ComponentConfiguration {

    /** Original section used for extracting data */
    @NotNull
    private final ConfigurationSection section;

    /** Whether MiniMessage should be used if available or not */
    private final boolean minimessageSupport;

    /** Whether to automatically disable shadows for head components or not */
    private final boolean disableShadowForHeads;

    /** Whether native player list head icons should be hidden when possible */
    private final boolean disableNativePlayerlistHeads;

    /** Whether real player list entries should be replaced by fake visible entries */
    private final boolean fakePlayerlistEntries;

    /** Whether fake player list entries can use NamServerCore's cached skin textures */
    private final boolean fakePlayerlistUseNamServerCoreSkins;

    /**
     * Returns instance of this class created from given configuration section. If there are
     * issues in the configuration, console warns are printed.
     *
     * @param   section
     *          Configuration section to load from
     * @return  Loaded instance from given configuration section
     */
    @NotNull
    public static ComponentConfiguration fromSection(@NotNull ConfigurationSection section) {
        // Check keys
        section.checkForUnknownKey(Arrays.asList("minimessage-support", "disable-shadow-for-heads", "disable-native-playerlist-heads",
                "fake-playerlist-entries", "fake-playerlist-use-namservercore-skins"));

        return new ComponentConfiguration(
                section,
                section.getBoolean("minimessage-support", true),
                section.getBoolean("disable-shadow-for-heads", true),
                section.getBoolean("disable-native-playerlist-heads", false),
                section.getBoolean("fake-playerlist-entries", false),
                section.getBoolean("fake-playerlist-use-namservercore-skins", true)
        );
    }
}
