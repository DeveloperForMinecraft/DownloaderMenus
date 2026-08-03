package com.MenuDownloader;

import com.MenuDownloader.Logger;
import com.MenuDownloader.MenuDownload;
import com.MenuDownloader.MenuItem;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.potion.Effect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(value = "downloadmenus")
public class Main {
    private static String openCommand = "unknown";

    public Main() {
        Logger.log("MenuDownloader");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith("/")) {
            openCommand = message.replace("/", "");
        }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }

        if (Minecraft.getInstance().screen instanceof ChestScreen) {
            ChestScreen chestScreen = (ChestScreen) Minecraft.getInstance().screen;
            event.addWidget(new Button(
                    10, 8, 100, 20,
                    new StringTextComponent("Скачать меню"),
                    button -> {
                        if (Minecraft.getInstance().getCurrentServer() == null) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    new StringTextComponent("§cСохранение меню доступно только на сервере"),
                                    false
                            );
                            return;
                        }

                        File baseDir = new File("Downloaded Menus");
                        if (!baseDir.exists()) {
                            baseDir.mkdirs();
                        }

                        String serverIdentifier = null;
                        if (Minecraft.getInstance().getCurrentServer().ip != null &&
                            !Minecraft.getInstance().getCurrentServer().ip.isEmpty()) {
                            serverIdentifier = Minecraft.getInstance().getCurrentServer().ip;
                            serverIdentifier = serverIdentifier
                                .replace(":", "_")
                                .replace("/", "_")
                                .replace("\\", "_")
                                .replace("?", "_")
                                .replace("*", "_")
                                .replace("\"", "_")
                                .replace("<", "_")
                                .replace(">", "_")
                                .replace("|", "_");
                        } else {
                            serverIdentifier = Minecraft.getInstance().getCurrentServer().name;
                        }

                        File serverDir = null;

                        if (serverIdentifier != null && !serverIdentifier.isEmpty()) {
                            serverDir = new File(baseDir, serverIdentifier);
                            if (!serverDir.exists()) {
                                try {
                                    if (!serverDir.mkdirs()) {
                                        String serverName = Minecraft.getInstance().getCurrentServer().name;
                                        if (serverName != null && !serverName.equals(serverIdentifier)) {
                                            serverDir = new File(baseDir, serverName);
                                            if (!serverDir.exists()) {
                                                try {
                                                    serverDir.mkdirs();
                                                } catch (SecurityException e) {
                                                    // Skip error
                                                }
                                            }
                                        }
                                    }
                                } catch (SecurityException e) {
                                    // Skip error
                                }
                            }
                        }

                        if (serverDir == null || !serverDir.exists()) {
                            serverDir = baseDir;
                        }

                        String fileName = openCommand + " " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd;HH-mm-ss")) + ".yml";
                        File menuFile = new File(serverDir, fileName);

                        try {
                            menuFile.createNewFile();
                        } catch (IOException e) {
                            // Skip error
                        }

                        Minecraft.getInstance().player.displayClientMessage(
                                new StringTextComponent("§aГотово!"),
                                false
                        );

                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(menuFile, false))) {
                            int chestSize = ((ChestContainer) chestScreen.getMenu()).slots.size() - 36;
                            writer.write("#By DecentMCDev\n#Скачать из контейнера\nmenu_title: '" + MenuDownload.formatText(chestScreen.getTitle()).replace("'", "''") + "'\nopen_command: \n  - " + openCommand + "\nsize: " + chestSize + "\nregister_command: true\nitems:\n");

                            // --- Фаза 1: собираем все предметы ---
                            List<MenuItem> menuItems = new ArrayList<>();
                            Iterator<Slot> slotIterator = ((ChestContainer) chestScreen.getMenu()).slots.iterator();
                            while (slotIterator.hasNext()) {
                                Slot slot = slotIterator.next();
                                if (slot.getItem().isEmpty() || slot.index >= chestSize) {
                                    continue;
                                }

                                String displayName = MenuDownload.formatText(slot.getItem().getHoverName()).replace("'", "''");

                                String material;
                                if (slot.getItem().getTag() != null && slot.getItem().getTag().toString().contains("Properties:{textures:[{Value:\"")) {
                                    try {
                                        String texture = slot.getItem().getTag().toString().split("Properties:\\{textures:\\[\\{Value:\"")[1].split("\"}]}}")[0];
                                        material = "basehead-" + texture;
                                    } catch (Exception e) {
                                        ResourceLocation rn = slot.getItem().getItem().getRegistryName();
                                        material = (rn != null) ? rn.getPath() : "air";
                                    }
                                } else {
                                    ResourceLocation rn = slot.getItem().getItem().getRegistryName();
                                    material = (rn != null) ? rn.getPath() : "air";
                                }

                                Integer count = slot.getItem().getCount() > 1 ? slot.getItem().getCount() : null;

                                Integer modelData = null;
                                if (slot.getItem().getTag() != null && slot.getItem().getTag().contains("CustomModelData")) {
                                    modelData = slot.getItem().getTag().getInt("CustomModelData");
                                }

                                Integer damage = null;
                                if (slot.getItem().isDamageableItem() && slot.getItem().getDamageValue() > 0) {
                                    damage = slot.getItem().getDamageValue();
                                }

                                Integer rgb = null;
                                if (slot.getItem().hasTag()) {
                                    if (slot.getItem().getTag().contains("display", 10) &&
                                        slot.getItem().getTag().getCompound("display").contains("color", 3)) {
                                        rgb = slot.getItem().getTag().getCompound("display").getInt("color");
                                    } else if (slot.getItem().getTag().contains("CustomPotionColor", 3)) {
                                        rgb = slot.getItem().getTag().getInt("CustomPotionColor");
                                    }
                                }

                                List<String> lore = null;
                                if (slot.getItem().hasTag() &&
                                    slot.getItem().getTag().contains("display", 10) &&
                                    slot.getItem().getTag().getCompound("display").contains("Lore", 9)) {
                                    lore = new ArrayList<>();
                                    ListNBT loreList = slot.getItem().getTag().getCompound("display").getList("Lore", 8);
                                    for (int i = 0; i < loreList.size(); i++) {
                                        String loreLineJson = loreList.getString(i);
                                        if (loreLineJson != null && !loreLineJson.isEmpty()) {
                                            try {
                                                net.minecraft.util.text.ITextComponent loreComp = net.minecraft.util.text.ITextComponent.Serializer.fromJson(loreLineJson);
                                                if (loreComp != null) {
                                                    String formatted = MenuDownload.formatText(loreComp);
                                                    if (formatted.equals("&r")) formatted = "";
                                                    lore.add(formatted.replace("'", "''"));
                                                }
                                            } catch (Exception e) {
                                                lore.add(loreLineJson.replace("§", "&").replace("'", "''"));
                                            }
                                        }
                                    }
                                }

                                List<String> enchantments = null;
                                if (!EnchantmentHelper.getEnchantments(slot.getItem()).isEmpty()) {
                                    enchantments = new ArrayList<>();
                                    for (java.util.Map.Entry<net.minecraft.enchantment.Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(slot.getItem()).entrySet()) {
                                        try {
                                            String enchName = entry.getKey().getRegistryName().getPath().toUpperCase();
                                            enchantments.add(enchName + ";" + entry.getValue());
                                        } catch (Exception e) {
                                            // ignore
                                        }
                                    }
                                }

                                List<String> potionEffects = null;
                                if (slot.getItem().getTag() != null && slot.getItem().getTag().contains("CustomPotionEffects", 9)) {
                                    potionEffects = new ArrayList<>();
                                    ListNBT effectsList = slot.getItem().getTag().getList("CustomPotionEffects", 10);
                                    for (int i = 0; i < effectsList.size(); i++) {
                                        CompoundNBT effectTag = effectsList.getCompound(i);
                                        int effectId = effectTag.getByte("Id") & 0xFF;
                                        int amplifier = effectTag.getByte("Amplifier") + 1;
                                        int duration = effectTag.getInt("Duration");
                                        Effect effect = Effect.byId(effectId);
                                        if (effect != null && effect.getRegistryName() != null) {
                                            String effectName = effect.getRegistryName().getPath().toUpperCase();
                                            potionEffects.add(effectName + ";" + amplifier + ";" + duration);
                                        }
                                    }
                                }

                                MenuItem newItem = new MenuItem(displayName, material, count, modelData, damage, rgb, lore, enchantments, potionEffects, slot.index);

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

                            // --- Фаза 2: записываем сгруппированные предметы ---
                            Map<String, Integer> materialCounters = new HashMap<>();
                            for (MenuItem item : menuItems) {
                                String baseName = item.material.startsWith("basehead-") ? "basehead" : item.material;
                                int cnt = materialCounters.getOrDefault(baseName, 0) + 1;
                                materialCounters.put(baseName, cnt);
                                String key = baseName + "_" + cnt;

                                writer.write("  '" + key + "':\n");
                                writer.write("    display_name: '" + item.displayName + "'\n");

                                writer.write("    slots:\n");
                                for (int s : item.slots) {
                                    writer.write("    - " + s + "\n");
                                }

                                writer.write("    material: " + item.material + "\n");

                                if (item.count != null) {
                                    writer.write("    amount: " + item.count + "\n");
                                }
                                if (item.damage != null) {
                                    writer.write("    data: " + item.damage + "\n");
                                }
                                if (item.modelData != null) {
                                    writer.write("    model_data: " + item.modelData + "\n");
                                }
                                if (item.rgb != null) {
                                    int r = (item.rgb >> 16) & 0xFF;
                                    int g = (item.rgb >> 8) & 0xFF;
                                    int b = item.rgb & 0xFF;
                                    writer.write("    rgb: " + r + ", " + g + ", " + b + "\n");
                                }
                                if (item.lore != null && !item.lore.isEmpty()) {
                                    writer.write("    lore:\n");
                                    for (String line : item.lore) {
                                        writer.write("    - '" + line + "'\n");
                                    }
                                }
                                if (item.enchantments != null && !item.enchantments.isEmpty()) {
                                    writer.write("    hide_enchantments: true\n    enchantments:\n");
                                    for (String ench : item.enchantments) {
                                        writer.write("    - '" + ench + "'\n");
                                    }
                                }
                                if (item.potionEffects != null && !item.potionEffects.isEmpty()) {
                                    writer.write("    hide_effects: true\n    potion_effects:\n");
                                    for (String effect : item.potionEffects) {
                                        writer.write("    - " + effect + "\n");
                                    }
                                }
                                writer.write("    hide_attributes: true\n");
                            }

                            Minecraft.getInstance().player.displayClientMessage(
                                    new StringTextComponent("§aСохранено в файле: Downloaded Menus/" + Minecraft.getInstance().getCurrentServer().ip + "/" + menuFile.getName()),
                                    false
                            );
                        } catch (IOException e) {
                            // ignore error
                        }
                    }
            ));
        }
    }
}
