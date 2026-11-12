package com.luminamods;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public class LuminaConfig {
    public static class Slot {
        public boolean on;
        public String id;
        public float vol;
        Slot(boolean on, String id, float vol) { this.on = on; this.id = id; this.vol = vol; }
    }

    public static String lang = "en";
    public static boolean watermark = true, fps = true, coords = false, keystrokes = true,
        potionHud = true, armorHud = true, totemHud = false,
        hitboxes = false, hitPlayersOnly = true, rainbow = true;
    public static int colorIndex = 0, hitR = 255, hitG = 60, hitB = 60;

    public static final Slot clicks = new Slot(true, "block.note_block.hat", 0.5f);
    public static final Slot gapple = new Slot(false, "entity.experience_orb.pickup", 0.8f);
    public static final Slot ench = new Slot(false, "block.note_block.bell", 0.8f);
    public static final Slot totem = new Slot(false, "block.anvil.place", 0.6f);
    public static final Slot attack = new Slot(false, "entity.arrow.hit_player", 0.6f);
    public static final Slot hurt = new Slot(false, "block.note_block.bass", 0.6f);
    public static final Slot[] SLOTS = {clicks, gapple, ench, totem, attack, hurt};
    private static final String[] SLOT_KEYS = {"clicks", "gapple", "ench", "totem", "attack", "hurt"};

    public static final String[] SOUND_IDS = {
        "ui.button.click", "block.note_block.hat", "block.note_block.bass", "block.stone_button.click_on",
        "block.wooden_button.click_on", "entity.experience_orb.pickup", "entity.player.levelup",
        "block.anvil.place", "block.note_block.bell", "entity.arrow.hit_player", "block.amethyst_block.chime"
    };
    public static final String[] SOUND_NAMES = {
        "UI Click", "Hi-hat", "Bass", "Stone", "Wood", "Orb", "Level up", "Anvil", "Bell", "Ding", "Chime"
    };
    public static final String[] COLOR_EN = {"Cyan", "Pink", "Green", "Orange", "Purple", "Red"};
    public static final String[] COLOR_PL = {"Cyjan", "Różowy", "Zielony", "Pomarańczowy", "Fioletowy", "Czerwony"};
    public static final int[] COLORS = {0xFF55FFFF, 0xFFFF55FF, 0xFF55FF55, 0xFFFFAA00, 0xFFAA55FF, 0xFFFF5555};

    public static String active = "default";

    public static int accent() {
        if (rainbow) {
            float h = (System.currentTimeMillis() % 4000L) / 4000f;
            return 0xFF000000 | MathHelper.hsvToRgb(h, 0.6f, 1f);
        }
        return COLORS[colorIndex];
    }

    public static int hitRgb() {
        return (hitR << 16) | (hitG << 8) | hitB;
    }

    // ---------- pliki ----------
    private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("luminamods"); }
    private static Path profilesDir() { return dir().resolve("profiles"); }
    private static Path profileFile(String n) { return profilesDir().resolve(n + ".properties"); }

    public static String sanitize(String n) {
        String s = n == null ? "" : n.trim().replaceAll("[^a-zA-Z0-9_-]", "");
        return s.length() > 24 ? s.substring(0, 24) : s;
    }

    public static void init() {
        try {
            Files.createDirectories(profilesDir());
            Path l = dir().resolve("lang.txt");
            if (Files.exists(l)) lang = Files.readString(l).trim().equals("pl") ? "pl" : "en";
            Path a = dir().resolve("active.txt");
            String n = Files.exists(a) ? sanitize(Files.readString(a)) : "default";
            if (n.isEmpty()) n = "default";
            if (Files.exists(profileFile(n))) load(n);
            else { active = n; save(); }
        } catch (Exception ignored) { }
    }

    public static void saveLang() {
        try { Files.writeString(dir().resolve("lang.txt"), lang); } catch (Exception ignored) { }
    }

    public static void save() {
        try {
            Files.createDirectories(profilesDir());
            try (OutputStream out = Files.newOutputStream(profileFile(active))) { snapshot().store(out, "LuminaMods profile"); }
            Files.writeString(dir().resolve("active.txt"), active);
        } catch (Exception ignored) { }
    }

    public static void load(String name) {
        try {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(profileFile(name))) { p.load(in); }
            apply(p);
            active = name;
            Files.writeString(dir().resolve("active.txt"), active);
        } catch (Exception ignored) { }
    }

    public static List<String> profiles() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(profilesDir())) {
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.endsWith(".properties")) out.add(n.substring(0, n.length() - 11));
            });
        } catch (Exception ignored) { }
        Collections.sort(out);
        if (out.isEmpty()) out.add("default");
        return out;
    }

    /** Nowy profil = kopia obecnych ustawien. */
    public static boolean create(String raw) {
        String n = sanitize(raw);
        if (n.isEmpty() || Files.exists(profileFile(n))) return false;
        active = n;
        save();
        return true;
    }

    public static void delete(String name) {
        if (profiles().size() <= 1) return;
        try { Files.deleteIfExists(profileFile(name)); } catch (Exception ignored) { }
        if (name.equals(active)) {
            load(profiles().get(0));
        }
    }

    private static Properties snapshot() {
        Properties p = new Properties();
        p.setProperty("watermark", String.valueOf(watermark));
        p.setProperty("fps", String.valueOf(fps));
        p.setProperty("coords", String.valueOf(coords));
        p.setProperty("keystrokes", String.valueOf(keystrokes));
        p.setProperty("potionHud", String.valueOf(potionHud));
        p.setProperty("armorHud", String.valueOf(armorHud));
        p.setProperty("totemHud", String.valueOf(totemHud));
        p.setProperty("hitboxes", String.valueOf(hitboxes));
        p.setProperty("hitPlayersOnly", String.valueOf(hitPlayersOnly));
        p.setProperty("rainbow", String.valueOf(rainbow));
        p.setProperty("colorIndex", String.valueOf(colorIndex));
        p.setProperty("hitR", String.valueOf(hitR));
        p.setProperty("hitG", String.valueOf(hitG));
        p.setProperty("hitB", String.valueOf(hitB));
        for (int i = 0; i < SLOTS.length; i++) {
            p.setProperty(SLOT_KEYS[i] + ".on", String.valueOf(SLOTS[i].on));
            p.setProperty(SLOT_KEYS[i] + ".id", SLOTS[i].id);
            p.setProperty(SLOT_KEYS[i] + ".vol", String.valueOf(SLOTS[i].vol));
        }
        return p;
    }

    private static void apply(Properties p) {
        watermark = gb(p, "watermark", true);
        fps = gb(p, "fps", true);
        coords = gb(p, "coords", false);
        keystrokes = gb(p, "keystrokes", true);
        potionHud = gb(p, "potionHud", true);
        armorHud = gb(p, "armorHud", true);
        totemHud = gb(p, "totemHud", false);
        hitboxes = gb(p, "hitboxes", false);
        hitPlayersOnly = gb(p, "hitPlayersOnly", true);
        rainbow = gb(p, "rainbow", true);
        colorIndex = Math.floorMod(gi(p, "colorIndex", 0), COLORS.length);
        hitR = Math.max(0, Math.min(255, gi(p, "hitR", 255)));
        hitG = Math.max(0, Math.min(255, gi(p, "hitG", 60)));
        hitB = Math.max(0, Math.min(255, gi(p, "hitB", 60)));
        for (int i = 0; i < SLOTS.length; i++) {
            Slot s = SLOTS[i];
            s.on = gb(p, SLOT_KEYS[i] + ".on", s.on);
            s.id = p.getProperty(SLOT_KEYS[i] + ".id", s.id);
            s.vol = (float) Math.max(0, Math.min(1, gf(p, SLOT_KEYS[i] + ".vol", s.vol)));
        }
    }

    private static boolean gb(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v);
    }

    private static int gi(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k, String.valueOf(d))); } catch (Exception e) { return d; }
    }

    private static double gf(Properties p, String k, double d) {
        try { return Double.parseDouble(p.getProperty(k, String.valueOf(d))); } catch (Exception e) { return d; }
    }
}
