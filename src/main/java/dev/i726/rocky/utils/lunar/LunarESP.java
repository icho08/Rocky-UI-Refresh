package dev.i726.rocky.utils.lunar;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 2-D ESP overlay for Lunar Client (Mojang-mapped Minecraft).
 *
 * Since Mixins are unavailable in Lunar, we schedule a render task onto the
 * Minecraft main thread each frame via mc.execute().  On the main thread we:
 *   1.  Enumerate nearby AbstractClientPlayers via reflection
 *   2.  Project their bounding boxes from world-space to screen-space using a
 *       manually reconstructed MVP matrix (JOML, which Minecraft bundles)
 *   3.  Draw 2-D line boxes using Blaze3D's BufferBuilder via reflection
 *
 * All class / method names are Mojang-mapped so they match Lunar Client.
 */
public final class LunarESP {

    // ── Enable flag ───────────────────────────────────────────────────────────
    public static volatile boolean enabled       = false;
    public static volatile int     espColor      = 0xFF_FF4444; // ARGB red
    public static volatile int     teamColor     = 0xFF_44FF44; // ARGB green for self
    public static volatile boolean showSelf      = false;
    public static volatile float   lineWidth     = 1.2f;

    // ── Cached reflection handles ─────────────────────────────────────────────
    private static ClassLoader loader;

    // Minecraft
    private static Method MC_EXECUTE;
    private static Method MC_GET_WINDOW;
    private static Field  MC_GAME_RENDERER;
    private static Field  MC_PLAYER;
    private static Field  MC_LEVEL;
    private static Method MC_OPTIONS_FOV;    // may be a field or option value

    // Window
    private static Method WIN_WIDTH, WIN_HEIGHT;

    // GameRenderer -> Camera
    private static Method GR_GET_CAMERA;   // getMainCamera()
    private static Method CAM_GET_POS;     // getPosition()  -> Vec3
    private static Method CAM_X_ROT;       // getXRot()      -> float (pitch)
    private static Method CAM_Y_ROT;       // getYRot()      -> float (yaw)

    // Vec3
    private static Method VEC3_X, VEC3_Y, VEC3_Z;

    // ClientLevel (world) -> players
    private static Method LEVEL_PLAYERS;   // players() -> List<AbstractClientPlayer>

    // Entity -> position + AABB
    private static Method ENT_GET_X, ENT_GET_Y, ENT_GET_Z;
    private static Method ENT_GET_BB;      // getBoundingBox() -> AABB
    private static Field  ENT_UUID;

    // AABB
    private static Field AABB_MIN_X, AABB_MIN_Y, AABB_MIN_Z;
    private static Field AABB_MAX_X, AABB_MAX_Y, AABB_MAX_Z;

    // Blaze3D rendering
    private static Class<?> CLS_RENDER_SYS;
    private static Method   RS_ENABLE_BLEND, RS_DISABLE_BLEND;
    private static Method   RS_DEFAULT_BLEND, RS_LINE_WIDTH;

    private static Class<?> CLS_TESS;
    private static Method   TESS_GET_INST;
    private static Method   TESS_BEGIN;    // BufferBuilder begin(VertexFormat.Mode, VertexFormat)

    private static Class<?> CLS_VERTEX_FMT_MODE;
    private static Object   VFM_DEBUG_LINES; // VertexFormat.Mode.DEBUG_LINES

    private static Class<?> CLS_DEF_VERTEX_FMT;
    private static Object   DVF_POSITION_COLOR; // DefaultVertexFormat.POSITION_COLOR

    private static Class<?> CLS_BUF_BUILDER;
    private static Method   BB_ADD_VERTEX;     // addVertex(x,y,z) -> VertexConsumer
    private static Method   VC_SET_COLOR;      // setColor(r,g,b,a) -> VertexConsumer
    private static Method   BB_BUILD_OR_THROW; // buildOrThrow() -> MeshData

    private static Class<?> CLS_BUF_UPLOADER;
    private static Method   BU_DRAW_WITH_SHADER;

    private static Class<?> CLS_MATRIX_STACK;
    private static Method   RS_SET_PROJ;
    private static Method   RS_GET_PROJ;

    // Options FOV
    private static Field  OPTS_FOV;        // field "fov" on Options, which is an OptionInstance

    // ── Init ──────────────────────────────────────────────────────────────────

    public static boolean init(ClassLoader gameLoader) {
        loader = gameLoader;
        try {
            Class<?> mcCls = Class.forName("net.minecraft.client.Minecraft", true, loader);
            MC_EXECUTE       = findMethod(mcCls, "execute", Runnable.class);
            MC_GET_WINDOW    = findMethod(mcCls, "getWindow");
            MC_GAME_RENDERER = findField(mcCls,  "gameRenderer");
            MC_PLAYER        = findField(mcCls,  "player");
            MC_LEVEL         = findField(mcCls,  "level");

            Class<?> winCls  = Class.forName("com.mojang.blaze3d.platform.Window", true, loader);
            WIN_WIDTH        = findMethod(winCls, "getScreenWidth",  "getWidth");
            WIN_HEIGHT       = findMethod(winCls, "getScreenHeight", "getHeight");

            Class<?> grCls   = Class.forName("net.minecraft.client.renderer.GameRenderer", true, loader);
            GR_GET_CAMERA    = findMethod(grCls, "getMainCamera");

            Class<?> camCls  = Class.forName("net.minecraft.client.Camera", true, loader);
            CAM_GET_POS      = findMethod(camCls, "getPosition");
            CAM_X_ROT        = findMethod(camCls, "getXRot");
            CAM_Y_ROT        = findMethod(camCls, "getYRot");

            Class<?> vec3Cls = Class.forName("net.minecraft.world.phys.Vec3", true, loader);
            VEC3_X = findMethod(vec3Cls, "x"); VEC3_Y = findMethod(vec3Cls, "y");
            VEC3_Z = findMethod(vec3Cls, "z");

            Class<?> levelCls = Class.forName("net.minecraft.client.multiplayer.ClientLevel", true, loader);
            LEVEL_PLAYERS = findMethod(levelCls, "players");

            Class<?> entCls  = Class.forName("net.minecraft.world.entity.Entity", true, loader);
            ENT_GET_X = findMethod(entCls, "getX");
            ENT_GET_Y = findMethod(entCls, "getY");
            ENT_GET_Z = findMethod(entCls, "getZ");
            ENT_GET_BB = findMethod(entCls, "getBoundingBox");
            ENT_UUID   = findField(entCls, "uuid");

            Class<?> aabbCls = Class.forName("net.minecraft.world.phys.AABB", true, loader);
            AABB_MIN_X = findField(aabbCls, "minX"); AABB_MAX_X = findField(aabbCls, "maxX");
            AABB_MIN_Y = findField(aabbCls, "minY"); AABB_MAX_Y = findField(aabbCls, "maxY");
            AABB_MIN_Z = findField(aabbCls, "minZ"); AABB_MAX_Z = findField(aabbCls, "maxZ");

            // Options FOV (OptionInstance<Integer>)
            Class<?> optsCls = Class.forName("net.minecraft.client.Options", true, loader);
            OPTS_FOV = findField(optsCls, "fov");

            // Blaze3D
            CLS_RENDER_SYS       = Class.forName("com.mojang.blaze3d.systems.RenderSystem", true, loader);
            RS_ENABLE_BLEND      = findMethod(CLS_RENDER_SYS, "enableBlend");
            RS_DISABLE_BLEND     = findMethod(CLS_RENDER_SYS, "disableBlend");
            RS_DEFAULT_BLEND     = findMethod(CLS_RENDER_SYS, "defaultBlendFunc");
            RS_LINE_WIDTH        = findMethod(CLS_RENDER_SYS, "lineWidth");
            RS_GET_PROJ          = findMethod(CLS_RENDER_SYS, "getProjectionMatrix");
            RS_SET_PROJ          = findMethodWithParams(CLS_RENDER_SYS, "setProjectionMatrix");

            CLS_TESS       = Class.forName("com.mojang.blaze3d.vertex.Tesselator", true, loader);
            TESS_GET_INST  = findMethod(CLS_TESS, "getInstance");

            CLS_VERTEX_FMT_MODE = Class.forName("com.mojang.blaze3d.vertex.VertexFormat$Mode", true, loader);
            for (Object o : CLS_VERTEX_FMT_MODE.getEnumConstants()) {
                if (o.toString().equals("DEBUG_LINES")) { VFM_DEBUG_LINES = o; break; }
            }
            // Fallback: use LINES if DEBUG_LINES not present
            if (VFM_DEBUG_LINES == null) {
                for (Object o : CLS_VERTEX_FMT_MODE.getEnumConstants()) {
                    if (o.toString().equals("LINES")) { VFM_DEBUG_LINES = o; break; }
                }
            }

            CLS_DEF_VERTEX_FMT  = Class.forName("com.mojang.blaze3d.vertex.DefaultVertexFormat", true, loader);
            DVF_POSITION_COLOR  = CLS_DEF_VERTEX_FMT.getField("POSITION_COLOR").get(null);

            CLS_BUF_BUILDER     = Class.forName("com.mojang.blaze3d.vertex.BufferBuilder", true, loader);
            TESS_BEGIN          = findMethod(CLS_TESS, "begin");
            BB_BUILD_OR_THROW   = findMethod(CLS_BUF_BUILDER, "buildOrThrow", "build");
            BB_ADD_VERTEX       = findMethod(CLS_BUF_BUILDER, "addVertex", "vertex");
            // setColor is on the returned VertexConsumer
            VC_SET_COLOR        = null; // resolved lazily per-vertex

            CLS_BUF_UPLOADER    = Class.forName("com.mojang.blaze3d.vertex.BufferUploader", true, loader);
            BU_DRAW_WITH_SHADER = findMethod(CLS_BUF_UPLOADER, "drawWithShader", "draw");

            System.out.println("[Rocky/Lunar] ESP renderer initialised.");
            return true;
        } catch (Throwable t) {
            System.err.println("[Rocky/Lunar] ESP init failed: " + t);
            return false;
        }
    }

    // ── Render scheduling ─────────────────────────────────────────────────────

    /**
     * Schedule a render tick on the Minecraft main thread.
     * Called every ~16 ms from the LunarCompat feature loop.
     */
    public static void scheduleFrame(Object mc) {
        if (!enabled) return;
        try {
            MC_EXECUTE.invoke(mc, (Runnable) () -> {
                try { renderFrame(mc); } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    // ── Frame rendering ───────────────────────────────────────────────────────

    private static void renderFrame(Object mc) throws Throwable {
        Object level  = MC_LEVEL.get(mc);
        Object player = MC_PLAYER.get(mc);
        if (level == null || player == null) return;

        // Get window dimensions
        Object win = MC_GET_WINDOW.invoke(mc);
        int sw = ((Number) WIN_WIDTH.invoke(win)).intValue();
        int sh = ((Number) WIN_HEIGHT.invoke(win)).intValue();
        if (sw <= 0 || sh <= 0) return;

        // Build MVP matrix from camera state
        Object gr     = MC_GAME_RENDERER.get(mc);
        Object cam    = GR_GET_CAMERA.invoke(gr);
        Object camPos = CAM_GET_POS.invoke(cam);
        double cx = (double) VEC3_X.invoke(camPos);
        double cy = (double) VEC3_Y.invoke(camPos);
        double cz = (double) VEC3_Z.invoke(camPos);
        float  pitch  = (float) CAM_X_ROT.invoke(cam);
        float  yaw    = (float) CAM_Y_ROT.invoke(cam);
        float  fov    = getFov(mc);

        Matrix4f mvp  = buildMVP(pitch, yaw, fov, (float) sw / sh);

        // Collect nearby players
        java.util.List<?> players = (java.util.List<?>) LEVEL_PLAYERS.invoke(level);

        // Setup GL state
        RS_ENABLE_BLEND.invoke(null);
        RS_DEFAULT_BLEND.invoke(null);
        if (RS_LINE_WIDTH != null) {
            try { RS_LINE_WIDTH.invoke(null, lineWidth); } catch (Throwable ignored) {}
        }

        // Save & set orthographic projection for 2D drawing
        Object savedProj = RS_GET_PROJ.invoke(null);
        Matrix4f ortho = new Matrix4f().ortho(0, sw, sh, 0, -1, 1);
        if (RS_SET_PROJ != null) {
            try { RS_SET_PROJ.invoke(null, ortho); } catch (Throwable ignored) {}
        }

        // Get local player UUID to skip self
        java.util.UUID selfUUID = ENT_UUID != null
                ? (java.util.UUID) ENT_UUID.get(player) : null;

        // Draw boxes for each player
        for (Object p : players) {
            if (!showSelf && selfUUID != null) {
                java.util.UUID pUUID = ENT_UUID != null
                        ? (java.util.UUID) ENT_UUID.get(p) : null;
                if (selfUUID.equals(pUUID)) continue;
            }

            Object aabb = ENT_GET_BB.invoke(p);
            double minX = (double) AABB_MIN_X.get(aabb) - cx;
            double minY = (double) AABB_MIN_Y.get(aabb) - cy;
            double minZ = (double) AABB_MIN_Z.get(aabb) - cz;
            double maxX = (double) AABB_MAX_X.get(aabb) - cx;
            double maxY = (double) AABB_MAX_Y.get(aabb) - cy;
            double maxZ = (double) AABB_MAX_Z.get(aabb) - cz;

            // Project the 8 corners and get the 2D bounding rect
            float[] screen2D = project2DBounds(mvp, sw, sh,
                    minX, minY, minZ, maxX, maxY, maxZ);
            if (screen2D == null) continue; // behind camera

            drawBox2D(screen2D[0], screen2D[1], screen2D[2], screen2D[3],
                    espColor & 0xFFFFFF, (espColor >>> 24) & 0xFF);
        }

        // Restore projection
        if (RS_SET_PROJ != null && savedProj != null) {
            try { RS_SET_PROJ.invoke(null, savedProj); } catch (Throwable ignored) {}
        }
        RS_DISABLE_BLEND.invoke(null);
    }

    // ── 3D → 2D projection ────────────────────────────────────────────────────

    /**
     * Builds a View*Projection matrix from raw camera angles and FOV.
     * No Minecraft API needed — pure JOML.
     */
    private static Matrix4f buildMVP(float pitchDeg, float yawDeg, float fovDeg, float aspect) {
        Matrix4f proj = new Matrix4f().perspective(
                (float) Math.toRadians(fovDeg), aspect, 0.05f, 1024f);
        Matrix4f view = new Matrix4f()
                .rotateX((float) Math.toRadians(pitchDeg))
                .rotateY((float) Math.toRadians(yawDeg + 180f));
        return proj.mul(view);
    }

    /**
     * Projects the 8 corners of a world-space bounding box (already offset by
     * camera pos) and returns the 2D screen-space AABB as [x1, y1, x2, y2].
     * Returns null if the box is entirely behind the camera.
     */
    private static float[] project2DBounds(Matrix4f mvp, int sw, int sh,
            double mnX, double mnY, double mnZ, double mxX, double mxY, double mxZ) {
        float sMinX = Float.MAX_VALUE, sMinY = Float.MAX_VALUE;
        float sMaxX = -Float.MAX_VALUE, sMaxY = -Float.MAX_VALUE;
        int visible = 0;

        double[] xs = {mnX, mxX};
        double[] ys = {mnY, mxY};
        double[] zs = {mnZ, mxZ};

        for (double wx : xs) {
            for (double wy : ys) {
                for (double wz : zs) {
                    Vector4f clip = mvp.transform(
                            new Vector4f((float) wx, (float) wy, (float) wz, 1f));
                    if (clip.w <= 0.001f) continue; // behind near-plane
                    float nx = clip.x / clip.w;
                    float ny = clip.y / clip.w;
                    if (nx < -1.5f || nx > 1.5f || ny < -1.5f || ny > 1.5f) continue;
                    float sx = (nx + 1f) * 0.5f * sw;
                    float sy = (1f - ny) * 0.5f * sh;
                    if (sx < sMinX) sMinX = sx; if (sx > sMaxX) sMaxX = sx;
                    if (sy < sMinY) sMinY = sy; if (sy > sMaxY) sMaxY = sy;
                    visible++;
                }
            }
        }
        if (visible == 0) return null;

        // Clamp to screen
        sMinX = Math.max(0, sMinX); sMinY = Math.max(0, sMinY);
        sMaxX = Math.min(sw, sMaxX); sMaxY = Math.min(sh, sMaxY);
        if (sMaxX - sMinX < 1f || sMaxY - sMinY < 1f) return null;

        return new float[]{sMinX, sMinY, sMaxX, sMaxY};
    }

    // ── 2D draw call ──────────────────────────────────────────────────────────

    private static void drawBox2D(float x1, float y1, float x2, float y2, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        try {
            Object bb = TESS_BEGIN.invoke(
                    TESS_GET_INST.invoke(null), VFM_DEBUG_LINES, DVF_POSITION_COLOR);

            // 4 edges of a 2D box
            addLine(bb, x1, y1, x2, y1, r, g, b, alpha);
            addLine(bb, x2, y1, x2, y2, r, g, b, alpha);
            addLine(bb, x2, y2, x1, y2, r, g, b, alpha);
            addLine(bb, x1, y2, x1, y1, r, g, b, alpha);

            Object mesh = BB_BUILD_OR_THROW.invoke(bb);
            BU_DRAW_WITH_SHADER.invoke(null, mesh);
        } catch (Throwable ignored) {}
    }

    /** Adds two vertices (a line segment) to a BufferBuilder. */
    private static void addLine(Object bb,
            float x1, float y1, float x2, float y2,
            int r, int g, int b, int a) throws Throwable {
        addVertex(bb, x1, y1, r, g, b, a);
        addVertex(bb, x2, y2, r, g, b, a);
    }

    private static void addVertex(Object bb, float x, float y,
            int r, int g, int b, int a) throws Throwable {
        // addVertex(x, y, z) returns a VertexConsumer — chain setColor on it
        if (BB_ADD_VERTEX != null) {
            Object vc = BB_ADD_VERTEX.invoke(bb, x, y, 0f);
            // Resolve setColor lazily
            if (VC_SET_COLOR == null && vc != null) {
                try {
                    VC_SET_COLOR = vc.getClass().getMethod("setColor", int.class, int.class, int.class, int.class);
                    VC_SET_COLOR.setAccessible(true);
                } catch (Throwable ignored2) {}
            }
            if (VC_SET_COLOR != null && vc != null) {
                VC_SET_COLOR.invoke(vc, r, g, b, a);
            }
        }
    }

    // ── FOV helper ────────────────────────────────────────────────────────────

    private static float getFov(Object mc) {
        try {
            // Options.fov is an OptionInstance<Integer>; call .get() on it
            Object opts = mc.getClass().getMethod("getOptions").invoke(mc);
            if (opts == null) {
                opts = mc.getClass().getField("options").get(mc);
            }
            Object fovOpt = OPTS_FOV.get(opts);
            // OptionInstance.get() returns the value
            Method getVal = fovOpt.getClass().getMethod("get");
            Object val = getVal.invoke(fovOpt);
            return val instanceof Number ? ((Number) val).floatValue() : 70f;
        } catch (Throwable ignored) {
            return 70f;
        }
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private static Method findMethod(Class<?> cls, String... names) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (String name : names) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name)) { m.setAccessible(true); return m; }
                }
            }
        }
        System.err.println("[Rocky/ESP] Method not found: " + cls.getSimpleName()
                + "#" + names[0]);
        return null;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?> paramType) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name, paramType); m.setAccessible(true); return m; }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static Method findMethodWithParams(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) { m.setAccessible(true); return m; }
            }
        }
        return null;
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        }
        System.err.println("[Rocky/ESP] Field not found: " + cls.getSimpleName() + "#" + name);
        return null;
    }
}
