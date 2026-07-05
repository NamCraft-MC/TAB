package me.neznamy.tab.shared.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An enum containing all possible legacy color codes and magic codes. Also contains handy color-related methods.
 */
@Getter
@AllArgsConstructor
public enum EnumChatFormat {

    BLACK('0', true),
    DARK_BLUE('1', true),
    DARK_GREEN('2', true),
    DARK_AQUA('3', true),
    DARK_RED('4', true),
    DARK_PURPLE('5', true),
    GOLD('6', true),
    GRAY('7', true),
    DARK_GRAY('8', true),
    BLUE('9', true),
    GREEN('a', true),
    AQUA('b', true),
    RED('c', true),
    LIGHT_PURPLE('d', true),
    YELLOW('e', true),
    WHITE('f', true),
    OBFUSCATED('k', false),
    BOLD('l', false),
    STRIKETHROUGH('m', false),
    UNDERLINE('n', false),
    ITALIC('o', false),
    RESET('r', false);

    /** Character representing the color or magic code */
    private final char character;

    /** Flag tracking whether this is a color code or not */
    private final boolean color;

    /** BBCode-like RGB tag used by some web generators: [COLOR=#RRGGBB]text[/COLOR] */
    private static final Pattern BBCODE_COLOR = Pattern.compile("(?i)\\[COLOR=\\s*#?([0-9A-F]{6})\\]");

    /** NamCraft shorthand RGB format: &RRGGBB */
    private static final Pattern AMPERSAND_RGB = Pattern.compile("(?i)&([0-9A-F]{6})");

    /**
     * Color translation method taken from bukkit, which converts '&amp;' symbol into
     * the actual color character if followed by a valid color character.
     *
     * @param   textToTranslate
     *          text to replace color symbol in
     * @return  colorized string from provided text
     */
    public static @NotNull String color(@NotNull String textToTranslate) {
        textToTranslate = convertBbCodeColors(textToTranslate);
        textToTranslate = convertAmpersandRgb(textToTranslate);
        if (!textToTranslate.contains("&")) return textToTranslate;
        char[] b = textToTranslate.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if ((b[i] == '&') && ("0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx#".indexOf(b[(i + 1)]) > -1)) {
                b[i] = '§';
                b[(i + 1)] = Character.toLowerCase(b[(i + 1)]);
            }
        }
        return new String(b);
    }

    @NotNull
    private static String convertBbCodeColors(@NotNull String text) {
        if (text.indexOf('[') == -1) return text;
        Matcher matcher = BBCODE_COLOR.matcher(text);
        StringBuffer converted = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(converted, "#" + matcher.group(1));
        }
        matcher.appendTail(converted);
        return converted.toString().replaceAll("(?i)\\[/COLOR\\]", "");
    }

    @NotNull
    private static String convertAmpersandRgb(@NotNull String text) {
        if (text.indexOf('&') == -1) return text;
        Matcher matcher = AMPERSAND_RGB.matcher(text);
        StringBuffer converted = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(converted, "#" + matcher.group(1));
        }
        matcher.appendTail(converted);
        return converted.toString();
    }
}
