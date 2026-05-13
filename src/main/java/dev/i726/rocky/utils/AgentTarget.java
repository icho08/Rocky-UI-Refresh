package dev.i726.rocky.utils;

import dev.i726.rocky.Main;
import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.vape.RockyGui;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.client.SelfDestruct;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

public final class AgentTarget {

    private static final Map<Integer, Boolean> KEY_STATES = new HashMap<>();
    private static int bridgeCooldown = 0;

    public static void init(String args, Instrumentation inst) {
        System.out.println("[Rocky] Agent target reached. Initializing Hyper-Trigger v4.0...");

        new Thread(() -> {
            try {
                int attempts = 0;
                while (attempts < 100) {
                    if (SelfDestruct.destruct) return;
                    try {
                        if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getWindow() != null) break;
                    } catch (Throwable ignored) {}
                    Thread.sleep(500);
                    attempts++;
                }

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || SelfDestruct.destruct) return;

                mc.execute(() -> {
                    try {
                        if (Rocky.INSTANCE == null && !SelfDestruct.destruct) {
                            new Main().onInitializeClient();
                            System.out.println("[Rocky] Hyper-Trigger Engine v4.0 Active.");
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                });

                startInputLoop();
            } catch (Throwable e) { e.printStackTrace(); }
        }, "Rocky-Bootstrap").start();
    }

    private static void startInputLoop() {
        new Thread(() -> {
            try {
                while (!SelfDestruct.destruct) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null && mc.getWindow() != null && Rocky.INSTANCE != null && Rocky.INSTANCE.getModuleManager() != null) {
                        long handle = mc.getWindow().getHandle();

                        // 1. GUI TOGGLE
                        boolean rshift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
                        if (rshift && !KEY_STATES.getOrDefault(GLFW.GLFW_KEY_RIGHT_SHIFT, false)) {
                            mc.execute(() -> {
                                if (!SelfDestruct.destruct && Rocky.INSTANCE != null) {
                                    if (mc.currentScreen instanceof RockyGui) mc.setScreen(null);
                                    else mc.setScreen(Rocky.INSTANCE.getRockyGui());
                                }
                            });
                        }
                        KEY_STATES.put(GLFW.GLFW_KEY_RIGHT_SHIFT, rshift);

                        // 2. MODULE TOGGLES
                        if (mc.player != null && mc.currentScreen == null) {
                            for (Module module : Rocky.INSTANCE.getModuleManager().getModules()) {
                                int key = module.getKey();
                                if (key <= 0 || key == GLFW.GLFW_KEY_RIGHT_SHIFT) continue;

                                boolean isDown = GLFW.glfwGetKey(handle, key) == 1;
                                boolean wasDown = KEY_STATES.getOrDefault(key, false);

                                if (isDown && !wasDown) {
                                    module.toggle();
                                }
                                KEY_STATES.put(key, isDown);
                            }
                        }

                        // 3. HYPER-ENGINE
                        if (mc.player != null) {
                            runHyperEngine(mc);
                        }
                    }
                    Thread.sleep(10);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }, "Rocky-HyperEngine").start();
    }

    private static void runHyperEngine(MinecraftClient mc) {
        // SPRINT
        Module sprint = Rocky.INSTANCE.getModuleManager().getModuleByName("Sprint");
        if (sprint != null && sprint.isEnabled()) {
            if (mc.player.input != null && mc.player.input.playerInput.forward()) {
                mc.player.setSprinting(true);
            }
        }

        // TRIGGERBOT (Manual Raycast Implementation)
        Module triggerBot = Rocky.INSTANCE.getModuleManager().getModuleByName("TriggerBot");
        if (triggerBot != null && triggerBot.isEnabled()) {
            Entity target = manualRaycast(mc, 3.0); // 3 blocks reach
            if (target != null && target.isAlive() && mc.player.getAttackCooldownProgress(0.0f) >= 0.95f) {
                mc.execute(() -> {
                    if (mc.interactionManager != null && !SelfDestruct.destruct) {
                        mc.interactionManager.attackEntity(mc.player, target);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                });
            }
        }

        // SMART BRIDGE
        Module smartBridge = Rocky.INSTANCE.getModuleManager().getModuleByName("Smart Bridge");
        if (smartBridge != null && smartBridge.isEnabled()) {
            if (bridgeCooldown > 0) bridgeCooldown--;
            
            net.minecraft.util.math.BlockPos below = net.minecraft.util.math.BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
            if (mc.world.getBlockState(below).isAir() && mc.player.isOnGround()) {
                mc.options.sneakKey.setPressed(true);
                if (bridgeCooldown <= 0 && mc.player.getMainHandStack().getItem() instanceof BlockItem) {
                    mc.execute(() -> {
                        if (mc.interactionManager != null && mc.crosshairTarget instanceof BlockHitResult bhr) {
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                            mc.player.swingHand(Hand.MAIN_HAND);
                            bridgeCooldown = 3;
                        }
                    });
                }
            } else if (mc.options.sneakKey.isPressed() && GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) != 1) {
                mc.options.sneakKey.setPressed(false);
            }
        }

        // VELOCITY
        Module velocity = Rocky.INSTANCE.getModuleManager().getModuleByName("Velocity");
        if (velocity != null && velocity.isEnabled()) {
            if (mc.player.hurtTime > 0) {
                mc.player.setVelocity(mc.player.getVelocity().x * 0.1, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.1);
            }
        }
    }

    private static Entity manualRaycast(MinecraftClient mc, double reach) {
        Entity player = mc.player;
        if (player == null || mc.world == null) return null;

        Vec3d start = player.getEyePos();
        Vec3d direction = player.getRotationVec(1.0F);
        Vec3d end = start.add(direction.x * reach, direction.y * reach, direction.z * reach);
        Box box = player.getBoundingBox().stretch(direction.multiply(reach)).expand(1.0, 1.0, 1.0);
        
        EntityHitResult hit = ProjectileUtil.raycast(player, start, end, box, (entity) -> !entity.isSpectator() && entity.canHit(), reach * reach);
        
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            return hit.getEntity();
        }
        return null;
    }
}
