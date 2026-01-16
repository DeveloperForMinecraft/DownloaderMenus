package com.MenuDownloader;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import java.util.List;

public class MenuDownload {
    
    public static String formatText(ITextComponent component) {
        if (component == null) return "";
        StringBuilder builder = new StringBuilder();
    
        for (ITextComponent sibling : component.getSiblings()) {
            processSingleComponent(sibling, builder);
        
            for (ITextComponent nested : sibling.getSiblings()) {
                processSingleComponent(nested, builder);
            }
        }
    
        String result = builder.toString();
    
        if (result.isEmpty()) {
            StringBuilder mainBuilder = new StringBuilder();
            processSingleComponent(component, mainBuilder);
            result = mainBuilder.toString();
            if (result.isEmpty()) {
                return "&r";
            }
        }
    
        return result;
    }
    
    private static StringBuilder processComponentRecursive(ITextComponent component) {
        StringBuilder builder = new StringBuilder();
        
        if (component == null) return builder;
        
        processSingleComponent(component, builder);
        
        List<ITextComponent> siblings = component.getSiblings();
        if (siblings != null && !siblings.isEmpty()) {
            for (ITextComponent sibling : siblings) {
                builder.append(processComponentRecursive(sibling));
            }
        }
        
        return builder;
    }
    private static String convertHexToColorCode(String hexColor) {
        hexColor = hexColor.replace("&#", "");
    
        switch(hexColor.toUpperCase()) {
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
    private static void processSingleComponent(ITextComponent component, StringBuilder builder) {
        if (component == null) return;
        
        String text = component.getString();
        
        if (text == null || text.isEmpty() || 
            text.equals("[]")) {
            return;
        }
        
        Style style = component.getStyle();
        
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
        
        if (text.isEmpty()) {
            builder.append("&r");
        } else {
            builder.append(text);
        }
    }
}