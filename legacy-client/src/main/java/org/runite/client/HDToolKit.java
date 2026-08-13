package org.runite.client;

import com.jogamp.nativewindow.awt.AWTGraphicsConfiguration;
import com.jogamp.nativewindow.awt.JAWTWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.glu.gl2es1.GLUgl2es1;
import jogamp.nativewindow.jawt.x11.X11JAWTWindow;
import jogamp.newt.awt.NewtFactoryAWT;
import org.rs09.SlayerTracker;
import org.rs09.client.config.GameConfig;

import java.awt.*;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public final class HDToolKit {

    private static final float[] aFloatArray1808 = new float[16];
    public static GL2 gl;
    public static boolean highDetail = false;
    public static int viewHeight;
    public static int viewWidth;
    static int maxTextureUnits;
    static boolean aBoolean1790;
    static int anInt1791 = 0;
    static boolean aBoolean1798 = true;
    static boolean allows3DTextureMapping;
    static boolean supportMultisample;
    static int anInt1810;
    static boolean supportVertexBufferObject;
    static boolean aBoolean1817;
    static boolean supportVertexProgram;
    static boolean supportTextureCubeMap;
    private static GLContext glContext;
    private static GLDrawable glDrawable;
    private static String vendor;
    private static String renderer;
    private static float aFloat1787;
    private static boolean aBoolean1788 = false;
    private static int anInt1792 = 0;
    private static int anInt1793 = 0;
    private static float aFloat1794 = 0.0F;
    private static float aFloat1795;
    private static boolean aBoolean1796 = true;
    private static float aFloat1797 = 0.0F;
    private static boolean viewportSetup = false;
    private static int anInt1803 = -1;
    private static boolean aBoolean1805 = true;
    private static int anInt1812;
    private static boolean aBoolean1816 = true;
    private static JAWTWindow jawtWindow;

    private static RSString method1820(String var0) {
        byte[] var1;
        var1 = var0.getBytes(StandardCharsets.ISO_8859_1);
        return TextureOperation33.bufferToString(var1, var1.length, 0);
    }

    static void method1821(int offsetX, int offsetY, int ratioWidth, int ratioHeight) {
        viewport(0, 0, viewWidth, viewHeight, offsetX, offsetY, 0.0F, 0.0F, ratioWidth, ratioHeight);
    }

    static void method1822() {
        Unsorted.method551(0, 0);
        method1836();
        method1856(1);
        method1847(1);
        method1837(false);
        method1831(false);
        method1827(false);
        method1823();
    }

    static void method1823() {
        if (aBoolean1788) {
            gl.glMatrixMode(GL2.GL_TEXTURE);
            gl.glLoadIdentity();
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            aBoolean1788 = false;
        }

    }

    static void method1824() {
        Unsorted.method551(0, 0);
        method1836();
        method1856(0);
        method1847(0);
        method1837(false);
        method1831(false);
        method1827(false);
        method1823();
    }

    static void method1825(float var0, float var1) {
        if (!viewportSetup) {
            if (var0 != aFloat1797 || var1 != aFloat1794) {
                aFloat1797 = var0;
                aFloat1794 = var1;
                if (var1 == 0.0F) {
                    aFloatArray1808[10] = aFloat1787;
                    aFloatArray1808[14] = aFloat1795;
                } else {
                    float var2 = var0 / (var1 + var0);
                    float var3 = var2 * var2;
                    float var4 = -aFloat1795 * (1.0F - var2) * (1.0F - var2) / var1;
                    aFloatArray1808[10] = aFloat1787 + var4;
                    aFloatArray1808[14] = aFloat1795 * var3;
                }

                gl.glMatrixMode(GL2.GL_PROJECTION);
                gl.glLoadMatrixf(aFloatArray1808, 0);
                gl.glMatrixMode(GL2.GL_MODELVIEW);
            }
        }
    }

    public static void bufferSwap() {
        try {
            glDrawable.swapBuffers();
        } catch (GLException ignore) {
            //TODO: This may be the cause of the display failing sometimes.
        }
    }

    static void method1827(boolean var0) {
        if (var0 != aBoolean1816) {
            if (var0) {
                gl.glEnable(GL2.GL_FOG);
            } else {
                gl.glDisable(GL2.GL_FOG);
            }

            aBoolean1816 = var0;
        }
    }

    static void method1828() {
        Unsorted.method551(0, 0);
        method1836();
        method1856(0);
        method1847(0);
        method1837(false);
        method1831(false);
        method1827(false);
        method1823();
    }

    private static void method1829() {
        viewportSetup = false;
        gl.glDisable(GL2.GL_TEXTURE_2D);
        anInt1803 = -1;
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_COMBINE);
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
        anInt1793 = 0;
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
        anInt1792 = 0;
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_FOG);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        aBoolean1796 = true;
        aBoolean1805 = true;
        aBoolean1816 = true;
        Class44.method1073();
        gl.glActiveTexture(GL2.GL_TEXTURE1);
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_COMBINE);
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
        gl.glActiveTexture(GL2.GL_TEXTURE0);
        gl.setSwapInterval(0);
        gl.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        gl.glShadeModel(GL2.GL_SMOOTH);
        gl.glClearDepth(1.0D);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        method1830();
        gl.glMatrixMode(GL2.GL_TEXTURE);
        gl.glLoadIdentity();
        gl.glPolygonMode(GL2.GL_FRONT, GL2.GL_FILL);
        gl.glEnable(GL2.GL_CULL_FACE);
        gl.glCullFace(GL2.GL_BACK);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL2.GL_ALPHA_TEST);
        gl.glAlphaFunc(GL2.GL_GREATER, 0.0F);
        gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);
        aBoolean1798 = true;
        gl.glEnableClientState(GL2.GL_COLOR_ARRAY);
        gl.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
        Class92.method1511();
        Class68.method1275();
    }

    static void method1830() {
        gl.glDepthMask(true);
    }

    static void method1831(boolean var0) {
        if (var0 != aBoolean1805) {
            if (var0) {
                gl.glEnable(GL2.GL_DEPTH_TEST);
            } else {
                gl.glDisable(GL2.GL_DEPTH_TEST);
            }

            aBoolean1805 = var0;
        }
    }

    static void method1832(float var0) {
        method1825(3000.0F, var0 * 1.5F);
    }

    static void method1833() {
        int[] var0 = new int[2];
        gl.glGetIntegerv(GL2.GL_DRAW_BUFFER, var0, 0);
        gl.glGetIntegerv(GL2.GL_READ_BUFFER, var0, 1);
        gl.glDrawBuffer(GL2.GL_BACK_LEFT);
        gl.glReadBuffer(GL2.GL_FRONT_LEFT);
        bindTexture2D(-1);
        gl.glPushAttrib(GL2.GL_ENABLE_BIT);
        gl.glDisable(GL2.GL_FOG);
        gl.glDisable(GL2.GL_BLEND);
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glDisable(GL2.GL_ALPHA_TEST);
        gl.glRasterPos2i(0, 0);
        gl.glCopyPixels(0, 0, viewWidth, viewHeight, GL2.GL_COLOR);
        gl.glPopAttrib();
        gl.glDrawBuffer(var0[0]);
        gl.glReadBuffer(var0[1]);
    }

    static void createAndDestroyContext(Canvas canvas) {
        try {
            if (!canvas.isDisplayable()) {
                return;
            }

            GLProfile profile = GLProfile.getDefault();

            GLCapabilities glCaps = new GLCapabilities(profile);
            AWTGraphicsConfiguration config = AWTGraphicsConfiguration.create(canvas.getGraphicsConfiguration(), glCaps, glCaps);
            JAWTWindow jawtWindow = NewtFactoryAWT.getNativeWindow(canvas, config);
            GLDrawableFactory glDrawableFactory = GLDrawableFactory.getFactory(profile);
            GLDrawable glDrawable = glDrawableFactory.createGLDrawable(jawtWindow);

            glDrawable.setRealized(true);
            GLContext glContext = glDrawable.createContext(null);
            glContext.makeCurrent();
            glContext.release();
            glContext.destroy();
            glDrawable.setRealized(false);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void method1835() {
        Unsorted.method551(0, 0);
        method1836();
        bindTexture2D(-1);
        method1837(false);
        method1831(false);
        method1827(false);
        method1823();
    }

    private static void method1836() {
        if (!viewportSetup) {
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glOrtho(0.0D, viewWidth, 0.0D, viewHeight, -1.0D, 1.0D);
            gl.glViewport(0, 0, viewWidth, viewHeight);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glLoadIdentity();
            viewportSetup = true;
        }
    }

    static void method1837(boolean var0) {
        if (var0 != aBoolean1796) {
            if (var0) {
                gl.glEnable(GL2.GL_LIGHTING);
            } else {
                gl.glDisable(GL2.GL_LIGHTING);
            }

            aBoolean1796 = var0;
        }
    }

    static float method1839() {
        return aFloat1794;
    }

    private static int method1840() {
        int var0 = 0;
        vendor = gl.glGetString(GL2.GL_VENDOR);
        renderer = gl.glGetString(GL2.GL_RENDERER);

        String var1 = vendor.toLowerCase();
        if (var1.contains("microsoft")) {
            var0 |= 1;
        }

        if (var1.contains("brian paul") || var1.contains("mesa")) {
            var0 |= 1;
        }

        String versionString = gl.glGetString(GL2.GL_VERSION);
        String[] var3 = versionString.split("[. ]");
        if (var3.length >= 2) {
            try {
                int var4 = Integer.parseInt(var3[0]);
                int var5 = Integer.parseInt(var3[1]);
                anInt1812 = var4 * 10 + var5;
            } catch (NumberFormatException var11) {
                var0 |= 4;
            }
        } else {
            var0 |= 4;
        }

        if (anInt1812 < 12) {
            var0 |= 2;
        }

        if (!gl.isExtensionAvailable("GL_ARB_multitexture")) {
            var0 |= 8;
        }

        if (!gl.isExtensionAvailable("GL_ARB_texture_env_combine")) {
            var0 |= 32;
        }

        int[] var12 = new int[1];
        gl.glGetIntegerv(GL2.GL_MAX_TEXTURE_UNITS, var12, 0);
        maxTextureUnits = var12[0];
        gl.glGetIntegerv(GL2.GL_MAX_TEXTURE_COORDS_ARB, var12, 0);
        int anInt1814 = var12[0];
        gl.glGetIntegerv(GL2.GL_MAX_TEXTURE_IMAGE_UNITS_ARB, var12, 0);
        int anInt1806 = var12[0];
        if (maxTextureUnits < 2 || anInt1814 < 2 || anInt1806 < 2) {
            var0 |= 16;
        }

        if (var0 == 0) {
            aBoolean1790 = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
            supportVertexBufferObject = gl.isExtensionAvailable("GL_ARB_vertex_buffer_object");
            supportMultisample = gl.isExtensionAvailable("GL_ARB_multisample");
            supportTextureCubeMap = gl.isExtensionAvailable("GL_ARB_texture_cube_map");
            supportVertexProgram = gl.isExtensionAvailable("GL_ARB_vertex_program");
            allows3DTextureMapping = gl.isExtensionAvailable("GL_EXT_texture3D");
            RSString var13 = method1820(renderer).toLowercase();
            if (var13.indexOf(RSString.parse("radeon"), 57) != -1) {
                int version = 0;
                RSString[] var7 = var13.method1565().method1567(32, (byte) -98);

                for (RSString var9 : var7) {
                    if (var9.length() >= 4 && var9.substring(0, 4, 0).isInteger()) {
                        version = var9.substring(0, 4, 0).parseInt();
                        break;
                    }
                }

                if (version >= 7000 && version <= 7999) {
                    supportVertexBufferObject = false;
                }

                if (version >= 7000 && version <= 9250) {
                    allows3DTextureMapping = false;
                }

                aBoolean1817 = supportVertexBufferObject;
            }

            if (supportVertexBufferObject) {
                try {
                    int[] var14 = new int[1];
                    gl.glGenBuffers(1, var14, 0);
                } catch (Throwable var10) {
                    return -4;
                }
            }

            return 0;
        } else {
            return var0;
        }
    }

    static void method1841() {
        gl.glClear(GL2.GL_DEPTH_BUFFER_BIT);
    }

    static void method1842() {
        if (gl != null) {
            try {
                Class101.method1609();
            } catch (GLException ex) {
                ex.printStackTrace();
            }
        }

        if (jawtWindow != null) {
            if (!jawtWindow.getLock().isLocked()) {
                jawtWindow.lockSurface();
            }

            if (glContext != null) {
                Class31.method988();

                try {
                    if (GLContext.getCurrent() == glContext) {
                        glContext.release();
                    }
                } catch (GLException ex) {
                    ex.printStackTrace();
                }

                try {
                    glContext.destroy();
                } catch (GLException ex) {
                    ex.printStackTrace();
                }
            }
        }

        if (glDrawable != null) {
            try {
                glDrawable.setRealized(false);
            } catch (GLException ex) {
                ex.printStackTrace();
            }
        }

        jawtWindow = null;
        gl = null;
        glDrawable = null;
        glContext = null;
        Class68.method1273();
        highDetail = false;
        SlayerTracker.setSprite();
    }

    static void method1843(float var0, float var1) {
        gl.glMatrixMode(GL2.GL_TEXTURE);
        if (aBoolean1788) {
            gl.glLoadIdentity();
        }

        gl.glTranslatef(var0, var1, (float) 0.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        aBoolean1788 = true;
    }

    static void viewport(int x, int y, int width, int height, int offsetX, int offsetY, float rotationX, float rotationY, int ratioWidth, int ratioHeight) {
        int left = (x - offsetX << 8) / ratioWidth;
        int right = (x + width - offsetX << 8) / ratioWidth;
        int top = (y - offsetY << 8) / ratioHeight;
        int bottom = (y + height - offsetY << 8) / ratioHeight;
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        float constantFloat = 0.09765625F;
        method1848((float) left * constantFloat, (float) right * constantFloat, (float) (-bottom) * constantFloat, (float) (-top) * constantFloat, 50.0F, GameConfig.RENDER_DISTANCE_VALUE);
        gl.glViewport(x, viewHeight - y - height, width, height);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
        if (rotationX != 0.0F) {
            gl.glRotatef(rotationX, 1.0F, 0.0F, 0.0F);
        }

        if (rotationY != 0.0F) {
            gl.glRotatef(rotationY, 0.0F, 1.0F, 0.0F);
        }

        viewportSetup = false;
        Class139.screenLowerX = left;
        Class145.screenUpperX = right;
        Class1.screenUpperY = top;
        AtmosphereParser.screenLowerY = bottom;
    }

    private static void method1845(boolean var0) {
        if (var0 != aBoolean1798) {
            if (var0) {
                gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);
            } else {
                gl.glDisableClientState(GL2.GL_NORMAL_ARRAY);
            }

            aBoolean1798 = var0;
        }
    }

    static void method1846() {
        if (Class106.aBoolean1441) {
            method1837(true);
            method1845(true);
        } else {
            method1837(false);
            method1845(false);
        }

    }

    static void method1847(int var0) {
        if (var0 != anInt1792) {
            //sets a texture environment parameter.
            if (var0 == 0) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
            } else if (var0 == 1) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_REPLACE);
            } else if (var0 == 2) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_ADD);
            }
            anInt1792 = var0;
        }
    }

    private static void method1848(float left, float right, float bottom, float top, float constantFloat, float renderDistance) {
        float var6 = constantFloat * 2.0F;
        aFloatArray1808[0] = var6 / (right - left);
        aFloatArray1808[1] = 0.0F;
        aFloatArray1808[2] = 0.0F;
        aFloatArray1808[3] = 0.0F;
        aFloatArray1808[4] = 0.0F;
        aFloatArray1808[5] = var6 / (top - bottom);
        aFloatArray1808[6] = 0.0F;
        aFloatArray1808[7] = 0.0F;
        aFloatArray1808[8] = (right + left) / (right - left);
        aFloatArray1808[9] = (top + bottom) / (top - bottom);
        aFloatArray1808[10] = aFloat1787 = -(renderDistance + constantFloat) / (renderDistance - constantFloat);
        aFloatArray1808[11] = -1.0F;
        aFloatArray1808[12] = 0.0F;
        aFloatArray1808[13] = 0.0F;
        aFloatArray1808[14] = aFloat1795 = -(var6 * renderDistance) / (renderDistance - constantFloat);
        aFloatArray1808[15] = 0.0F;
        gl.glLoadMatrixf(aFloatArray1808, 0);
        aFloat1797 = 0.0F;
        aFloat1794 = 0.0F;
    }

    /**
     * clearScreen takes an int of color (can be replaced with whatever color you see fit)
     *
     * @param color
     */
    static void clearScreen(int color) {
        gl.glClearColor((float) (color >> 16 & 0xFF) / 255.0F, (float) (color >> 8 & 0xFF) / 255.0F, (float) (color & 0xFF) / 255.0F, 0.0F);
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
    }

    static void bindTexture2D(int var0) {
        if (var0 != anInt1803) {
            if (var0 == -1) {
                gl.glDisable(GL2.GL_TEXTURE_2D);
            } else {
                if (anInt1803 == -1) {
                    gl.glEnable(GL2.GL_TEXTURE_2D);
                }

                gl.glBindTexture(GL2.GL_TEXTURE_2D, var0);
            }

            anInt1803 = var0;
        }
    }

    static void depthBufferWritingDisabled() {
        gl.glDepthMask(false);
    }

    static float method1852() {
        return aFloat1797;
    }

    // need to initialize a context to get the vendor name before the full context is made
    static String getVendorFirst(Canvas canvas) {
        try {
            if (!canvas.isDisplayable()) {
                return "err";
            }

            GLProfile profile = GLProfile.getDefault();

            GLCapabilities glCaps = new GLCapabilities(profile);
            AWTGraphicsConfiguration config = AWTGraphicsConfiguration.create(canvas.getGraphicsConfiguration(), glCaps, glCaps);
            JAWTWindow jawtWindow = NewtFactoryAWT.getNativeWindow(canvas, config);
            GLDrawableFactory glDrawableFactory = GLDrawableFactory.getFactory(profile);
            GLDrawable glDrawable = glDrawableFactory.createGLDrawable(jawtWindow);

            glDrawable.setRealized(true);
            GLContext glContext = glDrawable.createContext(null);
            glContext.makeCurrent();

            String vendor = GLContext.getCurrentGL().glGetString(GL2.GL_VENDOR);

            glContext.release();
            glContext.destroy();
            glDrawable.setRealized(false);

            return vendor;
        } catch (Exception ex) {
            return "err";
        }
    }

    static void startHDToolkit(Canvas canvas, int selectedAntiAliasing) {
        try {
            if (!canvas.isDisplayable())
            {
                return;
            }

            canvas.setIgnoreRepaint(true);

            GLProfile glProfile = GLProfile.get(GLProfile.GL2);

            GLCapabilities glCaps = new GLCapabilities(glProfile);

            if (GameConfig.AA_SAMPLES > 0) {
                selectedAntiAliasing = GameConfig.AA_SAMPLES;
            }
            if (selectedAntiAliasing > 0) {
                if (!(Signlink.osName.startsWith("linux") && getVendorFirst(canvas).startsWith("NVIDIA"))) {
                    glCaps.setSampleBuffers(true);
                    glCaps.setNumSamples(selectedAntiAliasing);
                } else {
                    System.err.println("----\nYou seem to be running on linux and using the nvidia drivers. Application-controlled AA does not work here.\nPlease force AA in your driver control panel, and ignore this if you have.\n----");
                }
            }
            AWTGraphicsConfiguration config = AWTGraphicsConfiguration.create(canvas.getGraphicsConfiguration(), glCaps, glCaps);

            jawtWindow = NewtFactoryAWT.getNativeWindow(canvas, config);
            canvas.setFocusable(true);

            GLDrawableFactory glDrawableFactory = GLDrawableFactory.getFactory(glProfile);

            if (jawtWindow instanceof X11JAWTWindow && !jawtWindow.getLock().isLocked())
            {
                jawtWindow.lockSurface();
            }
            try
            {
                glDrawable = glDrawableFactory.createGLDrawable(jawtWindow);
                glDrawable.setRealized(true);

                glContext = glDrawable.createContext(null);
                //glContext.enableGLDebugMessage(true);
            }
            finally
            {
                if (jawtWindow instanceof X11JAWTWindow && jawtWindow.getLock().isLocked())
                {
                    jawtWindow.unlockSurface();
                }
            }

            int retryContext = 0;
            while (true) {
                glContext = glDrawable.createContext(null);

                try {
                    int result = glContext.makeCurrent();
                    if (result != GLContext.CONTEXT_NOT_CURRENT) {
                        break;
                    }
                } catch (GLException ex) {
                    ex.printStackTrace();
                }

                if (retryContext++ > 5) {
                    return;
                }

                TimeUtils.sleep(1000L);
            }

            // sometimes it can get locked again despite the same code above, this ensures everything is fine
            if (jawtWindow instanceof X11JAWTWindow && jawtWindow.getLock().isLocked())
            {
                jawtWindow.unlockSurface();
            }

            gl = GLContext.getCurrentGL().getGL2();
            gl.setSwapInterval(1); // v-sync
            new GLUgl2es1();

            highDetail = true;
            SlayerTracker.setSprite();
            viewWidth = canvas.getSize().width;
            viewHeight = canvas.getSize().height;

            int result = method1840();
            if (result == 0) {
                method1857();
                method1829();
                gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
                int retry = 0;

                while (true) {
                    try {
                        glDrawable.swapBuffers();
                        break;
                    } catch (GLException ex) {
                        if (retry++ > 5) {
                            ex.printStackTrace();
                            method1842();
                            return;
                        }

                        TimeUtils.sleep(100L);
                    }
                }

                gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
            } else {
                method1842();
            }
        } catch (GLException ex) {
            ex.printStackTrace();
            method1842();
        }
    }

    static void method1854(int var0, int var1) {
        viewWidth = var0;
        viewHeight = var1;
        viewportSetup = false;
    }

    static void method1855(int var0, int var1, int var2, int var3, int var4, int var5) {
        int var6 = -var0;
        int var7 = viewWidth - var0;
        int var8 = -var1;
        int var9 = viewHeight - var1;
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        float var10 = (float) var2 / 512.0F;
        float var11 = var10 * (256.0F / (float) var4);
        float var12 = var10 * (256.0F / (float) var5);
        gl.glOrtho((float) var6 * var11, (float) var7 * var11, (float) (-var9) * var12, (float) (-var8) * var12, 50 - var3, 3584 - var3);
        gl.glViewport(0, 0, viewWidth, viewHeight);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
        viewportSetup = false;
    }

    static void method1856(int var0) {
        if (var0 != anInt1793) {
            if (var0 == 0) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
            }

            if (var0 == 1) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_REPLACE);
            }

            if (var0 == 2) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD);
            }

            if (var0 == 3) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_SUBTRACT);
            }

            if (var0 == 4) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD_SIGNED);
            }

            if (var0 == 5) {
                gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_INTERPOLATE);
            }

            anInt1793 = var0;
        }
    }

    private static void method1857() {
        int[] var0 = new int[1];
        gl.glGenTextures(1, var0, 0);
        anInt1810 = var0[0];
        gl.glBindTexture(GL2.GL_TEXTURE_2D, anInt1810);
        gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, 4, 1, 1, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, IntBuffer.wrap(new int[]{-1}));
        Class68.method1276();
        Class3_Sub24_Sub3.method468();
    }

}
