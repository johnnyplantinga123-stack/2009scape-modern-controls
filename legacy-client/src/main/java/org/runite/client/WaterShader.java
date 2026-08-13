package org.runite.client;

import com.jogamp.opengl.*;
import java.nio.ByteBuffer;

final class WaterShader implements ShaderInterface {

   static int anInt3285 = 128;
   private final float[] aFloatArray2190 = new float[4];
   private static boolean aBoolean2191 = false;
   private int anInt2192 = -1;
   private int anInt2193 = -1;


   public WaterShader() {
      if(HDToolKit.maxTextureUnits >= 2) {
         int[] textures = new int[1];
         byte[] pixels = new byte[8];
         int pixelsPos = 0;
         while (pixelsPos < 8) {
            pixels[pixelsPos] = (byte) (96 + ++pixelsPos * 159 / 8);
         }
         GL2 var4 = HDToolKit.gl;
         var4.glGenTextures(1, textures, 0);
         var4.glBindTexture(GL2.GL_TEXTURE_1D, textures[0]);
         var4.glTexImage1D(GL2.GL_TEXTURE_1D, 0, GL2.GL_ALPHA, 8, 0, GL2.GL_ALPHA, GL2.GL_UNSIGNED_BYTE, ByteBuffer.wrap(pixels));
         var4.glTexParameteri(GL2.GL_TEXTURE_1D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
         var4.glTexParameteri(GL2.GL_TEXTURE_1D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
         var4.glTexParameteri(GL2.GL_TEXTURE_1D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
         this.anInt2192 = textures[0];
         aBoolean2191 = HDToolKit.maxTextureUnits > 2 && HDToolKit.allows3DTextureMapping;
         this.method2251();
      }

   }


   private void method2251() {
      GL2 var1 = HDToolKit.gl;
      this.anInt2193 = var1.glGenLists(2);
      var1.glNewList(this.anInt2193, GL2.GL_COMPILE);
      var1.glActiveTexture(GL2.GL_TEXTURE1);
      if(aBoolean2191) {
         var1.glBindTexture(GL2.GL_TEXTURE_3D, Class88.anInt1228);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_REPLACE);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PREVIOUS);
         var1.glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
         var1.glTexGeni(GL2.GL_R, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
         var1.glTexGeni(GL2.GL_T, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
         var1.glTexGeni(GL2.GL_Q, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
         var1.glTexGenfv(GL2.GL_Q, GL2.GL_OBJECT_PLANE, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, 0);
         var1.glEnable(GL2.GL_TEXTURE_GEN_S);
         var1.glEnable(GL2.GL_TEXTURE_GEN_T);
         var1.glEnable(GL2.GL_TEXTURE_GEN_R);
         var1.glEnable(GL2.GL_TEXTURE_GEN_Q);
         var1.glEnable(GL2.GL_TEXTURE_3D);
         var1.glActiveTexture(GL2.GL_TEXTURE2);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_COMBINE);
      }

      var1.glBindTexture(GL2.GL_TEXTURE_1D, this.anInt2192);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_INTERPOLATE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_CONSTANT);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE2_RGB, GL2.GL_TEXTURE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_REPLACE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PREVIOUS);
      var1.glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
      var1.glEnable(GL2.GL_TEXTURE_1D);
      var1.glEnable(GL2.GL_TEXTURE_GEN_S);
      var1.glActiveTexture(GL2.GL_TEXTURE0);
      var1.glEndList();
      var1.glNewList(this.anInt2193 + 1, GL2.GL_COMPILE);
      var1.glActiveTexture(GL2.GL_TEXTURE1);
      if(aBoolean2191) {
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
         var1.glDisable(GL2.GL_TEXTURE_GEN_S);
         var1.glDisable(GL2.GL_TEXTURE_GEN_T);
         var1.glDisable(GL2.GL_TEXTURE_GEN_R);
         var1.glDisable(GL2.GL_TEXTURE_GEN_Q);
         var1.glDisable(GL2.GL_TEXTURE_3D);
         var1.glActiveTexture(GL2.GL_TEXTURE2);
         var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
      }

      var1.glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, new float[]{0.0F, 1.0F, 0.0F, 1.0F}, 0);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_TEXTURE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE2_RGB, GL2.GL_CONSTANT);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
      var1.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
      var1.glDisable(GL2.GL_TEXTURE_1D);
      var1.glDisable(GL2.GL_TEXTURE_GEN_S);
      var1.glActiveTexture(GL2.GL_TEXTURE0);
      var1.glEndList();
   }

   static int method2252() {
      return aBoolean2191 ? GL2.GL_TEXTURE2 : GL2.GL_TEXTURE1;
   }

   static void method2253() {
      GL2 var0 = HDToolKit.gl;
      var0.glClientActiveTexture(method2252());
      var0.glDisableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
      var0.glClientActiveTexture(GL2.GL_TEXTURE0);
   }

   public final void method22() {
      GL2 var1 = HDToolKit.gl;
      var1.glCallList(this.anInt2193);
   }

   static void method2254() {
      GL2 var0 = HDToolKit.gl;
      var0.glClientActiveTexture(method2252());
      var0.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
      var0.glClientActiveTexture(GL2.GL_TEXTURE0);
   }

   public final int method24() {
      return 0;
   }

   public final void method21() {
      GL2 var1 = HDToolKit.gl;
      var1.glCallList(this.anInt2193 + 1);
   }

   public final void method23(int var1) {
      GL2 var2 = HDToolKit.gl;
      var2.glActiveTexture(GL2.GL_TEXTURE1);
      if(!aBoolean2191 && var1 < 0) {
         var2.glDisable(GL2.GL_TEXTURE_GEN_S);
      } else {
         var2.glPushMatrix();
         var2.glLoadIdentity();
         var2.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
         var2.glRotatef((float)GroundItem.anInt2938 * 360.0F / 2048.0F, 1.0F, 0.0F, 0.0F);
         var2.glRotatef((float) TextureOperation9.anInt3103 * 360.0F / 2048.0F, 0.0F, 1.0F, 0.0F);
         var2.glTranslatef((float)(-Unsorted.anInt144), (float)(-Unsorted.anInt3695), (float)(-LinkableRSString.anInt2587));
         if(aBoolean2191) {
            this.aFloatArray2190[0] = 0.0010F;
            this.aFloatArray2190[1] = 9.0E-4F;
            this.aFloatArray2190[2] = 0.0F;
            this.aFloatArray2190[3] = 0.0F;
            var2.glTexGenfv(GL2.GL_S, GL2.GL_EYE_PLANE, this.aFloatArray2190, 0);
            this.aFloatArray2190[0] = 0.0F;
            this.aFloatArray2190[1] = 9.0E-4F;
            this.aFloatArray2190[2] = 0.0010F;
            this.aFloatArray2190[3] = 0.0F;
            var2.glTexGenfv(GL2.GL_T, GL2.GL_EYE_PLANE, this.aFloatArray2190, 0);
            this.aFloatArray2190[0] = 0.0F;
            this.aFloatArray2190[1] = 0.0F;
            this.aFloatArray2190[2] = 0.0F;
            this.aFloatArray2190[3] = (float)HDToolKit.anInt1791 * 0.0050F;
            var2.glTexGenfv(GL2.GL_R, GL2.GL_EYE_PLANE, this.aFloatArray2190, 0);
            var2.glActiveTexture(GL2.GL_TEXTURE2);
         }

         var2.glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, Class72.method1297(), 0);
         if(var1 >= 0) {
            this.aFloatArray2190[0] = 0.0F;
            this.aFloatArray2190[1] = 1.0F / (float) anInt3285;
            this.aFloatArray2190[2] = 0.0F;
            this.aFloatArray2190[3] = 1.0F * (float)var1 / (float) anInt3285;
            var2.glTexGenfv(GL2.GL_S, GL2.GL_EYE_PLANE, this.aFloatArray2190, 0);
            var2.glEnable(GL2.GL_TEXTURE_GEN_S);
         } else {
            var2.glDisable(GL2.GL_TEXTURE_GEN_S);
         }

         var2.glPopMatrix();
      }

      var2.glActiveTexture(GL2.GL_TEXTURE0);
   }



}
