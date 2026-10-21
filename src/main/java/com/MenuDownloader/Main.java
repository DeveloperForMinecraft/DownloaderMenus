package com.MenuDownloader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.potion.Effect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(value = "downloadmenus")
public class Main {
    private static final int FILE_COMMAND_MAX_LEN = 50;
    private static String openCommand = "unknown";
    private static volatile MenuLogicRecorder logicRecorder;

    private static final Map<String, String> ENCHANT_TO_BUKKIT = new HashMap<>();
    private static final Map<String, String> EFFECT_TO_BUKKIT = new HashMap<>();

    static {
        ENCHANT_TO_BUKKIT.put("protection", "PROTECTION_ENVIRONMENTAL");
        ENCHANT_TO_BUKKIT.put("fire_protection", "PROTECTION_FIRE");
        ENCHANT_TO_BUKKIT.put("feather_falling", "PROTECTION_FALL");
        ENCHANT_TO_BUKKIT.put("blast_protection", "PROTECTION_EXPLOSIONS");
        ENCHANT_TO_BUKKIT.put("respiration", "OXYGEN");
        ENCHANT_TO_BUKKIT.put("aqua_affinity", "WATER_WORKER");
        ENCHANT_TO_BUKKIT.put("sharpness", "DAMAGE_ALL");
        ENCHANT_TO_BUKKIT.put("smite", "DAMAGE_UNDEAD");
        ENCHANT_TO_BUKKIT.put("bane_of_arthropods", "DAMAGE_ARTHROPODS");
        ENCHANT_TO_BUKKIT.put("sweeping", "SWEEPING_EDGE");
        ENCHANT_TO_BUKKIT.put("efficiency", "DIG_SPEED");
        ENCHANT_TO_BUKKIT.put("unbreaking", "DURABILITY");
        ENCHANT_TO_BUKKIT.put("fortune", "LOOT_BONUS_BLOCKS");
        ENCHANT_TO_BUKKIT.put("power", "ARROW_DAMAGE");
        ENCHANT_TO_BUKKIT.put("punch", "ARROW_KNOCKBACK");
        ENCHANT_TO_BUKKIT.put("flame", "ARROW_FIRE");
        ENCHANT_TO_BUKKIT.put("infinity", "ARROW_INFINITE");
        ENCHANT_TO_BUKKIT.put("luck_of_the_sea", "LUCK");
        ENCHANT_TO_BUKKIT.put("curse_of_binding", "BINDING_CURSE");
        ENCHANT_TO_BUKKIT.put("curse_of_vanishing", "VANISHING_CURSE");
        EFFECT_TO_BUKKIT.put("slowness", "SLOW");
        EFFECT_TO_BUKKIT.put("haste", "FAST_DIGGING");
        EFFECT_TO_BUKKIT.put("mining_fatigue", "SLOW_DIGGING");
        EFFECT_TO_BUKKIT.put("strength", "INCREASE_DAMAGE");
        EFFECT_TO_BUKKIT.put("instant_health", "HEAL");
        EFFECT_TO_BUKKIT.put("instant_damage", "HARM");
        EFFECT_TO_BUKKIT.put("jump_boost", "JUMP");
        EFFECT_TO_BUKKIT.put("resistance", "DAMAGE_RESISTANCE");
    }

    public Main() {
        Logger.log("MenuDownloader");
        PacketSniffer.hook = (outbound, packet) -> {
            MenuLogicRecorder r = logicRecorder;
            if (r != null) {
                r.onPacket(outbound, packet);
            }
        };
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message.equals("/menulogic") || message.startsWith("/menulogic ")) {
            event.setCanceled(true);
            handleMenuLogic(message.substring("/menulogic".length()).trim());
            return;
        }
        if (message.startsWith("/")) {
            openCommand = message.substring(1).trim();
        }
    }

    private void handleMenuLogic(String arg) {
        if (logicRecorder == null) {
            if (arg.equals("stop")) {
                msg("§cЗапись логики не активна. Введи /menulogic для начала.");
                return;
            }
            if (Minecraft.getInstance().getCurrentServer() == null) {
                msg("§cЗапись логики доступна только на сервере");
                return;
            }
            PacketSniffer.inject();
            logicRecorder = new MenuLogicRecorder(currentServerDirName());
            if (Minecraft.getInstance().screen != null) {
                logicRecorder.onGuiOpen(Minecraft.getInstance().screen);
            }
            msg("§aЗапись логики меню началась.");
            msg("§aПоработай с меню (клики, переходы между меню), затем введи /menulogic — остановить и сохранить.");
            return;
        }
        File dir = logicRecorder.save();
        logicRecorder = null;
        if (dir != null) {
            msg("§aЛогика меню сохранена: " + dir.getPath());
            msg("§aФайлы: logic_graph.json (граф переходов) + YML-меню с click-командами");
        } else {
            msg("§cВо время записи не найдено ни одного меню. Открой меню и покликай по его слотам.");
        }
    }

    @SubscribeEvent
    public void onLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        PacketSniffer.inject();
    }

    @SubscribeEvent
    public void onLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        if (logicRecorder != null) {
            logicRecorder.save();
            logicRecorder = null;
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (logicRecorder != null) {
            logicRecorder.onGuiOpen(event.getGui());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && logicRecorder != null) {
            logicRecorder.onClientTick(Minecraft.getInstance());
        }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.screen instanceof ChestScreen) {
            ChestScreen chestScreen = (ChestScreen) minecraft.screen;
            event.addWidget(new Button(
                    10, 8, 100, 20,
                    new StringTextComponent("Скачать меню"),
                    button -> saveMenu(chestScreen)
            ));
        }
    }

    private void saveMenu(ChestScreen chestScreen) {
        if (Minecraft.getInstance().getCurrentServer() == null) {
            msg("§cСохранение меню доступно только на сервере");
            return;
        }

        String serverIdentifier = currentServerDirName();
        File serverDir = serverDirFor(serverIdentifier);

        String fileCommand = openCommand.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (fileCommand.isEmpty() || fileCommand.equals("unknown")) {
            fileCommand = "menu";
        }
        if (fileCommand.length() > FILE_COMMAND_MAX_LEN) {
            fileCommand = fileCommand.substring(0, FILE_COMMAND_MAX_LEN);
        }
        String fileName = fileCommand + " " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd;HH-mm-ss")) + ".yml";
        File menuFile = new File(serverDir, fileName);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(menuFile), StandardCharsets.UTF_8))) {
            int chestSize = ((ChestContainer) chestScreen.getMenu()).slots.size() - 36;
            List<MenuItem> menuItems = collectMenuItems(chestScreen, chestSize);
            writer.write("#By DecentMCDev\n#Скачать из контейнера\nmenu_title: " + yamlQuote(MenuDownload.formatText(chestScreen.getTitle())) + "\n");
            String commandToken = openCommand.trim().split("\\s+")[0];
            if (!commandToken.equals("unknown")) {
                writer.write("open_command:\n  - " + commandToken + "\nregister_command: true\n");
            }
            writer.write("size: " + chestSize + "\nitems:\n");
            writeItemsSection(writer, menuItems, null);

            msg("§aСохранено: Downloaded Menus/" + serverIdentifier + "/" + menuFile.getName());
        } catch (IOException e) {
            e.printStackTrace();
            msg("§cОшибка сохранения меню: " + e.getMessage());
        }
    }

    static void msg(String text) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(new StringTextComponent(text), false);
        }
    }

    static String currentServerDirName() {
        ServerData data = Minecraft.getInstance().getCurrentServer();
        String id = firstNonEmpty(data != null ? data.ip : null, data != null ? data.name : null);
        if (id == null) {
            return "unknown_server";
        }
        String sanitized = id.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "unknown_server" : sanitized;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    static File serverDirFor(String name) {
        File baseDir = new File("Downloaded Menus");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        File dir = new File(baseDir, name);
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                dir = baseDir;
            }
        } catch (SecurityException e) {
            dir = baseDir;
        }
        return dir;
    }

    static List<MenuItem> collectMenuItems(ChestScreen chestScreen, int chestSize) {
        List<MenuItem> menuItems = new ArrayList<>();
        for (Slot slot : ((ChestContainer) chestScreen.getMenu()).slots) {
            ItemStack stack = slot.getItem();
            CompoundNBT tag = stack.getTag();
            if (stack.isEmpty() || slot.index >= chestSize) {
                continue;
            }

            String displayName = MenuDownload.formatText(stack.getHoverName());

            String material = materialOf(stack);

            int amount = stack.getCount();
            Integer count = amount > 1 ? amount : null;

            Integer modelData = tag != null && tag.contains("CustomModelData", 99) ? tag.getInt("CustomModelData") : null;

            Integer damage = stack.isDamageableItem() && stack.getDamageValue() > 0 ? stack.getDamageValue() : null;

            Integer rgb = null;
            if (tag != null) {
                if (tag.contains("display", 10) && tag.getCompound("display").contains("color", 3)) {
                    rgb = tag.getCompound("display").getInt("color");
                } else if (tag.contains("CustomPotionColor", 3)) {
                    rgb = tag.getInt("CustomPotionColor");
                }
            }

            List<String> lore = null;
            if (tag != null && tag.contains("display", 10)
                    && tag.getCompound("display").contains("Lore", 9)) {
                lore = new ArrayList<>();
                ListNBT loreList = tag.getCompound("display").getList("Lore", 8);
                for (int i = 0; i < loreList.size(); i++) {
                    String loreLineJson = loreList.getString(i);
                    if (loreLineJson == null || loreLineJson.isEmpty()) {
                        continue;
                    }
                    try {
                        net.minecraft.util.text.ITextComponent loreComp = net.minecraft.util.text.ITextComponent.Serializer.fromJson(loreLineJson);
                        if (loreComp != null) {
                            String formatted = MenuDownload.formatText(loreComp);
                            lore.add(isBlankFormattingLine(formatted) ? "" : formatted);
                        }
                    } catch (Exception e) {
                        String legacyLine = loreLineJson.replace("§", "&");
                        lore.add(isBlankFormattingLine(legacyLine) ? "" : legacyLine);
                    }
                }
            }

            List<String> enchantments = null;
            Map<net.minecraft.enchantment.Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
            if (!enchants.isEmpty()) {
                enchantments = new ArrayList<>();
                for (Map.Entry<net.minecraft.enchantment.Enchantment, Integer> entry : enchants.entrySet()) {
                    try {
                        enchantments.add(bukkitEnchant(entry.getKey().getRegistryName().getPath()) + ";" + entry.getValue());
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }

            if (tag != null && tag.contains("StoredEnchantments", 9)) {
                if (enchantments == null) {
                    enchantments = new ArrayList<>();
                }
                ListNBT stored = tag.getList("StoredEnchantments", 10);
                for (int i = 0; i < stored.size(); i++) {
                    CompoundNBT enchTag = stored.getCompound(i);
                    try {
                        enchantments.add(bukkitEnchant(new ResourceLocation(enchTag.getString("id")).getPath()) + ";" + enchTag.getShort("lvl"));
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }

            List<String> potionEffects = null;
            if (tag != null && tag.contains("CustomPotionEffects", 9)) {
                potionEffects = new ArrayList<>();
                ListNBT effectsList = tag.getList("CustomPotionEffects", 10);
                for (int i = 0; i < effectsList.size(); i++) {
                    CompoundNBT effectTag = effectsList.getCompound(i);
                    Effect effect = Effect.byId(effectTag.getByte("Id") & 0xFF);
                    if (effect != null && effect.getRegistryName() != null) {
                        String effectPath = effect.getRegistryName().getPath();
                        String effectName = EFFECT_TO_BUKKIT.getOrDefault(effectPath, effectPath.toUpperCase(Locale.ROOT));
                        potionEffects.add(effectName + ";" + effectTag.getInt("Duration") + ";" + effectTag.getByte("Amplifier"));
                    }
                }
            }

            Integer mapId = null;
            String potionType = null;
            boolean unbreakable = false;
            int hideFlags = 0;
            if (tag != null) {
                if (tag.contains("map", 99)) {
                    mapId = tag.getInt("map");
                }
                if (tag.contains("Potion", 8)) {
                    potionType = tag.getString("Potion");
                }
                unbreakable = tag.contains("Unbreakable", 99) && tag.getByte("Unbreakable") != 0;
                if (tag.contains("HideFlags", 99)) {
                    hideFlags = tag.getInt("HideFlags");
                }
            }

            List<String> bannerMeta = null;
            String baseColor = null;
            if (tag != null && tag.contains("BlockEntityTag", 10)) {
                CompoundNBT beTag = tag.getCompound("BlockEntityTag");
                if (beTag.contains("Patterns", 9)) {
                    ListNBT patterns = beTag.getList("Patterns", 10);
                    bannerMeta = new ArrayList<>();
                    for (int i = 0; i < patterns.size(); i++) {
                        CompoundNBT patternTag = patterns.getCompound(i);
                        net.minecraft.tileentity.BannerPattern pattern =
                                net.minecraft.tileentity.BannerPattern.byHash(patternTag.getString("Pattern"));
                        net.minecraft.item.DyeColor dye =
                                net.minecraft.item.DyeColor.byId(patternTag.getInt("Color") & 15);
                        if (pattern != null && dye != null) {
                            bannerMeta.add(dye.name() + ";" + pattern.name());
                        }
                    }
                    if (bannerMeta.isEmpty()) {
                        bannerMeta = null;
                    }
                }
                if (beTag.contains("Base", 99)) {
                    net.minecraft.item.DyeColor dye = net.minecraft.item.DyeColor.byId(beTag.getInt("Base") & 15);
                    if (dye != null) {
                        baseColor = dye.name();
                    }
                }
            }

            MenuItem newItem = new MenuItem(displayName, material, count, modelData, damage, rgb, mapId,
                    potionType, unbreakable, hideFlags, lore, enchantments, potionEffects,
                    bannerMeta, baseColor, slot.index);

            boolean grouped = false;
            for (MenuItem existing : menuItems) {
                if (existing.sameContent(newItem)) {
                    existing.slots.add(slot.index);
                    grouped = true;
                    break;
                }
            }
            if (!grouped) {
                menuItems.add(newItem);
            }
        }
        return menuItems;
    }

    private static boolean isBlankFormattingLine(String line) {
        String noCodes = line.replaceAll("(&#[0-9A-Fa-f]{6})|(&[0-9A-Fa-fk-orK-OR])", "");
        return noCodes.trim().isEmpty();
    }

    private static String materialOf(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        if (tag != null && tag.contains("SkullOwner", 10)) {
            ListNBT textures = tag.getCompound("SkullOwner").getCompound("Properties").getList("textures", 10);
            if (!textures.isEmpty()) {
                return "basehead-" + textures.getCompound(0).getString("Value");
            }
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        return name != null ? name.getPath() : "air";
    }

    private static String bukkitEnchant(String path) {
        return ENCHANT_TO_BUKKIT.getOrDefault(path, path.toUpperCase(Locale.ROOT));
    }

    static String yamlQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    static void writeItemsSection(Writer writer, List<MenuItem> menuItems,
                                  Map<Integer, MenuLogicRecorder.SlotActions> actionsBySlot) throws IOException {
        Map<String, Integer> materialCounters = new HashMap<>();
        for (MenuItem item : menuItems) {
            String baseName = item.material.startsWith("basehead-") ? "basehead" : item.material;
            int cnt = materialCounters.getOrDefault(baseName, 0) + 1;
            materialCounters.put(baseName, cnt);
            String key = baseName + "_" + cnt;

            Map<String, List<Integer>> parts = new LinkedHashMap<>();
            for (int s : item.slots) {
                String sig = MenuLogicRecorder.SlotActions.signature(actionsBySlot == null ? null : actionsBySlot.get(s));
                List<Integer> partSlots = parts.get(sig);
                if (partSlots == null) {
                    partSlots = new ArrayList<>();
                    parts.put(sig, partSlots);
                }
                partSlots.add(s);
            }
            int part = 0;
            for (List<Integer> partSlots : parts.values()) {
                part++;
                String itemKey = parts.size() > 1 ? key + "_" + part : key;
                MenuLogicRecorder.SlotActions actions = actionsBySlot == null ? null : actionsBySlot.get(partSlots.get(0));
                writeMenuItem(writer, itemKey, item, partSlots, actions);
            }
        }
    }

    private static void opt(Writer writer, boolean condition, String line) throws IOException {
        if (condition) {
            writer.write("    " + line + "\n");
        }
    }

    private static void optList(Writer writer, String name, List<String> values, boolean quoted) throws IOException {
        if (values == null || values.isEmpty()) {
            return;
        }
        writer.write("    " + name + ":\n");
        for (String value : values) {
            writer.write("    - " + (quoted ? yamlQuote(value) : value) + "\n");
        }
    }

    private static void writeMenuItem(Writer writer, String key, MenuItem item,
                                      List<Integer> slots, MenuLogicRecorder.SlotActions actions) throws IOException {
        writer.write("  '" + key + "':\n    display_name: " + yamlQuote(item.displayName) + "\n");
        if (actions != null) {
            for (String comment : actions.comments) {
                writer.write("    " + comment + "\n");
            }
        }

        writer.write("    slots:\n");
        for (int s : slots) {
            writer.write("    - " + s + "\n");
        }

        writer.write("    material: " + item.material + "\n");
        opt(writer, item.count != null, "amount: " + item.count);
        opt(writer, item.damage != null, "damage: " + item.damage);
        opt(writer, item.modelData != null, "model_data: " + item.modelData);
        opt(writer, item.rgb != null,
                "rgb: " + ((item.rgb >> 16) & 0xFF) + ", " + ((item.rgb >> 8) & 0xFF) + ", " + (item.rgb & 0xFF));
        optList(writer, "lore", item.lore, true);
        optList(writer, "enchantments", item.enchantments, true);
        optList(writer, "potion_effects", item.potionEffects, false);
        opt(writer, item.baseColor != null, "base_color: " + item.baseColor);
        optList(writer, "banner_meta", item.bannerMeta, true);
        opt(writer, item.mapId != null, "nbt_int: 'map:" + item.mapId + "'");
        opt(writer, item.potionType != null, "nbt_string: 'Potion:" + item.potionType + "'");
        opt(writer, item.unbreakable, "unbreakable: true");

        int[] bits = {1, 2, 4, 32, 64};
        String[] names = {"HIDE_ENCHANTS", "HIDE_ATTRIBUTES", "HIDE_UNBREAKABLE", "HIDE_POTION_EFFECTS", "HIDE_DYE"};
        List<String> flags = new ArrayList<>();
        for (int i = 0; i < bits.length; i++) {
            if ((item.hideFlags & bits[i]) != 0) {
                flags.add(names[i]);
            }
        }
        optList(writer, "item_flags", flags, false);

        if (actions != null) {
            for (Map.Entry<String, String> entry : actions.commands.entrySet()) {
                writer.write("    " + entry.getKey() + ":\n    - '" + entry.getValue() + "'\n");
            }
        }
    }
}
