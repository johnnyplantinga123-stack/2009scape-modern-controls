package org.runite.client;

import com.jogamp.opengl.*;
import org.rs09.client.config.GameConfig;

import java.nio.ByteBuffer;

final class WaterMovementShader implements ShaderInterface {

   private int anInt2177 = -1;
   private static final float[] color = new float[]{0.1F, 0.1F, 0.15F, 0.1F};
   private final float[] aFloatArray2179 = new float[4];
   private int textureId = -1;
   private int anInt2181 = -1;


   private void method1699() {
      byte[] var1 = new byte[]{(byte)0, (byte)-1};
      GL2 var2 = HDToolKit.gl;
      int[] var3 = new int[1];
      var2.glGenTextures(1, var3, 0);
      var2.glBindTexture(GL2.GL_TEXTURE_1D, var3[0]);
      var2.glTexImage1D(GL2.GL_TEXTURE_1D, 0, GL2.GL_ALPHA, 2, 0, GL2.GL_ALPHA, GL2.GL_UNSIGNED_BYTE, ByteBuffer.wrap(var1));
      var2.glTexParameteri(GL2.GL_TEXTURE_1D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
      var2.glTexParameteri(GL2.GL_TEXTURE_1D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
      var2.glTexParameteri(GL2.GL_TEXTURE_1D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
      this.textureId = var3[0];
   }

   private void method1701() {
      GL2 var1 = HDToolKit.gl;
      this.anInt2177 = var1.glGenLists(2);
      var1.glNewList(this.anInt2177, GL2.GL_COMPILE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE1_RGB, GL2.GL_CONSTANT);
      var1.glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_RGB_SCALE, 2.0F);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE1_ALPHA, GL2.GL_CONSTANT);
      var1.glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
      var1.glTexGeni(GL2.GL_T, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
      var1.glTexGenfv(GL2.GL_S, GL2.GL_OBJECT_PLANE, new float[]{9.765625E-4F, 0.0F, 0.0F, 0.0F}, 0);
      var1.glTexGenfv(GL2.GL_T, GL2.GL_OBJECT_PLANE, new float[]{0.0F, 0.0F, 9.765625E-4F, 0.0F}, 0);
      var1.glEnable(GL2.GL_TEXTURE_GEN_S);
      var1.glEnable(GL2.GL_TEXTURE_GEN_T);
      if(Class88.Texture3DEnabled) {
         var1.glBindTexture(GL2.GL_TEXTURE_3D, Class88.anInt1228);
         var1.glTexGeni(GL2.GL_R, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
         var1.glTexGeni(GL2.GL_Q, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
         var1.glTexGenfv(GL2.GL_Q, GL2.GL_OBJECT_PLANE, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, 0);
         var1.glEnable(GL2.GL_TEXTURE_GEN_R);
         var1.glEnable(GL2.GL_TEXTURE_GEN_Q);
         var1.glEnable(GL2.GL_TEXTURE_3D);
      }

      var1.glActiveTexture(GL2.GL_TEXTURE1);
      var1.glEnable(GL2.GL_TEXTURE_1D);
      var1.glBindTexture(GL2.GL_TEXTURE_1D, this.textureId);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_INTERPOLATE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_CONSTANT);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE2_RGB, GL2.GL_TEXTURE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_INTERPOLATE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_CONSTANT);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE2_ALPHA, GL2.GL_TEXTURE);
      var1.glEnable(GL2.GL_TEXTURE_GEN_S);
      var1.glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
      var1.glPushMatrix();
      var1.glLoadIdentity();
      var1.glEndList();
      var1.glNewList(this.anInt2177 + 1, GL2.GL_COMPILE);
      var1.glActiveTexture(GL2.GL_TEXTURE1);
      var1.glDisable(GL2.GL_TEXTURE_1D);
      var1.glDisable(GL2.GL_TEXTURE_GEN_S);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_TEXTURE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE2_RGB, GL2.GL_CONSTANT);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE2_ALPHA, GL2.GL_CONSTANT);
      var1.glActiveTexture(GL2.GL_TEXTURE0);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE1_RGB, GL2.GL_PREVIOUS);
      var1.glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_RGB_SCALE, 1.0F);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE1_ALPHA, GL2.GL_PREVIOUS);
      var1.glDisable(GL2.GL_TEXTURE_GEN_S);
      var1.glDisable(GL2.GL_TEXTURE_GEN_T);
      if(Class88.Texture3DEnabled) {
         var1.glDisable(GL2.GL_TEXTURE_GEN_R);
         var1.glDisable(GL2.GL_TEXTURE_GEN_Q);
         var1.glDisable(GL2.GL_TEXTURE_3D);
      }

      var1.glEndList();
   }

   public final void method21() {
      HDToolKit.gl.glCallList(this.anInt2177 + 1);
   }

   public final void method23(int var1) {
      GL2 var2 = HDToolKit.gl;
      var2.glActiveTexture(GL2.GL_TEXTURE1);
      var2.glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, Unsorted.aFloatArray1934, 0);
      var2.glActiveTexture(GL2.GL_TEXTURE0);
      if((var1 & 1) == 1) {
         if(Class88.Texture3DEnabled) {
            if(this.anInt2181 != HDToolKit.anInt1791) {
               this.aFloatArray2179[0] = 0.0F;
               this.aFloatArray2179[1] = 0.0F;
               this.aFloatArray2179[2] = 0.0F;
               this.aFloatArray2179[3] = (float)HDToolKit.anInt1791 * 0.0050F;//Water moving speed?
               var2.glTexGenfv(GL2.GL_R, GL2.GL_OBJECT_PLANE, this.aFloatArray2179, 0);
               this.anInt2181 = HDToolKit.anInt1791;
            }
         } else {
            HDToolKit.bindTexture2D(Class88.anIntArray1224[HDToolKit.anInt1791 * 64 / 100 % 64]);
         }
      } else if(Class88.Texture3DEnabled) {
         this.aFloatArray2179[0] = 0.0F;
         this.aFloatArray2179[1] = 0.0F;
         this.aFloatArray2179[2] = 0.0F;
         this.aFloatArray2179[3] = 0.0F;
         var2.glTexGenfv(GL2.GL_R, GL2.GL_OBJECT_PLANE, this.aFloatArray2179, 0);
      } else {
         HDToolKit.bindTexture2D(Class88.anIntArray1224[0]);
      }

   }

   public final void method22() {
      GL2 var1 = HDToolKit.gl;
      HDToolKit.method1856(2);
      HDToolKit.method1847(2);
      HDToolKit.method1823();
      var1.glCallList(this.anInt2177);
      float var2 = 2662.4001F;
      var2 += (float)(GroundItem.anInt2938 - 128) * 0.5F;
      if(var2 >= GameConfig.RENDER_DISTANCE_FOG_FIX) {
         var2 = GameConfig.RENDER_DISTANCE_FOG_FIX;
      }

      this.aFloatArray2179[0] = 0.0F;
      this.aFloatArray2179[1] = 0.0F;
      this.aFloatArray2179[2] = 1.0F / (var2 - GameConfig.RENDER_DISTANCE_FOG_FIX);
      this.aFloatArray2179[3] = var2 / (var2 - GameConfig.RENDER_DISTANCE_FOG_FIX);
      var1.glTexGenfv(GL2.GL_S, GL2.GL_EYE_PLANE, this.aFloatArray2179, 0);
      var1.glPopMatrix();
      var1.glActiveTexture(GL2.GL_TEXTURE0);
      var1.glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, color, 0);
   }

   public final int method24() {
      return 15;
   }

   public WaterMovementShader() {
      this.method1699();
      this.method1701();
   }

}
