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
            int colorValue = style.getColor().getValue();
            String hexColor = String.format("%06X", colorValue & 0xFFFFFF);
            String minecraftColorCode = convertHexToColorCode(hexColor);
            if (minecraftColorCode != null) {
                builder.append("&").append(minecraftColorCode);
            } else {
                builder.append("&#").append(hexColor);
            }
        }
        if (style.isBold()) builder.append("&l");
        if (style.isItalic()) builder.append("&o");
        if (style.isUnderlined()) builder.append("&n");
        if (style.isStrikethrough()) builder.append("&m");
        if (style.isObfuscated()) builder.append("&k");
    }

    private static String convertHexToColorCode(String hexColor) {
        hexColor = hexColor.replace("&#", "");
        switch (hexColor.toUpperCase()) {
            case "000000": return "0";
            case "0000AA": return "1";
            case "00AA00": return "2";
            case "00AAAA": return "3";
            case "AA0000": return "4";
            case "AA00AA": return "5";
            case "FFAA00": return "6";
            case "AAAAAA": return "7";
            case "555555": return "8";
            case "5555FF": return "9";
            case "55FF55": return "a";
            case "55FFFF": return "b";
            case "FF5555": return "c";
            case "FF55FF": return "d";
            case "FFFF55": return "e";
            case "FFFFFF": return "f";
            default: return null;
        }
    }
}
