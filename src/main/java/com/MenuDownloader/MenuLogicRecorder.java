package com.MenuDownloader;

import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CClickWindowPacket;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.network.play.client.CCloseWindowPacket;
import net.minecraft.network.play.server.SChatPacket;
import net.minecraft.network.play.server.SCloseWindowPacket;
import net.minecraft.network.play.server.SOpenWindowPacket;

public class MenuLogicRecorder {

    private static final int EV_CLICK = 0;
    private static final int EV_OPEN = 1;
    private static final int EV_CLOSE = 2;
    private static final int EV_CHAT = 4;
    private static final int EV_GUI_OPEN = 6;
    private static final int EV_GUI_OTHER = 7;
    private static final int EV_GUI_CLOSE = 8;
    private static final int EV_PCLOSE = 9;
    private static final int EV_CMD = 10;

    private static final long CORRELATION_WINDOW_MS = 2000;
    private static final long CLOSE_ECHO_SUPPRESS_MS = 1000;
    private static final int CAPTURE_TICKS = 40;
    private static final int FEEDBACK_LIMIT = 3;
    private static final int MENU_NAME_MAX_LEN = 24;

    private static final class RecEvent {
        final long time;
        final int kind;
        final int containerId;
        final int slot;
        final String click;
        final String text;

        RecEvent(long time, int kind, int containerId, int slot, String click, String text) {
            this.time = time;
            this.kind = kind;
            this.containerId = containerId;
            this.slot = slot;
            this.click = click;
            this.text = text;
        }
    }

    private static final class MenuSnapshot {
        final String stateKey;
        final int containerId;
        final String titleString;
        String titleFormatted;
        int size;
        List<MenuItem> items = new ArrayList<>();
        boolean captured;
        int openTicks;

        MenuSnapshot(String stateKey, int containerId, String titleString) {
            this.stateKey = stateKey;
            this.containerId = containerId;
            this.titleString = titleString;
        }
    }

    private static final class ClickResult {
        String openKey;
        String otherTitle;
        final List<String> feedback = new ArrayList<>();
        String type = "none";
        boolean playerClosed;
    }

    private static final class SlotAgg {
        final Map<String, ClickResult> byClick = new LinkedHashMap<>();
    }

    public static final class SlotActions {
        public final Map<String, String> commands = new LinkedHashMap<>();
        public final List<String> comments = new ArrayList<>();

        static String signature(SlotActions a) {
            if (a == null || (a.commands.isEmpty() && a.comments.isEmpty())) {
                return "-";
            }
            return a.commands.toString() + "|" + a.comments.toString();
        }
    }

    private final ConcurrentLinkedQueue<RecEvent> events = new ConcurrentLinkedQueue<>();
    private final Map<String, MenuSnapshot> snapshots = new LinkedHashMap<>();
    private final String serverDirName;
    private volatile boolean recording = true;

    public MenuLogicRecorder(String serverDirName) {
        this.serverDirName = serverDirName;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String key(int containerId, String title) {
        return containerId + "|" + (title == null ? "" : title);
    }

    public void onPacket(boolean outbound, IPacket<?> packet) {
        if (!recording) {
            return;
        }
        try {
            if (outbound) {
                if (packet instanceof CClickWindowPacket) {
                    CClickWindowPacket p = (CClickWindowPacket) packet;
                    int slot = p.getSlotNum();
                    if (slot >= 0) {
                        events.add(new RecEvent(now(), EV_CLICK, p.getContainerId(), slot,
                                clickName(p.getClickType(), p.getButtonNum()), null));
                    }
                } else if (packet instanceof CCloseWindowPacket) {
                    events.add(new RecEvent(now(), EV_PCLOSE, -1, -1, null, null));
                } else if (packet instanceof CChatMessagePacket) {
                    String text = ((CChatMessagePacket) packet).getMessage();
                    if (text != null && text.startsWith("/")) {
                        events.add(new RecEvent(now(), EV_CMD, -1, -1, null, text.trim()));
                    }
                }
            } else {
                if (packet instanceof SOpenWindowPacket) {
                    SOpenWindowPacket p = (SOpenWindowPacket) packet;
                    events.add(new RecEvent(now(), EV_OPEN, p.getContainerId(), -1, null, p.getTitle().getString()));
                } else if (packet instanceof SCloseWindowPacket) {
                    events.add(new RecEvent(now(), EV_CLOSE, -1, -1, null, null));
                } else if (packet instanceof SChatPacket) {
                    String text = ((SChatPacket) packet).getMessage().getString();
                    if (text != null && !text.trim().isEmpty()) {
                        events.add(new RecEvent(now(), EV_CHAT, -1, -1, null, text.trim()));
                    }
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    public static String clickName(net.minecraft.inventory.container.ClickType type, int button) {
        switch (type) {
            case PICKUP:
                return button == 0 ? "LEFT" : "RIGHT";
            case QUICK_MOVE:
                return button == 0 ? "SHIFT_LEFT" : "SHIFT_RIGHT";
            case SWAP:
                return "HOTBAR_" + button;
            case CLONE:
                return "MIDDLE";
            default:
                return type.name();
        }
    }

    public void onGuiOpen(Screen screen) {
        if (!recording) {
            return;
        }
        if (screen instanceof ChestScreen) {
            ChestScreen chest = (ChestScreen) screen;
            int containerId = chest.getMenu().containerId;
            String title = chest.getTitle().getString();
            events.add(new RecEvent(now(), EV_GUI_OPEN, containerId, -1, null, title));
            touchSnapshot(containerId, title, chest);
        } else if (screen != null && !(screen instanceof ChatScreen)
                && !screen.getTitle().getString().trim().isEmpty()) {
            events.add(new RecEvent(now(), EV_GUI_OTHER, -1, -1, null, screen.getTitle().getString()));
        } else {
            events.add(new RecEvent(now(), EV_GUI_CLOSE, -1, -1, null, null));
        }
    }

    public void onClientTick(Minecraft minecraft) {
        if (recording && minecraft.screen instanceof ChestScreen) {
            ChestScreen chest = (ChestScreen) minecraft.screen;
            MenuSnapshot snapshot = touchSnapshot(chest.getMenu().containerId, chest.getTitle().getString(), chest);
            snapshot.openTicks++;
            captureIfNeeded(snapshot, chest);
        }
    }

    private MenuSnapshot touchSnapshot(int containerId, String title, ChestScreen chest) {
        String k = key(containerId, title);
        MenuSnapshot s = snapshots.get(k);
        if (s == null) {
            s = new MenuSnapshot(k, containerId, title);
            snapshots.put(k, s);
        }
        return s;
    }

    private void captureIfNeeded(MenuSnapshot snapshot, ChestScreen chest) {
        if (snapshot.captured) {
            return;
        }
        if (snapshot.openTicks > CAPTURE_TICKS || anyChestItem(chest)) {
            snapshot.size = chest.getMenu().slots.size() - 36;
            snapshot.items = Main.collectMenuItems(chest, snapshot.size);
            snapshot.titleFormatted = MenuDownload.formatText(chest.getTitle());
            snapshot.captured = true;
        }
    }

    private static boolean anyChestItem(ChestScreen chest) {
        int chestSize = chest.getMenu().slots.size() - 36;
        for (int i = 0; i < chestSize && i < chest.getMenu().slots.size(); i++) {
            if (!chest.getMenu().slots.get(i).getItem().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public File save() {
        recording = false;
        List<RecEvent> list = new ArrayList<>(events);
        list.sort((a, b) -> Long.compare(a.time, b.time));

        LinkedHashMap<String, MenuSnapshot> openStates = new LinkedHashMap<>();
        for (RecEvent e : list) {
            if (e.kind != EV_OPEN && e.kind != EV_GUI_OPEN) {
                continue;
            }
            String k = key(e.containerId, e.text);
            if (!openStates.containsKey(k)) {
                MenuSnapshot s = snapshots.get(k);
                openStates.put(k, s != null ? s : new MenuSnapshot(k, e.containerId, e.text));
            }
        }
        if (openStates.isEmpty()) {
            return null;
        }

        LinkedHashMap<String, MenuSnapshot> states = new LinkedHashMap<>();
        Map<String, String> openKeyToMenuId = new HashMap<>();
        for (Map.Entry<String, MenuSnapshot> en : openStates.entrySet()) {
            MenuSnapshot s = en.getValue();
            String menuId = s.titleString;
            MenuSnapshot existing = states.get(menuId);
            if (existing == null || fullerSnapshot(s, existing)) {
                states.put(menuId, s);
            }
            openKeyToMenuId.put(en.getKey(), menuId);
        }

        Map<String, String> menuNames = new LinkedHashMap<>();
        Set<String> usedNames = new HashSet<>();
        for (Map.Entry<String, MenuSnapshot> en : states.entrySet()) {
            menuNames.put(en.getKey(), menuNameFor(en.getValue().titleString, usedNames));
        }

        Map<String, String> menuCommands = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            RecEvent e = list.get(i);
            if (e.kind != EV_CMD) {
                continue;
            }
            String cmd = sanitizeCommand(e.text.substring(1));
            if (cmd == null) {
                continue;
            }
            long windowEnd = e.time + CORRELATION_WINDOW_MS;
            for (int j = i + 1; j < list.size() && list.get(j).time <= windowEnd; j++) {
                RecEvent o = list.get(j);
                if (o.kind == EV_OPEN || o.kind == EV_GUI_OPEN) {
                    String menuId = openKeyToMenuId.get(key(o.containerId, o.text));
                    if (menuId != null) {
                        menuCommands.putIfAbsent(menuId, cmd);
                    }
                    break;
                }
            }
        }

        boolean[] serverClose = new boolean[list.size()];
        long lastPlayerClose = -1;
        for (int i = 0; i < list.size(); i++) {
            RecEvent e = list.get(i);
            if (e.kind == EV_PCLOSE) {
                lastPlayerClose = e.time;
            } else if (e.kind == EV_CLOSE) {
                serverClose[i] = lastPlayerClose < 0 || e.time - lastPlayerClose > CLOSE_ECHO_SUPPRESS_MS;
            }
        }

        Map<String, Map<Integer, SlotAgg>> byMenu = new LinkedHashMap<>();
        List<Object> transitionsJson = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            RecEvent click = list.get(i);
            if (click.kind != EV_CLICK) {
                continue;
            }
            String fromKey = currentMenuAt(list, i);
            if (fromKey == null) {
                continue;
            }
            String fromId = openKeyToMenuId.get(fromKey);
            if (fromId == null) {
                continue;
            }
            ClickResult res = new ClickResult();
            boolean sawServerClose = false;
            boolean sawPlayerClose = false;
            long windowEnd = click.time + CORRELATION_WINDOW_MS;
            for (int j = i + 1; j < list.size() && list.get(j).time <= windowEnd; j++) {
                RecEvent e = list.get(j);
                if (e.kind == EV_CLICK) {
                    break;
                }
                if (e.kind == EV_CLOSE) {
                    if (serverClose[j]) {
                        sawServerClose = true;
                    }
                    continue;
                }
                if (e.kind == EV_PCLOSE || e.kind == EV_GUI_CLOSE) {
                    sawPlayerClose = true;
                }
                if (sawPlayerClose) {
                    continue;
                }
                if (e.kind == EV_OPEN || e.kind == EV_GUI_OPEN) {
                    String k = key(e.containerId, e.text);
                    if (!k.equals(fromKey) && res.openKey == null) {
                        res.openKey = k;
                    }
                } else if (e.kind == EV_GUI_OTHER) {
                    if (res.otherTitle == null) {
                        res.otherTitle = e.text;
                    }
                } else if (e.kind == EV_CHAT) {
                    if (res.feedback.size() < FEEDBACK_LIMIT) {
                        res.feedback.add(e.text);
                    }
                }
            }
            if (res.openKey != null) {
                res.type = "open";
            } else if (res.otherTitle != null) {
                res.type = "other_screen";
            } else if (sawServerClose) {
                res.type = "close";
            } else if (!res.feedback.isEmpty()) {
                res.type = "feedback";
            } else {
                res.playerClosed = sawPlayerClose;
            }

            Map<Integer, SlotAgg> slotMap = byMenu.get(fromId);
            if (slotMap == null) {
                slotMap = new HashMap<>();
                byMenu.put(fromId, slotMap);
            }
            SlotAgg agg = slotMap.get(click.slot);
            if (agg == null) {
                agg = new SlotAgg();
                slotMap.put(click.slot, agg);
            }
            agg.byClick.put(click.click, res);
            if (res.openKey != null) {
                String toId = openKeyToMenuId.get(res.openKey);
                if (toId != null) {
                    res.openKey = toId;
                    if (toId.equals(fromId) && sawServerClose) {
                        res.type = "close";
                        res.openKey = null;
                    }
                }
            }

            Map<String, Object> tj = new LinkedHashMap<>();
            tj.put("menu", menuNames.get(fromId));
            tj.put("slot", click.slot);
            tj.put("click", click.click);
            Map<String, Object> rj = new LinkedHashMap<>();
            rj.put("type", res.type);
            if (res.openKey != null) {
                rj.put("to_menu", menuNames.get(res.openKey));
            }
            if (res.otherTitle != null) {
                rj.put("other_screen", res.otherTitle);
            }
            if (!res.feedback.isEmpty()) {
                rj.put("feedback", res.feedback);
            }
            if (res.playerClosed && "none".equals(res.type)) {
                rj.put("player_closed", true);
            }
            tj.put("result", rj);
            transitionsJson.add(tj);
        }

        Map<String, Object> decorative = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, SlotAgg>> menuEntry : byMenu.entrySet()) {
            List<Integer> deadSlots = new ArrayList<>();
            for (Map.Entry<Integer, SlotAgg> slotEntry : menuEntry.getValue().entrySet()) {
                boolean allDead = true;
                for (ClickResult r : slotEntry.getValue().byClick.values()) {
                    if (!"none".equals(r.type)) {
                        allDead = false;
                        break;
                    }
                }
                if (allDead) {
                    deadSlots.add(slotEntry.getKey());
                }
            }
            if (!deadSlots.isEmpty()) {
                decorative.put(menuNames.get(menuEntry.getKey()), deadSlots);
            }
        }

        List<Object> menusJson = new ArrayList<>();
        for (Map.Entry<String, MenuSnapshot> en : states.entrySet()) {
            MenuSnapshot s = en.getValue();
            Map<String, Object> mj = new LinkedHashMap<>();
            mj.put("name", menuNames.get(en.getKey()));
            mj.put("title", s.titleString);
            mj.put("container_id", s.containerId);
            mj.put("size", s.size);
            mj.put("items_captured", s.captured);
            Map<String, Object> slotsJson = new LinkedHashMap<>();
            if (s.captured) {
                for (MenuItem mi : s.items) {
                    Map<String, Object> ij = new LinkedHashMap<>();
                    ij.put("material", mi.material);
                    ij.put("name", mi.displayName);
                    for (int slot : mi.slots) {
                        slotsJson.put(String.valueOf(slot), ij);
                    }
                }
            }
            mj.put("slots", slotsJson);
            menusJson.add(mj);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("server", serverDirName);
        root.put("saved_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        root.put("menus", menusJson);
        root.put("transitions", transitionsJson);
        root.put("decorative_slots", decorative);

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd_HH-mm-ss"));
        File dir = new File(Main.serverDirFor(serverDirName), "logic-" + stamp);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }

        List<Object[]> pendingFiles = new ArrayList<>();
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
        pendingFiles.add(new Object[]{new File(dir, "logic_graph.json"), json});

        for (Map.Entry<String, MenuSnapshot> en : states.entrySet()) {
            MenuSnapshot s = en.getValue();
            String name = menuNames.get(en.getKey());
            File f = new File(dir, name + ".yml");
            try {
                StringWriter sw = new StringWriter();
                BufferedWriter w = new BufferedWriter(sw);
                w.write("#By DecentMCDev\n#Реконструкция логики меню (запись /menulogic)\n");
                if (!s.captured) {
                    w.write("# Это меню было замечено в записи, но его содержимое не успело сохраниться\nmenu_title: " + Main.yamlQuote(s.titleString) + "\nitems: {}\n");
                } else {
                    w.write("menu_title: " + Main.yamlQuote(s.titleFormatted != null ? s.titleFormatted : s.titleString) + "\n");
                    String openCmd = menuCommands.get(en.getKey());
                    if (openCmd == null) {
                        openCmd = commandFromTitle(s.titleString);
                    }
                    if (openCmd == null) {
                        String slug = titleSlug(s.titleString);
                        if (slug.replaceAll("[^a-z]", "").length() >= 3) {
                            openCmd = sanitizeCommand(slug);
                        }
                    }
                    if (openCmd != null) {
                        w.write("open_command:\n  - " + openCmd + "\nregister_command: true\n");
                    }
                    w.write("size: " + s.size + "\nitems:\n");
                    Map<Integer, SlotActions> actions = buildSlotActions(en.getKey(), menuNames, byMenu);
                    Main.writeItemsSection(w, s.items, actions);
                }
                w.flush();
                pendingFiles.add(new Object[]{f, sw.toString()});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Thread diskWriter = new Thread(() -> {
            for (Object[] p : pendingFiles) {
                try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream((File) p[0]), StandardCharsets.UTF_8))) {
                    w.write((String) p[1]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "MenuDownloader-Save");
        diskWriter.setDaemon(true);
        diskWriter.start();

        return dir;
    }

    private static boolean fullerSnapshot(MenuSnapshot candidate, MenuSnapshot current) {
        if (!current.captured) {
            return candidate.captured;
        }
        return candidate.captured && candidate.items.size() > current.items.size();
    }

    private static String titleSlug(String title) {
        String base = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return base.replaceAll("[^a-z0-9_\\-]", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
    }

    private static String menuNameFor(String title, Set<String> used) {
        String base = titleSlug(title);
        if (base.isEmpty()) {
            base = "menu";
        }
        if (base.length() > MENU_NAME_MAX_LEN) {
            base = base.substring(0, MENU_NAME_MAX_LEN);
        }
        String name = base;
        int i = 2;
        while (used.contains(name)) {
            name = base + "_" + i;
            i++;
        }
        used.add(name);
        return name;
    }

    private static String sanitizeCommand(String cmd) {
        if (cmd == null) {
            return null;
        }
        cmd = cmd.trim().toLowerCase(Locale.ROOT).split("\\s+")[0];
        if (!cmd.matches("[a-z0-9_\\-]{2,32}") || cmd.equals("menulogic")) {
            return null;
        }
        return cmd;
    }

    private static String commandFromTitle(String title) {
        if (title == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/([A-Za-z0-9_\\-]+)").matcher(title);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return sanitizeCommand(last);
    }

    private static String currentMenuAt(List<RecEvent> list, int i) {
        RecEvent click = list.get(i);
        for (int j = i - 1; j >= 0; j--) {
            RecEvent e = list.get(j);
            if (e.kind == EV_GUI_OPEN && e.containerId == click.containerId) {
                return key(e.containerId, e.text);
            }
        }
        return null;
    }

    private Map<Integer, SlotActions> buildSlotActions(String menuId,
                                                       Map<String, String> menuNames,
                                                       Map<String, Map<Integer, SlotAgg>> byMenu) {
        Map<Integer, SlotAgg> slotMap = byMenu.getOrDefault(menuId, Collections.emptyMap());
        Map<Integer, SlotActions> out = new HashMap<>();
        String selfName = menuNames.get(menuId);
        for (Map.Entry<Integer, SlotAgg> e : slotMap.entrySet()) {
            SlotActions sa = new SlotActions();
            for (Map.Entry<String, ClickResult> c : e.getValue().byClick.entrySet()) {
                String click = c.getKey();
                ClickResult r = c.getValue();
                String section = sectionFor(click);
                if ("open".equals(r.type)) {
                    String toName = menuNames.get(r.openKey);
                    if (toName != null && !toName.equals(selfName) && section != null) {
                        sa.commands.put(section, "[openguimenu] " + toName);
                    } else {
                        sa.comments.add("# " + click + ": то же меню перезапустилось (возможно, пагинация)");
                    }
                } else if ("close".equals(r.type)) {
                    if (section != null) {
                        sa.commands.put(section, "[close]");
                        if (!r.feedback.isEmpty()) {
                            sa.comments.add("# " + click + ": ответ сервера: " + joinFeedback(r.feedback));
                        }
                    } else {
                        sa.comments.add("# " + click + ": меню закрывается (вероятно [close])");
                    }
                } else if ("feedback".equals(r.type)) {
                    sa.comments.add("# " + click + ": неизвестная команда; ответ сервера: " + joinFeedback(r.feedback));
                } else if ("other_screen".equals(r.type)) {
                    String extra = r.feedback.isEmpty() ? "" : " (ответ: " + joinFeedback(r.feedback) + ")";
                    sa.comments.add("# " + click + ": открыт другой экран: " + oneLine(r.otherTitle) + extra);
                } else {
                    sa.comments.add("# " + click + (r.playerClosed
                            ? ": меню закрыто игроком — реакции на клик не видно"
                            : ": видимой реакции нет (вероятно декорация)"));
                }
            }
            out.put(e.getKey(), sa);
        }
        return out;
    }

    private static String sectionFor(String click) {
        switch (click) {
            case "LEFT":
                return "left_click_commands";
            case "RIGHT":
                return "right_click_commands";
            case "SHIFT_LEFT":
                return "shift_left_click_commands";
            case "SHIFT_RIGHT":
                return "shift_right_click_commands";
            default:
                return null;
        }
    }

    private static String joinFeedback(List<String> feedback) {
        StringBuilder sb = new StringBuilder();
        for (String s : feedback) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(oneLine(s));
            if (sb.length() > 200) {
                break;
            }
        }
        return sb.toString();
    }

    private static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        String result = s.replaceAll("[\\r\\n]", " ").trim();
        return result.length() > 120 ? result.substring(0, 120) : result;
    }
}
