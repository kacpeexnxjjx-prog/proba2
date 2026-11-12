package com.luminamods;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class LuminaScreen extends Screen {
    private static final int PW = 560, PH = 340, SB = 112;
    private static final int BG = 0xF2141720, SIDE = 0xF20E1017, ROW = 0xFF1B1F2B, ROW_HOVER = 0xFF232938,
        ROW_SEL = 0xFF262C3E, LINE = 0xFF2B3042, TEXT = 0xFFE8EAF0, MUTED = 0xFF8B90A0;

    private static class Row {
        int x, y, w, h = 20;
        String label = "";
        Supplier<String> value;
        BooleanSupplier toggle;
        DoubleSupplier slider;
        DoubleConsumer sliderSet;
        Runnable left, right, onSwitch;
        boolean selected;
        int swatch = 0;
    }

    private final List<Row> rows = new ArrayList<>();
    private Row dragging;
    private TextFieldWidget field;
    private int tab = 0, selEvent = 0;
    private int x0, y0, cx, cw, top;
    private long copiedUntil = 0;

    public LuminaScreen() {
        super(Text.literal("LuminaMods"));
    }

    private static String tr(String en, String pl) {
        return "pl".equals(LuminaConfig.lang) ? pl : en;
    }

    @Override
    protected void init() {
        x0 = (this.width - PW) / 2;
        y0 = (this.height - PH) / 2;
        cx = x0 + SB + 14;
        cw = PW - SB - 28;
        top = y0 + 38;
        build();
    }

    // ---------- budowanie zakladek ----------

    private Row add(int x, int y, int w, String label) {
        Row r = new Row();
        r.x = x; r.y = y; r.w = w; r.label = label;
        rows.add(r);
        return r;
    }

    private Row toggle(int y, String label, BooleanSupplier g, Consumer<Boolean> s) {
        Row r = add(cx, y, cw, label);
        r.toggle = g;
        r.left = () -> s.accept(!g.getAsBoolean());
        return r;
    }

    private Row slider(int x, int y, int w, String label, double min, double max,
                       DoubleSupplier g, DoubleConsumer s, Supplier<String> text) {
        Row r = add(x, y, w, label);
        r.slider = () -> (g.getAsDouble() - min) / (max - min);
        r.sliderSet = v -> s.accept(min + v * (max - min));
        r.value = text;
        return r;
    }

    private void build() {
        clearChildren();
        rows.clear();
        field = null;
        dragging = null;
        switch (tab) {
            case 0 -> hudTab();
            case 1 -> visualsTab();
            case 2 -> soundsTab();
            default -> profilesTab();
        }
    }

    private void hudTab() {
        int y = top, s = 24;
        toggle(y, tr("Watermark", "Znaczek LuminaMods"), () -> LuminaConfig.watermark, v -> LuminaConfig.watermark = v); y += s;
        toggle(y, "FPS", () -> LuminaConfig.fps, v -> LuminaConfig.fps = v); y += s;
        toggle(y, tr("Coordinates", "Koordynaty"), () -> LuminaConfig.coords, v -> LuminaConfig.coords = v); y += s;
        toggle(y, tr("Keystrokes + CPS", "Klawisze + CPS"), () -> LuminaConfig.keystrokes, v -> LuminaConfig.keystrokes = v); y += s;
        toggle(y, tr("Potion HUD", "HUD mikstur"), () -> LuminaConfig.potionHud, v -> LuminaConfig.potionHud = v); y += s;
        toggle(y, tr("Armor HUD", "HUD pancerza"), () -> LuminaConfig.armorHud, v -> LuminaConfig.armorHud = v); y += s;
        toggle(y, tr("Totem counter", "Licznik totemów"), () -> LuminaConfig.totemHud, v -> LuminaConfig.totemHud = v);
    }

    private void visualsTab() {
        int y = top, s = 24;
        Row h = toggle(y, tr("Custom hitboxes", "Własne hitboxy"), () -> LuminaConfig.hitboxes, v -> LuminaConfig.hitboxes = v);
        h.swatch = 0xFF000000 | LuminaConfig.hitRgb();
        y += s;
        toggle(y, tr("Players only", "Tylko gracze"), () -> LuminaConfig.hitPlayersOnly, v -> LuminaConfig.hitPlayersOnly = v); y += s;
        slider(cx, y, cw, tr("Hitbox red", "Hitbox: czerwony"), 0, 255, () -> LuminaConfig.hitR,
            v -> LuminaConfig.hitR = (int) Math.round(v), () -> String.valueOf(LuminaConfig.hitR)); y += s;
        slider(cx, y, cw, tr("Hitbox green", "Hitbox: zielony"), 0, 255, () -> LuminaConfig.hitG,
            v -> LuminaConfig.hitG = (int) Math.round(v), () -> String.valueOf(LuminaConfig.hitG)); y += s;
        slider(cx, y, cw, tr("Hitbox blue", "Hitbox: niebieski"), 0, 255, () -> LuminaConfig.hitB,
            v -> LuminaConfig.hitB = (int) Math.round(v), () -> String.valueOf(LuminaConfig.hitB)); y += s + 8;
        toggle(y, tr("Rainbow accent", "Tęczowy motyw"), () -> LuminaConfig.rainbow, v -> LuminaConfig.rainbow = v); y += s;
        Row c = add(cx, y, cw, tr("Accent color", "Kolor motywu"));
        c.value = () -> "pl".equals(LuminaConfig.lang) ? LuminaConfig.COLOR_PL[LuminaConfig.colorIndex] : LuminaConfig.COLOR_EN[LuminaConfig.colorIndex];
        c.swatch = 1;
        c.left = () -> LuminaConfig.colorIndex = Math.floorMod(LuminaConfig.colorIndex + 1, LuminaConfig.COLORS.length);
        c.right = () -> LuminaConfig.colorIndex = Math.floorMod(LuminaConfig.colorIndex - 1, LuminaConfig.COLORS.length);
    }

    private String eventLabel(int i) {
        return switch (i) {
            case 0 -> tr("Key & mouse clicks", "Klikanie klawiszy");
            case 1 -> tr("Golden apple (ref)", "Złote jabłko (ref)");
            case 2 -> tr("Ench. apple (koxy)", "Zakl. jabłko (koxy)");
            case 3 -> tr("Totem pop (you)", "Totem (ty)");
            case 4 -> tr("You hit something", "Twoje uderzenie");
            default -> tr("You take damage", "Otrzymane obrażenia");
        };
    }

    private void soundsTab() {
        int y = top, s = 24;
        for (int i = 0; i < LuminaConfig.SLOTS.length; i++) {
            final int idx = i;
            Row r = add(cx, y, 190, eventLabel(i));
            r.toggle = () -> LuminaConfig.SLOTS[idx].on;
            r.onSwitch = () -> LuminaConfig.SLOTS[idx].on = !LuminaConfig.SLOTS[idx].on;
            r.selected = idx == selEvent;
            r.left = () -> { selEvent = idx; build(); };
            y += s;
        }
        int dx = cx + 200, dw = cw - 200;
        LuminaConfig.Slot sel = LuminaConfig.SLOTS[selEvent];

        Row snd = add(dx, top, dw, tr("Sound", "Dźwięk"));
        snd.value = () -> soundName(sel.id);
        snd.left = () -> cycleSound(1);
        snd.right = () -> cycleSound(-1);

        slider(dx, top + s, dw, tr("Volume", "Głośność"), 0, 1, () -> sel.vol,
            v -> sel.vol = (float) v, () -> Math.round(sel.vol * 100) + "%");

        Row test = add(dx, top + 2 * s, dw, tr("Play preview", "Odtwórz podgląd"));
        test.left = () -> LuminaClient.playForce(MinecraftClient.getInstance(), sel.id, sel.vol);

        field = new TextFieldWidget(this.textRenderer, dx, top + 3 * s + 14, dw, 18, Text.empty());
        field.setMaxLength(100);
        field.setPlaceholder(Text.literal(tr("sound id, e.g. creamykeys:click", "id dźwięku, np. creamykeys:click")));
        field.setText(sel.id);
        field.setChangedListener(t -> {
            String v = t.trim();
            if (!v.isEmpty() && Identifier.tryParse(v) != null) sel.id = v;
        });
        addDrawableChild(field);
    }

    private static int presetIndex(String id) {
        for (int i = 0; i < LuminaConfig.SOUND_IDS.length; i++) {
            if (LuminaConfig.SOUND_IDS[i].equals(id)) return i;
        }
        return -1;
    }

    private static String soundName(String id) {
        int i = presetIndex(id);
        return i >= 0 ? LuminaConfig.SOUND_NAMES[i] : (id.length() > 18 ? id.substring(0, 17) + "…" : id);
    }

    private void cycleSound(int d) {
        LuminaConfig.Slot s = LuminaConfig.SLOTS[selEvent];
        int n = LuminaConfig.SOUND_IDS.length;
        int i = presetIndex(s.id);
        i = i < 0 ? (d > 0 ? 0 : n - 1) : Math.floorMod(i + d, n);
        s.id = LuminaConfig.SOUND_IDS[i];
        if (field != null) field.setText(s.id);
        LuminaClient.playForce(MinecraftClient.getInstance(), s.id, s.vol);
    }

    private void profilesTab() {
        int s = 24;
        List<String> ps = LuminaConfig.profiles();
        int y = top;
        for (int i = 0; i < ps.size() && i < 6; i++) {
            final String name = ps.get(i);
            Row r = add(cx, y, cw - 68, name);
            r.selected = name.equals(LuminaConfig.active);
            r.left = () -> {
                if (!name.equals(LuminaConfig.active)) {
                    LuminaConfig.save();
                    LuminaConfig.load(name);
                }
                build();
            };
            Row d = add(cx + cw - 64, y, 64, tr("Delete", "Usuń"));
            d.left = () -> { LuminaConfig.delete(name); build(); };
            y += s;
        }
        y = top + 6 * s + 6;
        field = new TextFieldWidget(this.textRenderer, cx, y, cw - 110, 20, Text.empty());
        field.setMaxLength(24);
        field.setPlaceholder(Text.literal(tr("New profile name", "Nazwa nowego profilu")));
        addDrawableChild(field);
        Row create = add(cx + cw - 104, y, 104, tr("Create", "Utwórz"));
        create.left = () -> {
            LuminaConfig.save();
            if (LuminaConfig.create(field.getText())) build();
        };
        Row lang = add(cx, y + s + 6, cw, tr("Language", "Język"));
        lang.value = () -> "pl".equals(LuminaConfig.lang) ? "Polski" : "English";
        lang.left = () -> {
            LuminaConfig.lang = "pl".equals(LuminaConfig.lang) ? "en" : "pl";
            LuminaConfig.saveLang();
            build();
        };
        lang.right = lang.left;
    }

    // ---------- rysowanie ----------

    private static String[] tabNames() {
        return new String[]{"HUD", tr("Visuals", "Wizualne"), tr("Sounds", "Dźwięki"), tr("Profiles", "Profile")};
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        int accent = LuminaConfig.accent();

        ctx.fill(x0, y0, x0 + PW, y0 + PH, BG);
        ctx.fill(x0, y0, x0 + SB, y0 + PH, SIDE);
        ctx.fill(x0, y0, x0 + PW, y0 + 2, accent);
        ctx.fill(x0 + SB, y0 + 2, x0 + SB + 1, y0 + PH, LINE);

        ctx.drawTextWithShadow(this.textRenderer, "LuminaMods", x0 + 12, y0 + 12, accent);
        String[] names = tabNames();
        for (int i = 0; i < names.length; i++) {
            int ty = y0 + 36 + i * 24;
            boolean hov = mx >= x0 + 8 && mx < x0 + SB - 8 && my >= ty && my < ty + 20;
            if (i == tab) {
                ctx.fill(x0 + 8, ty, x0 + SB - 8, ty + 20, ROW_SEL);
                ctx.fill(x0 + 8, ty, x0 + 10, ty + 20, accent);
            } else if (hov) {
                ctx.fill(x0 + 8, ty, x0 + SB - 8, ty + 20, ROW);
            }
            ctx.drawTextWithShadow(this.textRenderer, names[i], x0 + 18, ty + 6, i == tab ? 0xFFFFFFFF : MUTED);
        }
        ctx.drawTextWithShadow(this.textRenderer, "by k4cperts", x0 + 12, y0 + PH - 26, MUTED);
        boolean copied = System.currentTimeMillis() < copiedUntil;
        ctx.drawTextWithShadow(this.textRenderer, copied ? tr("Copied!", "Skopiowano!") : "dc.gg/luminamods",
            x0 + 12, y0 + PH - 15, copied ? 0xFF55FF55 : accent);

        ctx.drawTextWithShadow(this.textRenderer, names[tab], cx, y0 + 14, 0xFFFFFFFF);
        ctx.fill(cx, y0 + 28, cx + cw, y0 + 29, LINE);

        for (Row r : rows) drawRow(ctx, r, mx, my, accent);

        if (tab == 2) {
            ctx.drawTextWithShadow(this.textRenderer, tr("Custom sound ID", "Własne ID dźwięku"), cx + 200, top + 72 + 2, MUTED);
            ctx.drawTextWithShadow(this.textRenderer, tr("Works with resource-pack sounds", "Działa z dźwiękami z resource packa"),
                cx + 200, top + 72 + 40, MUTED);
        }
        if (tab == 3) {
            ctx.drawTextWithShadow(this.textRenderer, tr("New profile copies current settings.", "Nowy profil kopiuje obecne ustawienia."),
                cx, top + 6 * 24 + 6 + 24 + 6 + 28, MUTED);
        }
    }

    private int sliderX(Row r) {
        return r.x + r.w - 8 - 90;
    }

    private void drawRow(DrawContext ctx, Row r, int mx, int my, int accent) {
        boolean hov = mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h;
        ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, r.selected ? ROW_SEL : hov ? ROW_HOVER : ROW);
        if (r.selected) ctx.fill(r.x, r.y, r.x + 2, r.y + r.h, accent);
        int ty = r.y + (r.h - 8) / 2;
        ctx.drawTextWithShadow(this.textRenderer, r.label, r.x + 8, ty, TEXT);
        int rx = r.x + r.w - 8;

        if (r.toggle != null) {
            boolean on = r.toggle.getAsBoolean();
            ctx.fill(rx - 26, r.y + 5, rx, r.y + 15, on ? accent : LINE);
            int kx = on ? rx - 12 : rx - 24;
            ctx.fill(kx, r.y + 6, kx + 10, r.y + 14, on ? 0xFF0E1017 : MUTED);
            rx -= 34;
        }
        if (r.swatch != 0) {
            int col = r.swatch == 1 ? LuminaConfig.COLORS[LuminaConfig.colorIndex] : r.swatch;
            if (r.value != null) {
                // swatch po prawej, tekst obok
                ctx.fill(rx - 10, r.y + 5, rx, r.y + 15, col);
                rx -= 16;
            } else {
                ctx.fill(rx - 10, r.y + 5, rx, r.y + 15, col);
                rx -= 16;
            }
        }
        if (r.slider != null) {
            int sw = 90, sx = sliderX(r);
            double v = Math.max(0, Math.min(1, r.slider.getAsDouble()));
            ctx.fill(sx, r.y + 9, sx + sw, r.y + 11, LINE);
            int fw = (int) (sw * v);
            ctx.fill(sx, r.y + 9, sx + fw, r.y + 11, accent);
            ctx.fill(sx + fw - 2, r.y + 6, sx + fw + 2, r.y + 14, 0xFFFFFFFF);
            if (r.value != null) {
                String t = r.value.get();
                ctx.drawTextWithShadow(this.textRenderer, t, sx - 8 - this.textRenderer.getWidth(t), ty, MUTED);
            }
        } else if (r.value != null) {
            String t = r.value.get();
            int tw = this.textRenderer.getWidth(t);
            ctx.fill(rx - tw - 12, r.y + 3, rx, r.y + r.h - 3, LINE);
            ctx.drawTextWithShadow(this.textRenderer, t, rx - tw - 6, ty, 0xFFFFFFFF);
        }
    }

    // ---------- myszka ----------

    private void setSlider(Row r, double mx) {
        double v = Math.max(0, Math.min(1, (mx - sliderX(r)) / 90.0));
        r.sliderSet.accept(v);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int i = 0; i < 4; i++) {
            int ty = y0 + 36 + i * 24;
            if (mx >= x0 + 8 && mx < x0 + SB - 8 && my >= ty && my < ty + 20) {
                tab = i;
                build();
                return true;
            }
        }
        if (mx >= x0 + 8 && mx < x0 + SB - 8 && my >= y0 + PH - 17 && my < y0 + PH - 5) {
            MinecraftClient.getInstance().keyboard.setClipboard("https://dc.gg/luminamods");
            copiedUntil = System.currentTimeMillis() + 1500;
            return true;
        }
        for (Row r : new ArrayList<>(rows)) {
            if (mx < r.x || mx >= r.x + r.w || my < r.y || my >= r.y + r.h) continue;
            if (r.slider != null) {
                dragging = r;
                setSlider(r, mx);
                return true;
            }
            if (r.onSwitch != null && mx >= r.x + r.w - 42) {
                r.onSwitch.run();
                return true;
            }
            Runnable a = (btn == 1 && r.right != null) ? r.right : r.left;
            if ((btn == 0 || btn == 1) && a != null) a.run();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging != null) {
            setSlider(dragging, mx);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = null;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void removed() {
        LuminaConfig.save();
    }
}
