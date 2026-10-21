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
    public final Integer mapId;
    public final String potionType;
    public final boolean unbreakable;
    public final int hideFlags;
    public final List<String> lore;
    public final List<String> enchantments;
    public final List<String> potionEffects;
    public final List<String> bannerMeta;
    public final String baseColor;
    public final List<Integer> slots = new ArrayList<>();

    public MenuItem(String displayName, String material, Integer count, Integer modelData,
                    Integer damage, Integer rgb, Integer mapId, String potionType,
                    boolean unbreakable, int hideFlags,
                    List<String> lore, List<String> enchantments, List<String> potionEffects,
                    List<String> bannerMeta, String baseColor, int firstSlot) {
        this.displayName = displayName;
        this.material = material;
        this.count = count;
        this.modelData = modelData;
        this.damage = damage;
        this.rgb = rgb;
        this.mapId = mapId;
        this.potionType = potionType;
        this.unbreakable = unbreakable;
        this.hideFlags = hideFlags;
        this.lore = lore;
        this.enchantments = enchantments;
        this.potionEffects = potionEffects;
        this.bannerMeta = bannerMeta;
        this.baseColor = baseColor;
        this.slots.add(firstSlot);
    }

    public boolean sameContent(MenuItem other) {
        return Objects.equals(displayName, other.displayName)
            && Objects.equals(material, other.material)
            && Objects.equals(count, other.count)
            && Objects.equals(modelData, other.modelData)
            && Objects.equals(damage, other.damage)
            && Objects.equals(rgb, other.rgb)
            && Objects.equals(mapId, other.mapId)
            && Objects.equals(potionType, other.potionType)
            && unbreakable == other.unbreakable
            && hideFlags == other.hideFlags
            && Objects.equals(lore, other.lore)
            && Objects.equals(enchantments, other.enchantments)
            && Objects.equals(potionEffects, other.potionEffects)
            && Objects.equals(bannerMeta, other.bannerMeta)
            && Objects.equals(baseColor, other.baseColor);
    }
}
