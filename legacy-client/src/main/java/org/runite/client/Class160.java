package org.runite.client;

import com.jogamp.opengl.*;
import java.nio.ByteBuffer;

final class Class160 implements ShaderInterface {

    private int anInt2187 = -1;
    private boolean aBoolean2188 = false;
    private int[] anIntArray2189 = null;


    public Class160() {
        if (HDToolKit.supportTextureCubeMap && HDToolKit.maxTextureUnits >= 2) {
            this.method2199();
            GL2 var1 = HDToolKit.gl;
            var1.glBindTexture(GL2.GL_TEXTURE_CUBE_MAP, this.anIntArray2189[0]);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_R, GL2.GL_CLAMP_TO_EDGE);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);
            var1.glBindTexture(GL2.GL_TEXTURE_CUBE_MAP, this.anIntArray2189[1]);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_R, GL2.GL_CLAMP_TO_EDGE);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);
            var1.glBindTexture(GL2.GL_TEXTURE_CUBE_MAP, this.anIntArray2189[2]);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_R, GL2.GL_CLAMP_TO_EDGE);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
            var1.glTexParameteri(GL2.GL_TEXTURE_CUBE_MAP, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);
            this.aBoolean2188 = HDToolKit.maxTextureUnits < 3;
        }

        this.method2198();
    }

    private void method2198() {
        GL2 var1 = HDToolKit.gl;
        this.anInt2187 = var1.glGenLists(2);
        var1.glNewList(this.anInt2187, GL2.GL_COMPILE);
        if (this.anIntArray2189 == null) {
            var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PRIMARY_COLOR);
        } else {
            var1.glActiveTexture(GL2.GL_TEXTURE1);
            var1.glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_NORMAL_MAP);
            var1.glTexGeni(GL2.GL_T, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_NORMAL_MAP);
            var1.glTexGeni(GL2.GL_R, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_NORMAL_MAP);
            var1.glEnable(GL2.GL_TEXTURE_GEN_S);
            var1.glEnable(GL2.GL_TEXTURE_GEN_T);
            var1.glEnable(GL2.GL_TEXTURE_GEN_R);
            var1.glEnable(GL2.GL_TEXTURE_CUBE_MAP);
            var1.glMatrixMode(GL2.GL_TEXTURE);
            var1.glLoadIdentity();
            var1.glRotatef(22.5F, 1.0F, 0.0F, 0.0F);
            var1.glMatrixMode(GL2.GL_MODELVIEW);
            if (this.aBoolean2188) {
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_ALPHA);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_REPLACE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PRIMARY_COLOR);
            } else {
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_REPLACE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_PREVIOUS);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
                var1.glActiveTexture(GL2.GL_TEXTURE2);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_COMBINE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_PREVIOUS);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC1_RGB, GL2.GL_PREVIOUS);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND1_RGB, GL2.GL_SRC_ALPHA);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_REPLACE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PRIMARY_COLOR);
                var1.glBindTexture(GL2.GL_TEXTURE_2D, HDToolKit.anInt1810);
                var1.glEnable(GL2.GL_TEXTURE_2D);
            }

            var1.glActiveTexture(GL2.GL_TEXTURE0);
        }

        var1.glEndList();
        var1.glNewList(this.anInt2187 + 1, GL2.GL_COMPILE);
        if (this.anIntArray2189 == null) {
            var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
        } else {
            var1.glActiveTexture(GL2.GL_TEXTURE1);
            var1.glDisable(GL2.GL_TEXTURE_GEN_S);
            var1.glDisable(GL2.GL_TEXTURE_GEN_T);
            var1.glDisable(GL2.GL_TEXTURE_GEN_R);
            var1.glDisable(GL2.GL_TEXTURE_CUBE_MAP);
            var1.glMatrixMode(GL2.GL_TEXTURE);
            var1.glLoadIdentity();
            var1.glMatrixMode(GL2.GL_MODELVIEW);
            if (this.aBoolean2188) {
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
            } else {
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_TEXTURE);
                var1.glActiveTexture(GL2.GL_TEXTURE2);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_TEXTURE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND1_RGB, GL2.GL_SRC_COLOR);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
                var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
                var1.glDisable(GL2.GL_TEXTURE_2D);
            }

            var1.glActiveTexture(GL2.GL_TEXTURE0);
        }

        var1.glEndList();
    }

    public final void method21() {
        GL2 var1 = HDToolKit.gl;
        if (Class106.aBoolean1441) {
            var1.glCallList(this.anInt2187 + 1);
        } else {
            var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
        }

    }

    public final int method24() {
        return 4;
    }

    public final void method22() {
        GL2 var1 = HDToolKit.gl;
        HDToolKit.method1847(1);
        if (Class106.aBoolean1441) {
            var1.glCallList(this.anInt2187);
        } else {
            var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PRIMARY_COLOR);
        }

    }

    public final void method23(int var1) {
        GL2 var2 = HDToolKit.gl;
        if (Class106.aBoolean1441 && this.anIntArray2189 != null) {
            var2.glActiveTexture(GL2.GL_TEXTURE1);
            var2.glBindTexture(GL2.GL_TEXTURE_CUBE_MAP, this.anIntArray2189[var1 - 1]);
            var2.glActiveTexture(GL2.GL_TEXTURE0);
        }

    }

    private void method2199() {
        GL2 var8 = HDToolKit.gl;
        if (this.anIntArray2189 == null) {
            this.anIntArray2189 = new int[3];
            var8.glGenTextures(3, this.anIntArray2189, 0);
        }

        short var9 = 4096;
        byte[] var10 = new byte[var9];
        byte[] var11 = new byte[var9];
        byte[] var12 = new byte[var9];

        for (int var13 = 0; var13 < 6; ++var13) {
            int var14 = 0;

            for (int var15 = 0; var15 < 64; ++var15) {
                for (int var16 = 0; var16 < 64; ++var16) {
                    float var5 = 2.0F * (float) var16 / 64.0F - 1.0F;
                    float var6 = 2.0F * (float) var15 / 64.0F - 1.0F;
                    float var7 = (float) (1.0D / Math.sqrt(var5 * var5 + 1.0F + var6 * var6));
                    var5 *= var7;
                    var6 *= var7;
                    float var4;
                    if (var13 == 0) {
                        var4 = -var5;
                    } else if (var13 == 1) {
                        var4 = var5;
                    } else if (var13 == 2) {
                        var4 = var6;
                    } else if (var13 == 3) {
                        var4 = -var6;
                    } else if (var13 == 4) {
                        var4 = var7;
                    } else {
                        var4 = -var7;
                    }

                    int var1;
                    int var2;
                    int var3;
                    if (var4 > 0.0F) {
                        var1 = (int) (Math.pow(var4, 96.0D) * 255.0D);
                        var2 = (int) (Math.pow(var4, 36.0D) * 255.0D);
                        var3 = (int) (Math.pow(var4, 12.0D) * 255.0D);
                    } else {
                        var3 = 0;
                        var2 = 0;
                        var1 = 0;
                    }

                    if (HDToolKit.maxTextureUnits < 3) {
                        var1 /= 5;
                        var2 /= 5;
                        var3 /= 5;
                    } else {
                        var1 /= 2;
                        var2 /= 2;
                        var3 /= 2;
                    }

                    var11[var14] = (byte) var1;
                    var12[var14] = (byte) var2;
                    var10[var14] = (byte) var3;
                    ++var14;
                }
            }

            var8.glBindTexture(GL2.GL_TEXTURE_CUBE_MAP, this.anIntArray2189[0]);
            var8.glTexImage2D(GL2.GL_TEXTURE_CUBE_MAP_POSITIVE_X + var13, 0, GL2.GL_ALPHA, 64, 64, 0, GL2.GL_ALPHA, GL2.GL_UNSIGNED_BYTE, ByteBuffer.wrap(var11));
            var8.glBindTexture(GL2.GL_TEXTURE_CUBE_MAP, this.anIntArray2189[1]);
            var8.glTexImage2D(GL2.GL_TEXTURE_CUBE_MAP_POSITIVE_X + var13, 0, GL2.GL_ALPHA, 64, 64, 0, GL2.GL_ALPHA, GL2.GL_UNSIGNED_BYTE, ByteBuffer.wrap(var12));
            var8.glBindTexture(GL2.GL_TEXTURE_CUBE_MAP, this.anIntArray2189[2]);
            var8.glTexImage2D(GL2.GL_TEXTURE_CUBE_MAP_POSITIVE_X + var13, 0, GL2.GL_ALPHA, 64, 64, 0, GL2.GL_ALPHA, GL2.GL_UNSIGNED_BYTE, ByteBuffer.wrap(var10));
            Class31.anInt580 += var9 * 3;
        }

    }
}
