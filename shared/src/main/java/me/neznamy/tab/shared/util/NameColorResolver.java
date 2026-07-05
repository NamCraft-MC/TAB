package me.neznamy.tab.shared.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the last visible text color from a formatted prefix so player names can inherit it.
 */
@UtilityClass
public class NameColorResolver {

    private static final Pattern MINI_HEX = Pattern.compile("(?i)<(?:color:)?#([0-9a-f]{6})>");
    private static final String LEGACY_COLOR_CODES = "0123456789abcdef";
    private static final String LEGACY_FORMAT_CODES = "klmno";

    /**
     * Returns a formatting code that re-applies the last color found in {@code prefix}.
     *
     * @param   prefix
     *          Formatted prefix to scan
     * @return  Color code to prepend to a name, or empty string if no color was found
     */
    @NotNull
    public static String lastColorCode(@NotNull String prefix) {
        LastColor color = LastColor.empty();
        for (int i = 0; i < prefix.length(); i++) {
            char current = prefix.charAt(i);
            if ((current == '&' || current == '§') && i + 7 < prefix.length()
                    && prefix.charAt(i + 1) == '#'
                    && isHex(prefix, i + 2, 6)) {
                color = new LastColor(i, "&#" + prefix.substring(i + 2, i + 8));
                i += 7;
                continue;
            }
            if ((current == '&' || current == '§') && i + 13 < prefix.length()
                    && Character.toLowerCase(prefix.charAt(i + 1)) == 'x') {
                String expanded = readExpandedHex(prefix, i);
                if (expanded != null) {
                    color = new LastColor(i, "&#" + expanded);
                    i += 13;
                    continue;
                }
            }
            if ((current == '&' || current == '§') && i + 1 < prefix.length()) {
                char code = Character.toLowerCase(prefix.charAt(i + 1));
                if (LEGACY_COLOR_CODES.indexOf(code) >= 0) {
                    color = new LastColor(i, "&" + code);
                    i++;
                    continue;
                }
                if (code == 'r') {
                    color = LastColor.empty();
                    i++;
                }
            }
        }

        Matcher matcher = MINI_HEX.matcher(prefix);
        while (matcher.find()) {
            if (matcher.start() > color.position()) {
                color = new LastColor(matcher.start(), "<#" + matcher.group(1).toUpperCase(Locale.ROOT) + ">");
            }
        }
        return color.code();
    }

    /**
     * Checks if the value begins with an explicit color or formatting code.
     *
     * @param   value
     *          Value to inspect
     * @return  {@code true} if it starts with a color or formatting code
     */
    public static boolean startsWithFormatting(@NotNull String value) {
        String trimmed = trimLeading(value);
        if (trimmed.length() >= 2 && (trimmed.charAt(0) == '&' || trimmed.charAt(0) == '§')) {
            char code = Character.toLowerCase(trimmed.charAt(1));
            return code == '#' || code == 'x' || code == 'r'
                    || LEGACY_COLOR_CODES.indexOf(code) >= 0
                    || LEGACY_FORMAT_CODES.indexOf(code) >= 0;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("<#")
                || lower.startsWith("<color:#")
                || lower.startsWith("<gradient:")
                || lower.startsWith("<rainbow")
                || lower.startsWith("<reset>")
                || lower.startsWith("<black>")
                || lower.startsWith("<dark_blue>")
                || lower.startsWith("<dark_green>")
                || lower.startsWith("<dark_aqua>")
                || lower.startsWith("<dark_red>")
                || lower.startsWith("<dark_purple>")
                || lower.startsWith("<gold>")
                || lower.startsWith("<gray>")
                || lower.startsWith("<grey>")
                || lower.startsWith("<dark_gray>")
                || lower.startsWith("<dark_grey>")
                || lower.startsWith("<blue>")
                || lower.startsWith("<green>")
                || lower.startsWith("<aqua>")
                || lower.startsWith("<red>")
                || lower.startsWith("<light_purple>")
                || lower.startsWith("<yellow>")
                || lower.startsWith("<white>");
    }

    /**
     * Removes trailing legacy color / formatting codes from text.
     *
     * @param   value
     *          Value to trim
     * @return  Value without trailing legacy formatting codes
     */
    @NotNull
    public static String trimTrailingFormatting(@NotNull String value) {
        int trailingWhitespaceStart = value.length();
        while (trailingWhitespaceStart > 0 && Character.isWhitespace(value.charAt(trailingWhitespaceStart - 1))) {
            trailingWhitespaceStart--;
        }
        int end = trailingWhitespaceStart;
        while (end >= 2) {
            char marker = value.charAt(end - 2);
            if (marker != '&' && marker != '§') break;
            char code = Character.toLowerCase(value.charAt(end - 1));
            if (code != 'r' && LEGACY_COLOR_CODES.indexOf(code) < 0 && LEGACY_FORMAT_CODES.indexOf(code) < 0) break;
            end -= 2;
        }
        return end == trailingWhitespaceStart ? value : value.substring(0, end) + value.substring(trailingWhitespaceStart);
    }

    private static boolean isHex(@NotNull String input, int start, int length) {
        if (start < 0 || start + length > input.length()) return false;
        for (int i = start; i < start + length; i++) {
            if (Character.digit(input.charAt(i), 16) == -1) return false;
        }
        return true;
    }

    private static String readExpandedHex(@NotNull String input, int start) {
        StringBuilder hex = new StringBuilder(6);
        int cursor = start + 2;
        for (int i = 0; i < 6; i++) {
            if (cursor + 1 >= input.length()) return null;
            char separator = input.charAt(cursor);
            char value = input.charAt(cursor + 1);
            if (separator != '&' && separator != '§') return null;
            if (Character.digit(value, 16) == -1) return null;
            hex.append(value);
            cursor += 2;
        }
        return hex.toString();
    }

    @NotNull
    private static String trimLeading(@NotNull String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return value.substring(index);
    }

    private static final class LastColor {

        private final int position;
        @NotNull private final String code;

        private LastColor(int position, @NotNull String code) {
            this.position = position;
            this.code = code;
        }

        private int position() {
            return position;
        }

        @NotNull
        private String code() {
            return code;
        }

        private static LastColor empty() {
            return new LastColor(-1, "");
        }
    }
}
