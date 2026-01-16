package com.MenuDownloader;

import com.MenuDownloader.Logger;
import com.MenuDownloader.MenuDownload;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(value = "downloadmenus")
public class Main {
    private static String openCommand = "error";

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
                    new StringTextComponent("Ñêà÷àòü Ìåíþ"),
                    button -> {
                        if (Minecraft.getInstance().getCurrentServer() == null) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    new StringTextComponent("§cÑêà÷èâàíèå ìåíþ äîñòóïíî òîëüêî íà ñåðâåðå"),
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
                                new StringTextComponent("§aÃîòîâî!"),
                                false
                        );

                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(menuFile, false))) {
                            writer.write("#By DecentMCDev\n#Ñïàñèáî çà ñêà÷èâàíèå\nmenu_title: '" + MenuDownload.formatText(chestScreen.getTitle()) + "'\nopen_command: \n  - " + openCommand + "\nsize: " + (((ChestContainer) chestScreen.getMenu()).slots.size() - 37) + "\nregister_command: true\nitems:\n");
                            Iterator<Slot> slotIterator = ((ChestContainer) chestScreen.getMenu()).slots.iterator();
                            while (slotIterator.hasNext()) {
                                Slot slot = slotIterator.next();
                                if (!slot.getItem().isEmpty() && slot.index < ((ChestContainer) chestScreen.getMenu()).slots.size() - 36) {
                                    writer.write("  '" + slot.index + "':\n    display_name: '" + MenuDownload.formatText(slot.getItem().getHoverName()) + "'\n    slot: " + slot.index + "\n");

                                    if (slot.getItem().getTag() != null && slot.getItem().getTag().toString().contains("Properties:{textures:[{Value:\"")) {
                                        String texture = slot.getItem().getTag().toString().split("Properties:\\{textures:\\[\\{Value:\"")[1].split("\"}]}}")[0];
                                        writer.write("    material: basehead-" + texture + "\n");
                                    } else {
                                        String material = slot.getItem().getItem().getRegistryName().getPath();
                                        writer.write("    material: " + material + "\n");
                                    }

                                    if (slot.getItem().getCount() > 1) {
                                        writer.write("    amount: " + slot.getItem().getCount() + "\n");
                                    }

                                    if (slot.getItem().getTag() != null && slot.getItem().getTag().contains("CustomModelData")) {
                                        writer.write("    model_data: " + slot.getItem().getTag().getInt("CustomModelData") + "\n");
                                    }

                                    if (slot.getItem().hasTag() && 
                                        slot.getItem().getTag().contains("display", 10) &&
                                        slot.getItem().getTag().getCompound("display").contains("Lore", 9)) {

                                        writer.write("    lore:\n");
                                        for (int i = 0; i < slot.getItem().getTag().getCompound("display").getList("Lore", 8).size(); i++) {
                                            String loreLineJson = slot.getItem().getTag().getCompound("display").getList("Lore", 8).getString(i);
                                            if (loreLineJson != null && !loreLineJson.isEmpty()) {
                                                try {
                                                    ITextComponent loreComponent = ITextComponent.Serializer.fromJson(loreLineJson);
                                                    if (loreComponent != null) {
                                                        String formattedLore = MenuDownload.formatText(loreComponent);
                                                        formattedLore = formattedLore.replace("'", "''");
                                                        writer.write("    - '" + formattedLore + "'\n");
                                                    }
                                                } catch (Exception e) {
                                                    String simpleLore = loreLineJson.replace("§", "&");
                                                    simpleLore = simpleLore.replace("'", "''");
                                                    writer.write("    - '" + simpleLore + "'\n");
                                                }
                                            }
                                        }
                                    }

                                    if (!EnchantmentHelper.getEnchantments(slot.getItem()).isEmpty()) {
                                        writer.write("    hide_enchantments: true\n    enchantments:\n");
                                        EnchantmentHelper.getEnchantments(slot.getItem()).forEach((enchantment, level) -> {
                                            try {
                                                String enchantName = enchantment.getRegistryName().getPath().toUpperCase();
                                                writer.write("    - " + enchantName + ";" + level + "\n");
                                            } catch (Exception e) {
                                                // ignore error
                                            }
                                        });
                                    }

                                    if (slot.getItem().getTag() != null && slot.getItem().getTag().contains("CustomPotionEffects", 9)) {
                                        writer.write("    hide_effects: true\n    potion_effects:\n");
                                        writer.write("    - FIRE_RESISTANCE;1;1\n");
                                    }

                                    writer.write("    hide_attributes: true\n");
                                }
                            }

                            Minecraft.getInstance().player.displayClientMessage(
                                    new StringTextComponent("§aÑêà÷àííî â ïàïêó: Downloaded Menus/" + Minecraft.getInstance().getCurrentServer().ip + "/" + menuFile.getName()),
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
