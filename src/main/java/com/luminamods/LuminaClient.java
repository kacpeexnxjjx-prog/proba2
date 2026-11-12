package com.luminamods;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Random;

public class LuminaClient implements ClientModInitializer {
    private static KeyBinding openKey;
    private static final Random RNG = new Random();
    private static final boolean[] keyDown = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final boolean[] mouseDown = new boolean[3];
    private static final ArrayDeque<Long> lmb = new ArrayDeque<>();
    private static final ArrayDeque<Long> rmb = new ArrayDeque<>();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static boolean wasUsing, hadTotemEffect;
    private static Item useItem;
    private static int useLeft;
    private static float lastHp = -1f;

    @Override
    public void onInitializeClient() {
        LuminaConfig.init();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.luminamods.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.luminamods"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) {
                mc.setScreen(new LuminaScreen());
            }
            poll(mc);
            events(mc);
        });
        HudRenderCallback.EVENT.register(LuminaClient::renderHud);
        WorldRenderEvents.AFTER_ENTITIES.register(LuminaClient::renderHitboxes);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (player == mc.player) play(mc, LuminaConfig.attack, false);
            return ActionResult.PASS;
        });
    }

    // ---------- DZWIEKI ----------

    public static void play(MinecraftClient mc, LuminaConfig.Slot s, boolean randomPitch) {
        if (!s.on || s.vol <= 0f) return;
        playId(mc, s.id, s.vol, randomPitch ? 0.9f + RNG.nextFloat() * 0.2f : 1f);
    }

    public static void playForce(MinecraftClient mc, String id, float vol) {
        playId(mc, id, vol, 1f);
    }

    private static void playId(MinecraftClient mc, String id, float vol, float pitch) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(ident), pitch, vol));
    }

    private static void poll(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        long h = mc.getWindow().getHandle();
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            boolean down = GLFW.glfwGetKey(h, k) == GLFW.GLFW_PRESS;
            if (down && !keyDown[k]) play(mc, LuminaConfig.clicks, true);
            keyDown[k] = down;
        }
        for (int b = 0; b < 3; b++) {
            boolean down = GLFW.glfwGetMouseButton(h, b) == GLFW.GLFW_PRESS;
            if (down && !mouseDown[b]) {
                play(mc, LuminaConfig.clicks, true);
                if (b == 0) lmb.addLast(System.currentTimeMillis());
                if (b == 1) rmb.addLast(System.currentTimeMillis());
            }
            mouseDown[b] = down;
        }
    }

    private static void events(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            lastHp = -1f;
            wasUsing = false;
            hadTotemEffect = false;
            return;
        }
        boolean using = p.isUsingItem();
        if (using) {
            useItem = p.getActiveItem().getItem();
            useLeft = p.getItemUseTimeLeft();
        } else if (wasUsing && useLeft <= 2 && useItem != null) {
            if (useItem == Items.GOLDEN_APPLE) play(mc, LuminaConfig.gapple, false);
            else if (useItem == Items.ENCHANTED_GOLDEN_APPLE) play(mc, LuminaConfig.ench, false);
        }
        wasUsing = using;

        float hp = p.getHealth();
        if (lastHp >= 0f && hp < lastHp - 0.01f && hp > 0f) play(mc, LuminaConfig.hurt, false);
        lastHp = hp;

        StatusEffectInstance reg = p.getStatusEffect(StatusEffects.REGENERATION);
        boolean totemEffect = reg != null && reg.getAmplifier() == 1 && reg.getDuration() >= 860;
        if (totemEffect && !hadTotemEffect) play(mc, LuminaConfig.totem, false);
        hadTotemEffect = totemEffect;
    }

    // ---------- HITBOXY ----------

    private static void renderHitboxes(WorldRenderContext ctx) {
        if (!LuminaConfig.hitboxes) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || ctx.world() == null || ctx.consumers() == null || ctx.matrixStack() == null) return;

        Vec3d cam = ctx.camera().getPos();
        MatrixStack.Entry en = ctx.matrixStack().peek();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLines());
        int rgb = LuminaConfig.hitRgb();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;

        for (Entity e : ctx.world().getEntities()) {
            if (e == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            if (LuminaConfig.hitPlayersOnly && !(e instanceof PlayerEntity)) continue;
            if (e.squaredDistanceTo(cam) > 4096.0) continue;
            Box bb = e.getBoundingBox();
            box(vc, en,
                (float) (bb.minX - cam.x), (float) (bb.minY - cam.y), (float) (bb.minZ - cam.z),
                (float) (bb.maxX - cam.x), (float) (bb.maxY - cam.y), (float) (bb.maxZ - cam.z), r, g, b);
        }
    }

    private static void box(VertexConsumer vc, MatrixStack.Entry en, float x1, float y1, float z1,
                            float x2, float y2, float z2, int r, int g, int b) {
        ln(vc, en, x1, y1, z1, x2, y1, z1, r, g, b);
        ln(vc, en, x2, y1, z1, x2, y1, z2, r, g, b);
        ln(vc, en, x2, y1, z2, x1, y1, z2, r, g, b);
        ln(vc, en, x1, y1, z2, x1, y1, z1, r, g, b);
        ln(vc, en, x1, y2, z1, x2, y2, z1, r, g, b);
        ln(vc, en, x2, y2, z1, x2, y2, z2, r, g, b);
        ln(vc, en, x2, y2, z2, x1, y2, z2, r, g, b);
        ln(vc, en, x1, y2, z2, x1, y2, z1, r, g, b);
        ln(vc, en, x1, y1, z1, x1, y2, z1, r, g, b);
        ln(vc, en, x2, y1, z1, x2, y2, z1, r, g, b);
        ln(vc, en, x2, y1, z2, x2, y2, z2, r, g, b);
        ln(vc, en, x1, y1, z2, x1, y2, z2, r, g, b);
    }

    private static void ln(VertexConsumer vc, MatrixStack.Entry en, float x1, float y1, float z1,
                           float x2, float y2, float z2, int r, int g, int b) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0f) return;
        dx /= len; dy /= len; dz /= len;
        vc.vertex(en, x1, y1, z1).color(r, g, b, 255).normal(en, dx, dy, dz);
        vc.vertex(en, x2, y2, z2).color(r, g, b, 255).normal(en, dx, dy, dz);
    }

    // ---------- HUD ----------

    private static int cps(ArrayDeque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
        return q.size();
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        poll(mc);

        ClientPlayerEntity p = mc.player;
        TextRenderer tr = mc.textRenderer;
        int accent = LuminaConfig.accent();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int y = 4;

        if (LuminaConfig.watermark) { line(ctx, tr, "LuminaMods", 4, y, accent, accent); y += 14; }
        if (LuminaConfig.fps) { line(ctx, tr, "FPS: " + mc.getCurrentFps(), 4, y, accent, 0xFFFFFFFF); y += 14; }
        if (LuminaConfig.coords) {
            line(ctx, tr, "XYZ: " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ(), 4, y, accent, 0xFFFFFFFF);
        }

        if (LuminaConfig.potionHud) {
            int py = 4;
            for (StatusEffectInstance e : p.getStatusEffects()) {
                String name = e.getEffectType().value().getName().getString();
                String time = e.isInfinite() ? "inf" : String.format("%d:%02d", e.getDuration() / 1200, (e.getDuration() / 20) % 60);
                String t = name + " " + (e.getAmplifier() + 1) + "  " + time;
                line(ctx, tr, t, sw - tr.getWidth(t) - 14, py, accent, 0xFFFFFFFF);
                py += 14;
            }
        }
        if (LuminaConfig.armorHud) {
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack st = p.getEquippedStack(ARMOR[i]);
                if (st.isEmpty()) continue;
                int ax = sw - 24, ay = sh / 2 - 36 + i * 18;
                ctx.drawItem(st, ax, ay);
                ctx.drawStackOverlay(tr, st, ax, ay);
            }
        }
        if (LuminaConfig.totemHud) {
            int n = p.getInventory().count(Items.TOTEM_OF_UNDYING);
            int ax = sw - 24, ay = sh / 2 + 40;
            ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), ax, ay);
            ctx.drawTextWithShadow(tr, "x" + n, ax - 2 - tr.getWidth("x" + n), ay + 5, 0xFFFFFFFF);
        }

        if (LuminaConfig.keystrokes) {
            long h = mc.getWindow().getHandle();
            int x = 10;
            int by = sh - 92;
            keyBox(ctx, tr, x + 25, by, 22, 22, "W", mc.options.forwardKey.isPressed(), accent);
            keyBox(ctx, tr, x, by + 25, 22, 22, "A", mc.options.leftKey.isPressed(), accent);
            keyBox(ctx, tr, x + 25, by + 25, 22, 22, "S", mc.options.backKey.isPressed(), accent);
            keyBox(ctx, tr, x + 50, by + 25, 22, 22, "D", mc.options.rightKey.isPressed(), accent);
            keyBox(ctx, tr, x, by + 50, 35, 22, "L " + cps(lmb), GLFW.glfwGetMouseButton(h, 0) == GLFW.GLFW_PRESS, accent);
            keyBox(ctx, tr, x + 37, by + 50, 35, 22, "R " + cps(rmb), GLFW.glfwGetMouseButton(h, 1) == GLFW.GLFW_PRESS, accent);
            keyBox(ctx, tr, x, by + 75, 72, 12, "SPACE", mc.options.jumpKey.isPressed(), accent);
        }
    }

    private static void line(DrawContext ctx, TextRenderer tr, String text, int x, int y, int accent, int textColor) {
        int w = tr.getWidth(text);
        ctx.fill(x, y, x + w + 10, y + 12, 0x90000000);
        ctx.fill(x, y, x + 2, y + 12, accent);
        ctx.drawTextWithShadow(tr, text, x + 6, y + 2, textColor);
    }

    private static void keyBox(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                               String label, boolean down, int accent) {
        int bg = down ? ((accent & 0x00FFFFFF) | 0xC0000000) : 0x90000000;
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + (h - 8) / 2, down ? 0xFF000000 : 0xFFFFFFFF);
    }
}
