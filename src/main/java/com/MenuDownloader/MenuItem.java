package com.MenuDownloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MenuItem {
    public final String displayName;
    public final String material;
    public final Integer count;
    public final Integer modelData;
    public final Integer damage;
    public final Integer rgb;
    public final List<String> lore;
    public final List<String> enchantments;
    public final List<String> potionEffects;
    public final List<Integer> slots = new ArrayList<>();

    public MenuItem(String displayName, String material, Integer count, Integer modelData,
                    Integer damage, Integer rgb,
                    List<String> lore, List<String> enchantments, List<String> potionEffects,
                    int firstSlot) {
        this.displayName = displayName;
        this.material = material;
        this.count = count;
        this.modelData = modelData;
        this.damage = damage;
        this.rgb = rgb;
        this.lore = lore;
        this.enchantments = enchantments;
        this.potionEffects = potionEffects;
        this.slots.add(firstSlot);
    }

    public boolean sameContent(MenuItem other) {
        return Objects.equals(displayName, other.displayName)
            && Objects.equals(material, other.material)
            && Objects.equals(count, other.count)
            && Objects.equals(modelData, other.modelData)
            && Objects.equals(damage, other.damage)
            && Objects.equals(rgb, other.rgb)
            && Objects.equals(lore, other.lore)
            && Objects.equals(enchantments, other.enchantments)
            && Objects.equals(potionEffects, other.potionEffects);
    }
}
