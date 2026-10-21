package com.MenuDownloader;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import java.util.Optional;

public class MenuDownload {

    public static String formatText(ITextComponent component) {
        if (component == null) return "";
        StringBuilder result = new StringBuilder();
        component.visit((style, text) -> {
            if (!text.isEmpty()) {
                appendStyle(result, style);
                result.append(text);
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (result.length() == 0) return "&r";
        return result.toString();
    }

    private static void appendStyle(StringBuilder builder, Style style) {
        if (style.getColor() != null) {
            String code = legacyColorCode(style.getColor().getValue());
            if (code != null) {
                builder.append('&').append(code);
            } else {
                builder.append("&#").append(String.format("%06X", style.getColor().getValue() & 0xFFFFFF));
            }
        }
        if (style.isBold()) builder.append("&l");
        if (style.isItalic()) builder.append("&o");
        if (style.isUnderlined()) builder.append("&n");
        if (style.isStrikethrough()) builder.append("&m");
        if (style.isObfuscated()) builder.append("&k");
    }

    private static String legacyColorCode(int rgb) {
        switch (rgb & 0xFFFFFF) {
            case 0x000000: return "0";
            case 0x0000AA: return "1";
            case 0x00AA00: return "2";
            case 0x00AAAA: return "3";
            case 0xAA0000: return "4";
            case 0xAA00AA: return "5";
            case 0xFFAA00: return "6";
            case 0xAAAAAA: return "7";
            case 0x555555: return "8";
            case 0x5555FF: return "9";
            case 0x55FF55: return "a";
            case 0x55FFFF: return "b";
            case 0xFF5555: return "c";
            case 0xFF55FF: return "d";
            case 0xFFFF55: return "e";
            case 0xFFFFFF: return "f";
            default: return null;
        }
    }
}
