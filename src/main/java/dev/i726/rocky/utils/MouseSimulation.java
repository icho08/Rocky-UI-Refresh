package dev.i726.rocky.utils;

import dev.i726.rocky.mixin.MinecraftClientAccessor;
import dev.i726.rocky.mixin.MouseHandlerAccessor;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.input.MouseButtonInfo;

import static dev.i726.rocky.Rocky.mc;


public final class MouseSimulation {
	public static HashMap<Integer, Boolean> mouseButtons = new HashMap<>();
	public static ExecutorService clickExecutor = Executors.newFixedThreadPool(100);

	public static MouseHandlerAccessor getMouseHandler() {
		return (MouseHandlerAccessor) ((MinecraftClientAccessor) mc).getMouseHandler();
	}

	public static boolean isMouseButtonPressed(int keyCode) {
		Boolean key = mouseButtons.get(keyCode);
		return key != null ? key : false;
	}

	public static void mousePress(int keyCode) {
		mouseButtons.put(keyCode, true);
		getMouseHandler().press(mc.getWindow().handle(), new MouseButtonInfo(keyCode, 0), GLFW.GLFW_PRESS);
	}

	public static void mouseRelease(int keyCode) {
		getMouseHandler().press(mc.getWindow().handle(), new MouseButtonInfo(keyCode, 0), GLFW.GLFW_RELEASE);
	}

	public static void mouseClick(int keyCode, int millis) {
		clickExecutor.submit(() -> {
			try {
				MouseSimulation.mousePress(keyCode);
				Thread.sleep(millis);
				MouseSimulation.mouseRelease(keyCode);
			} catch (InterruptedException ignored) {

			}
		});
	}

	public static void mouseClick(int keyCode) {
		mouseClick(keyCode, 35);
	}
}
