package com.alex.loaders.items;

import com.alex.io.InputStream;
import com.alex.io.OutputStream;
import com.alex.store.Store;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

public class ItemDefinitions implements Cloneable {
   public int id;
   public boolean loaded;
   public int modelId;
   public String name;
   public int zoom2d;
   public int xan2d;
   public int yan2d;
   public int xOffset2d;
   public int yOffset2d;
   public int equipSlot;
   public int equipType;
   public int stackable;
   public int cost;
   public boolean membersOnly;
   public int maleEquip1;
   public int femaleEquip1;
   public int maleEquip2;
   public int femaleEquip2;
   public int maleEquipModelId3;
   public int femaleEquipModelId3;
   public String[] groundOptions;
   public String[] inventoryOptions;
   public int[] originalModelColors;
   public int[] modifiedModelColors;
   public short[] originalTextureColors;
   public short[] modifiedTextureColors;
   public byte[] recolorPalette;
   public int[] unknownArray2;
   public int[] unknownArray4;
   public int[] unknownArray5;
   public byte[] unknownArray6;
   public byte[] unknownArray3;
   public boolean unnoted;
   public int primaryMaleDialogueHead;
   public int primaryFemaleDialogueHead;
   public int secondaryMaleDialogueHead;
   public int secondaryFemaleDialogueHead;
   public int Zan2d;
   public int dummyItem;
   public int switchNoteItemId;
   public int notedItemId;
   public int[] stackIds;
   public int[] stackAmounts;
   public int floorScaleX;
   public int floorScaleY;
   public int floorScaleZ;
   public int ambience;
   public int diffusion;
   public int teamId;
   public int switchLendItemId;
   public int lendedItemId;
   public int maleWieldX;
   public int maleWieldY;
   public int maleWieldZ;
   public int femaleWieldX;
   public int femaleWieldY;
   public int femaleWieldZ;
   public int unknownInt18;
   public int unknownInt19;
   public int unknownInt20;
   public int unknownInt21;
   public int unknownInt22;
   public int unknownInt23;
   public int unknownValue1;
   public int unknownValue2;
	private int opcode218;
	private int opcode219;
   public HashMap clientScriptData;

   public static ItemDefinitions getItemDefinition(Store cache, int itemId) {
      return getItemDefinition(cache, itemId, true);
   }

   public static ItemDefinitions getItemDefinition(Store cache, int itemId, boolean load) {
      return new ItemDefinitions(cache, itemId, load);
   }

   public ItemDefinitions(Store cache, int id) {
      this(cache, id, true);
   }

   public ItemDefinitions(Store cache, int id, boolean load) {
      this.id = id;
      this.setDefaultsVariableValules();
      this.setDefaultOptions();
      if(load) {
         this.loadItemDefinition(cache);
      }

   }

   public boolean isLoaded() {
      return this.loaded;
   }

   public int getCost() {
      return this.cost;
   }

   public int getTeamId() {
      return this.teamId;
   }

   public int getStackable() {
      return this.stackable;
   }

   public boolean isUnnoted() {
      return this.unnoted;
   }

   public int getLendedItemId() {
      return this.lendedItemId;
   }

   public int getXan2d() {
      return this.xan2d;
   }

   public void setXan2d(int xan2d) {
      this.xan2d = xan2d;
   }

   public int getYan2d() {
      return this.yan2d;
   }

   public void setYan2d(int yan2d) {
      this.yan2d = yan2d;
   }

   public int getxOffset2d() {
      return this.xOffset2d;
   }

   public void setxOffset2d(int xOffset2d) {
      this.xOffset2d = xOffset2d;
   }

   public int getyOffset2d() {
      return this.yOffset2d;
   }

   public void setyOffset2d(int yOffset2d) {
      this.yOffset2d = yOffset2d;
   }

   public int getMaleEquipModelId1() {
      return this.maleEquip1;
   }

   public void setMaleEquipModelId1(int maleEquipModelId1) {
      this.maleEquip1 = maleEquipModelId1;
   }

   public int getFemaleEquipModelId1() {
      return this.femaleEquip1;
   }

   public void setFemaleEquipModelId1(int femaleEquipModelId1) {
      this.femaleEquip1 = femaleEquipModelId1;
   }

   public int getMaleEquipModelId2() {
      return this.maleEquip2;
   }

   public void setMaleEquipModelId2(int maleEquipModelId2) {
      this.maleEquip2 = maleEquipModelId2;
   }

   public int getFemaleEquipModelId2() {
      return this.femaleEquip2;
   }

   public void setFemaleEquipModelId2(int femaleEquipModelId2) {
      this.femaleEquip2 = femaleEquipModelId2;
   }

   public int getMaleEquipModelId3() {
      return this.maleEquipModelId3;
   }

   public void setMaleEquipModelId3(int maleEquipModelId3) {
      this.maleEquipModelId3 = maleEquipModelId3;
   }

   public int getFemaleEquipModelId3() {
      return this.femaleEquipModelId3;
   }

   public void setFemaleEquipModelId3(int femaleEquipModelId3) {
      this.femaleEquipModelId3 = femaleEquipModelId3;
   }

   public int[] getOriginalModelColors() {
      return this.originalModelColors;
   }

   public void setOriginalModelColors(int[] originalModelColors) {
      this.originalModelColors = originalModelColors;
   }

   public int[] getModifiedModelColors() {
      return this.modifiedModelColors;
   }

   public void setModifiedModelColors(int[] modifiedModelColors) {
      this.modifiedModelColors = modifiedModelColors;
   }

   public int[] getStackAmounts() {
      return this.stackAmounts;
   }

   public void setStackAmounts(int[] stackAmounts) {
      this.stackAmounts = stackAmounts;
   }

   public int[] getStackIds() {
      return this.stackIds;
   }

   public void setStackIds(int[] stackIds) {
      this.stackIds = stackIds;
   }

   public void setStackable(int stackable) {
      this.stackable = stackable;
   }

   public void setCost(int cost) {
      this.cost = cost;
   }

   public void setTeamId(int teamId) {
      this.teamId = teamId;
   }

   public void setMembersOnly(boolean membersOnly) {
      this.membersOnly = membersOnly;
   }

   public void setUnnoted(boolean unnoted) {
      this.unnoted = unnoted;
   }

   public void setEquipSlot(int equipSlot) {
      this.equipSlot = equipSlot;
   }

   public void setEquipType(int equipType) {
      this.equipType = equipType;
   }

   public int getSwitchLendItemId() {
      return this.switchLendItemId;
   }

   public void setSwitchLendItemId(int switchLendItemId) {
      this.switchLendItemId = switchLendItemId;
   }

   public void setLendedItemId(int lendedItemId) {
      this.lendedItemId = lendedItemId;
   }

   public int getId() {
      return this.id;
   }

   public void setId(int id) {
      this.id = id;
   }

   public void write(Store store) {
      store.getIndexes()[19].putFile(this.getArchiveId(), this.getFileId(), this.encode());
   }

   private void loadItemDefinition(Store cache) {
      byte[] data = cache.getIndexes()[19].getFile(this.getArchiveId(), this.getFileId());
      if(data != null) {
         try {
            this.readOpcodeValues(new InputStream(data));
         } catch (RuntimeException var4) {
            var4.printStackTrace();
         }

         if(this.notedItemId != -1) {
            this.toNote(cache);
         }

         if(this.lendedItemId != -1) {
            this.toLend(cache);
         }

         this.loaded = true;
      }
   }

   private void toNote(Store store) {
      ItemDefinitions realItem = getItemDefinition(store, this.switchNoteItemId);
      this.membersOnly = realItem.membersOnly;
      this.cost = realItem.cost;
      this.name = realItem.name;
      this.stackable = 1;
   }

   private void toLend(Store store) {
      ItemDefinitions realItem = getItemDefinition(store, this.switchLendItemId);
      this.originalModelColors = realItem.originalModelColors;
      this.modifiedModelColors = realItem.modifiedModelColors;
      this.teamId = realItem.teamId;
      this.cost = 0;
      this.membersOnly = realItem.membersOnly;
      this.name = realItem.name;
      this.inventoryOptions = new String[5];
      this.groundOptions = realItem.groundOptions;
      if(realItem.inventoryOptions != null) {
         System.arraycopy(realItem.inventoryOptions, 0, this.inventoryOptions, 0, 4);
      }

      this.inventoryOptions[4] = "Discard";
      this.maleEquip1 = realItem.maleEquip1;
      this.maleEquip2 = realItem.maleEquip2;
      this.femaleEquip1 = realItem.femaleEquip1;
      this.femaleEquip2 = realItem.femaleEquip2;
      this.maleEquipModelId3 = realItem.maleEquipModelId3;
      this.femaleEquipModelId3 = realItem.femaleEquipModelId3;
      this.equipType = realItem.equipType;
      this.equipSlot = realItem.equipSlot;
   }

   public int getArchiveId() {
      return this.id >>> 8;
   }

   public int getFileId() {
      return 0xff & this.id;
   }

   public boolean hasSpecialBar() {
      if(this.clientScriptData == null) {
         return false;
      } else {
         Object specialBar = this.clientScriptData.get(Integer.valueOf(686));
         return specialBar != null && specialBar instanceof Integer && ((Integer)specialBar).intValue() == 1;
      }
   }

   public int getRenderAnimId() {
      if(this.clientScriptData == null) {
         return 1426;
      } else {
         Object animId = this.clientScriptData.get(Integer.valueOf(644));
         return animId != null && animId instanceof Integer?((Integer)animId).intValue():1426;
      }
   }

   public int getQuestId() {
      if(this.clientScriptData == null) {
         return -1;
      } else {
         System.out.println(this.clientScriptData);
         Object questId = this.clientScriptData.get(Integer.valueOf(861));
         return questId != null && questId instanceof Integer?((Integer)questId).intValue():-1;
      }
   }

   public HashMap getWearingSkillRequiriments() {
      if(this.clientScriptData == null) {
         return null;
      } else {
         HashMap skills = new HashMap();
         int nextLevel = -1;
         int nextSkill = -1;
         Iterator var5 = this.clientScriptData.keySet().iterator();

         while(var5.hasNext()) {
            int key = ((Integer)var5.next()).intValue();
            Object value = this.clientScriptData.get(Integer.valueOf(key));
            if(!(value instanceof String)) {
               if(key == 23) {
                  skills.put(Integer.valueOf(4), value);
                  skills.put(Integer.valueOf(11), Integer.valueOf(61));
               } else if(key >= 749 && key < 797) {
                  if(key % 2 == 0) {
                     nextLevel = ((Integer)value).intValue();
                  } else {
                     nextSkill = ((Integer)value).intValue();
                  }

                  if(nextLevel != -1 && nextSkill != -1) {
                     skills.put(Integer.valueOf(nextSkill), Integer.valueOf(nextLevel));
                     nextLevel = -1;
                     nextSkill = -1;
                  }
               }
            }
         }

         return skills;
      }
   }

   public void printClientScriptData() {
      Iterator key2 = this.clientScriptData.keySet().iterator();

      while(key2.hasNext()) {
         int requiriments = ((Integer)key2.next()).intValue();
         Object value = this.clientScriptData.get(Integer.valueOf(requiriments));
         System.out.println("KEY: " + requiriments + ", VALUE: " + value);
      }

      HashMap requiriments1 = this.getWearingSkillRequiriments();
      if(requiriments1 == null) {
         System.out.println("null.");
      } else {
         System.out.println(requiriments1.keySet().size());
         Iterator value1 = requiriments1.keySet().iterator();

         while(value1.hasNext()) {
            int key21 = ((Integer)value1.next()).intValue();
            Object value2 = requiriments1.get(Integer.valueOf(key21));
            System.out.println("SKILL: " + key21 + ", LEVEL: " + value2);
         }

      }
   }

   private void setDefaultOptions() {
      this.groundOptions = new String[]{null, null, "Take", null, null};
      this.inventoryOptions = new String[]{null, null, null, null, "Drop"};
   }

   private void setDefaultsVariableValules() {
      this.name = "null";
      this.maleEquip1 = -1;
      this.maleEquip2 = -1;
      this.femaleEquip1 = -1;
      this.femaleEquip2 = -1;
      this.zoom2d = 2000;
      this.switchLendItemId = -1;
      this.lendedItemId = -1;
      this.switchNoteItemId = -1;
      this.notedItemId = -1;
      this.floorScaleZ = 128;
      this.floorScaleX = 128;
      this.floorScaleY = 128;
      this.cost = 1;
      this.maleEquipModelId3 = -1;
      this.femaleEquipModelId3 = -1;
      this.teamId = -1;
      this.equipType = -1;
      this.equipSlot = -1;
      this.primaryMaleDialogueHead = -1;
      this.secondaryMaleDialogueHead = -1;
      this.primaryFemaleDialogueHead = -1;
      this.secondaryFemaleDialogueHead = -1;
      this.Zan2d = 0;
   }

   public byte[] encode() {
      OutputStream stream = new OutputStream();
      stream.writeByte(1);
      stream.writeBigSmart(this.modelId);
      if(!this.name.equals("null") && this.notedItemId == -1) {
         stream.writeByte(2);
         stream.writeString(this.name);
      }

      if(this.zoom2d != 2000) {
         stream.writeByte(4);
         stream.writeShort(this.zoom2d);
      }

      if(this.xan2d != 0) {
         stream.writeByte(5);
         stream.writeShort(this.xan2d);
      }

      if(this.yan2d != 0) {
         stream.writeByte(6);
         stream.writeShort(this.yan2d);
      }

      int data;
      if(this.xOffset2d != 0) {
         stream.writeByte(7);
         int translateX = this.xOffset2d;
         if (translateX < -32767) {
            translateX += 65536;
         }
         stream.writeShort(translateX);
      }

      if(this.yOffset2d != 0) {
         stream.writeByte(8);
         int translateY = this.yOffset2d;
         if (translateY < -32767) {
            translateY += 65536;
         }
         stream.writeShort(translateY);
      }

      if(this.stackable >= 1 && this.notedItemId == -1) {
         stream.writeByte(11);
      }

      if(this.cost != 1 && this.lendedItemId == -1) {
         stream.writeByte(12);
         stream.writeInt(this.cost);
      }

      if(this.equipSlot != -1) {
         stream.writeByte(13);
         stream.writeByte(this.equipSlot);
      }

      if(this.equipType != -1) {
         stream.writeByte(14);
         stream.writeByte(this.equipType);
      }

      if(this.membersOnly && this.notedItemId == -1) {
         stream.writeByte(16);
      }

      if(this.maleEquip1 != -1) {
         stream.writeByte(23);
         stream.writeBigSmart(this.maleEquip1);
      }

      if(this.maleEquip2 != -1) {
         stream.writeByte(24);
         stream.writeBigSmart(this.maleEquip2);
      }

      if(this.femaleEquip1 != -1) {
         stream.writeByte(25);
         stream.writeBigSmart(this.femaleEquip1);
      }

      if(this.femaleEquip2 != -1) {
         stream.writeByte(26);
         stream.writeBigSmart(this.femaleEquip2);
      }

      for(int index = 0; index < 5; index++) {
         String option = this.groundOptions[index];
         if ((index == 5 && option.equals("Examine")) || (index == 2 && option.equals("Take")) || option == null) {
            continue;
         }
         stream.writeByte(30 + index);
         stream.writeString(this.groundOptions[index]);
      }

      for(int index = 0; index < 5; index++) {
         String option = this.inventoryOptions[index];
         if (index == 4 && option.equals("Drop") || option == null) {
            continue;
         }
         stream.writeByte(35 + index);
         stream.writeString(this.inventoryOptions[index]);
      }

      if(this.originalModelColors != null && this.modifiedModelColors != null) {
         stream.writeByte(40);
         stream.writeByte(this.originalModelColors.length);

         for(data = 0; data < this.originalModelColors.length; ++data) {
            stream.writeShort(this.originalModelColors[data]);
            stream.writeShort(this.modifiedModelColors[data]);
         }
      }

      if(this.originalTextureColors != null && this.modifiedTextureColors != null) {
         stream.writeByte(41);
         stream.writeByte(this.originalTextureColors.length);

         for(data = 0; data < this.originalTextureColors.length; ++data) {
            stream.writeShort(this.originalTextureColors[data]);
            stream.writeShort(this.modifiedTextureColors[data]);
         }
      }

      if(this.recolorPalette != null) {
         stream.writeByte(42);
         stream.writeByte(this.recolorPalette.length);

         for(data = 0; data < this.recolorPalette.length; ++data) {
            stream.writeByte(this.recolorPalette[data]);
         }
      }

      if(this.unnoted) {
         stream.writeByte(65);
      }

      if(this.maleEquipModelId3 != -1) {
         stream.writeByte(78);
         stream.writeBigSmart(this.maleEquipModelId3);
      }

      if(this.femaleEquipModelId3 != -1) {
         stream.writeByte(79);
         stream.writeBigSmart(this.femaleEquipModelId3);
      }

      if (this.primaryMaleDialogueHead != -1) {
         stream.writeByte(90);
         stream.writeBigSmart(this.primaryMaleDialogueHead);
      }

      if (this.primaryFemaleDialogueHead != -1) {
         stream.writeByte(91);
         stream.writeBigSmart(this.primaryFemaleDialogueHead);
      }

      if (this.secondaryMaleDialogueHead != -1) {
         stream.writeByte(92);
         stream.writeBigSmart(this.secondaryMaleDialogueHead);
      }

      if (this.secondaryFemaleDialogueHead != -1) {
         stream.writeByte(93);
         stream.writeBigSmart(this.secondaryFemaleDialogueHead);
      }

      if (this.Zan2d != 0) {
         stream.writeByte(95);
         stream.writeShort(this.Zan2d);
      }

      if(this.switchNoteItemId != -1) {
         stream.writeByte(97);
         stream.writeShort(this.switchNoteItemId);
      }

      if(this.notedItemId != -1) {
         stream.writeByte(98);
         stream.writeShort(this.notedItemId);
      }

      if(this.stackIds != null && this.stackAmounts != null) {
         for(data = 0; data < this.stackIds.length; ++data) {
            if(this.stackIds[data] != 0 || this.stackAmounts[data] != 0) {
               stream.writeByte(100 + data);
               stream.writeShort(this.stackIds[data]);
               stream.writeShort(this.stackAmounts[data]);
            }
         }
      }

      if(this.floorScaleX != 128) {
         stream.writeByte(110);
         stream.writeShort(this.floorScaleX);
      }

      if(this.floorScaleY != 128) {
         stream.writeByte(111);
         stream.writeShort(this.floorScaleY);
      }

      if(this.floorScaleZ != 128) {
         stream.writeByte(112);
         stream.writeShort(this.floorScaleZ);
      }

      if(this.ambience != 0) {
         stream.writeByte(113);
         stream.writeByte(this.ambience);
      }

      if(this.diffusion != 0) {
         stream.writeByte(114);
         stream.writeByte(this.diffusion);
      }

      if(this.teamId != 0) {
         stream.writeByte(115);
         stream.writeByte(this.teamId);
      }

      if(this.switchLendItemId != -1) {
         stream.writeByte(121);
         stream.writeShort(this.switchLendItemId);
      }

      if(this.lendedItemId != -1) {
         stream.writeByte(122);
         stream.writeShort(this.lendedItemId);
      }

      if(this.maleWieldX != 0 || this.maleWieldY != 0 || this.maleWieldZ != 0) {
         stream.writeByte(125);
         stream.writeByte(this.maleWieldX);
         stream.writeByte(this.maleWieldY);
         stream.writeByte(this.maleWieldZ);
      }

      if(this.femaleWieldX != 0 || this.femaleWieldY != 0 || this.femaleWieldZ != 0) {
         stream.writeByte(126);
         stream.writeByte(this.femaleWieldX);
         stream.writeByte(this.femaleWieldY);
         stream.writeByte(this.femaleWieldZ);
      }

      if(this.unknownArray2 != null) {
         stream.writeByte(132);
         stream.writeByte(this.unknownArray2.length);

         for(data = 0; data < this.unknownArray2.length; ++data) {
            stream.writeShort(this.unknownArray2[data]);
         }
      }

      if(this.clientScriptData != null) {
         stream.writeByte(249);
         stream.writeByte(this.clientScriptData.size());
         Iterator var5 = this.clientScriptData.keySet().iterator();

         while(var5.hasNext()) {
            data = ((Integer)var5.next()).intValue();
            Object value2 = this.clientScriptData.get(Integer.valueOf(data));
            stream.writeByte(value2 instanceof String?1:0);
            stream.write24BitInt(data);
            if(value2 instanceof String) {
               stream.writeString((String)value2);
            } else {
               stream.writeInt(((Integer)value2).intValue());
            }
         }
      }

      stream.writeByte(0);
      byte[] var6 = new byte[stream.getOffset()];
      stream.setOffset(0);
      stream.getBytes(var6, 0, var6.length);
      return var6;
   }

   public int getInvModelId() {
      return this.modelId;
   }

   public void setInvModelId(int modelId) {
      this.modelId = modelId;
   }

   public int getInvModelZoom() {
      return this.zoom2d;
   }

   public void setInvModelZoom(int modelZoom) {
      this.zoom2d = modelZoom;
   }

   private final void readValues(InputStream stream, int opcode) {
      if(opcode == 1) {
         this.modelId = stream.readUnsignedShort();//stream.readBigSmart();
      } else if(opcode == 2) {
         this.name = stream.readString();
      } else if(opcode == 4) {
         this.zoom2d = stream.readUnsignedShort();
      } else if(opcode == 5) {
         this.xan2d = stream.readUnsignedShort();
      } else if(opcode == 6) {
         this.yan2d = stream.readUnsignedShort();
      } else if(opcode == 7) {
         this.xOffset2d = stream.readUnsignedShort();
         if(this.xOffset2d > Short.MAX_VALUE) {
         //if(this.modelOffset1 > 32767) {
            this.xOffset2d -= 65536;
         }

      } else if(opcode == 8) {
         this.yOffset2d = stream.readUnsignedShort();
         if(this.yOffset2d > Short.MAX_VALUE) {
         //if(this.modelOffset2 > 32767) {
            this.yOffset2d -= 65536;
         }

      } else if(opcode == 11) {
         this.stackable = 1;
      } else if(opcode == 12) {
         this.cost = stream.readInt();
      } else if(opcode == 13) {
         this.equipSlot = stream.readUnsignedByte();
      } else if(opcode == 14) {
         this.equipType = stream.readUnsignedByte();
      } else if(opcode == 16) {
         this.membersOnly = true;
      } else if(opcode == 18) {
         stream.readUnsignedShortLE();
      } else if(opcode == 23) {
         this.maleEquip1 = stream.readUnsignedShort();//stream.readBigSmart();
      } else if(opcode == 24) {
         this.maleEquip2 = stream.readUnsignedShort();//stream.readBigSmart();
      } else if(opcode == 25) {
         this.femaleEquip1 = stream.readUnsignedShort();//stream.readBigSmart();
      } else if(opcode == 26) {
         this.femaleEquip2 = stream.readUnsignedShort();//stream.readBigSmart();
      } else if(opcode == 27) {
         stream.readUnsignedByte();
      } else if(opcode >= 30 && opcode < 35) {
         this.groundOptions[opcode - 30] = stream.readString();
      } else if(opcode >= 35 && opcode < 40) {
         this.inventoryOptions[opcode - 35] = stream.readString();
      } else {
         int length;
         int index;
         if(opcode == 40) {
            length = stream.readUnsignedByte();
            this.originalModelColors = new int[length];
            this.modifiedModelColors = new int[length];

            for(index = 0; index < length; ++index) {
               this.originalModelColors[index] = stream.readUnsignedShort();
               this.modifiedModelColors[index] = stream.readUnsignedShort();
            }
         } else if(opcode == 41) {
            length = stream.readUnsignedByte();
            this.originalTextureColors = new short[length];
            this.modifiedTextureColors = new short[length];

            for(index = 0; index < length; ++index) {
               this.originalTextureColors[index] = (short)stream.readUnsignedShort();
               this.modifiedTextureColors[index] = (short)stream.readUnsignedShort();
            }
         } else if(opcode == 42) {
            length = stream.readUnsignedByte();
            this.recolorPalette = new byte[length];

            for(index = 0; index < length; ++index) {
               this.recolorPalette[index] = (byte)stream.readByte();
            }
         } else if(opcode == 65) {
            this.unnoted = true;
         } else if(opcode == 78) {
            this.maleEquipModelId3 = stream.readUnsignedShort();//stream.readBigSmart();
         } else if(opcode == 79) {
            this.femaleEquipModelId3 = stream.readUnsignedShort();//stream.readBigSmart();
         } else if(opcode == 90) {
            this.primaryMaleDialogueHead = stream.readUnsignedShort();//stream.readBigSmart();
         } else if(opcode == 91) {
            this.primaryFemaleDialogueHead = stream.readUnsignedShort();//stream.readBigSmart();
         } else if(opcode == 92) {
            this.secondaryMaleDialogueHead = stream.readUnsignedShort();//stream.readBigSmart();
         } else if(opcode == 93) {
            this.secondaryFemaleDialogueHead = stream.readUnsignedShort();//stream.readBigSmart();
         } else if(opcode == 95) {
            this.Zan2d = stream.readUnsignedShort();
         } else if(opcode == 96) {
            this.dummyItem = stream.readUnsignedByte();
         } else if(opcode == 97) {
            this.switchNoteItemId = stream.readUnsignedShort();
         } else if(opcode == 98) {
            this.notedItemId = stream.readUnsignedShort();
         } else if(opcode >= 100 && opcode < 110) {
            if(this.stackIds == null) {
               this.stackIds = new int[10];
               this.stackAmounts = new int[10];
            }

            this.stackIds[opcode - 100] = stream.readUnsignedShort();
            this.stackAmounts[opcode - 100] = stream.readUnsignedShort();
         } else if(opcode == 110) {
            this.floorScaleX = stream.readUnsignedShort();
         } else if(opcode == 111) {
            this.floorScaleY = stream.readUnsignedShort();
         } else if(opcode == 112) {
            this.floorScaleZ = stream.readUnsignedShort();
         } else if(opcode == 113) {
            this.ambience = stream.readByte();
         } else if(opcode == 114) {
            this.diffusion = stream.readByte();
         } else if(opcode == 115) {
            this.teamId = stream.readUnsignedByte();
         } else if(opcode == 121) {
            this.switchLendItemId = stream.readUnsignedShort();
         } else if(opcode == 122) {
            this.lendedItemId = stream.readUnsignedShort();
         } else if(opcode == 125) {
            this.maleWieldX = stream.readByte();
            this.maleWieldY = stream.readByte();
            this.maleWieldZ = stream.readByte();
         } else if(opcode == 126) {
            this.femaleWieldX = stream.readByte();
            this.femaleWieldY = stream.readByte();
            this.femaleWieldZ = stream.readByte();
         } else if(opcode == 127) {
            this.unknownInt18 = stream.readUnsignedByte();
            this.unknownInt19 = stream.readUnsignedShort();
         } else if(opcode == 128) {
            this.unknownInt20 = stream.readUnsignedByte();
            this.unknownInt21 = stream.readUnsignedShort();
         } else if(opcode == 129) {
            this.unknownInt20 = stream.readUnsignedByte();
            this.unknownInt21 = stream.readUnsignedShort();
         } else if(opcode == 130) {
            this.unknownInt22 = stream.readUnsignedByte();
            this.unknownInt23 = stream.readUnsignedShort();
         } else if(opcode == 132) {
            length = stream.readUnsignedByte();
            this.unknownArray2 = new int[length];

            for(index = 0; index < length; ++index) {
               this.unknownArray2[index] = stream.readUnsignedShort();
            }
         } else if(opcode == 134) {
            stream.readUnsignedByte();
         } else if(opcode == 139) {
            this.unknownValue2 = stream.readUnsignedShort();
         } else if(opcode == 140) {
            this.unknownValue1 = stream.readUnsignedShort();
		}else if (opcode == 191) {
            //int opcode191 = 0;
		}else if (opcode == 218) {
            //int opcode218 = 0;
		}else if (opcode == 219) {
            //int opcode219 = 0;
         } else if(opcode == 249) {
            length = stream.readUnsignedByte();
            if(this.clientScriptData == null) {
               this.clientScriptData = new HashMap(length);
            }

            for(index = 0; index < length; ++index) {
               boolean stringInstance = stream.readUnsignedByte() == 1;
               int key = stream.read24BitInt();
               Object value = stringInstance?stream.readString():Integer.valueOf(stream.readInt());
               this.clientScriptData.put(Integer.valueOf(key), value);
            }
         //}
         } else {
               throw new RuntimeException("MISSING OPCODE " + opcode + " FOR ITEM " + this.id);
            }
      }

   }

   private void readOpcodeValues(InputStream stream) {
      while(true) {
         int opcode = stream.readUnsignedByte();
         if(opcode == 0) {
            return;
         }

         this.readValues(stream, opcode);
      }
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public void resetTextureColors() {
      this.originalTextureColors = null;
      this.modifiedTextureColors = null;
   }

   public boolean isWearItem() {
      return this.equipSlot != -1;
   }

   public boolean isMembersOnly() {
      return this.membersOnly;
   }

   public void changeTextureColor(short originalModelColor, short modifiedModelColor) {
      if(this.originalTextureColors != null) {
         for(int newOriginalModelColors = 0; newOriginalModelColors < this.originalTextureColors.length; ++newOriginalModelColors) {
            if(this.originalTextureColors[newOriginalModelColors] == originalModelColor) {
               this.modifiedTextureColors[newOriginalModelColors] = modifiedModelColor;
               return;
            }
         }

         short[] var5 = Arrays.copyOf(this.originalTextureColors, this.originalTextureColors.length + 1);
         short[] newModifiedModelColors = Arrays.copyOf(this.modifiedTextureColors, this.modifiedTextureColors.length + 1);
         var5[var5.length - 1] = originalModelColor;
         newModifiedModelColors[newModifiedModelColors.length - 1] = modifiedModelColor;
         this.originalTextureColors = var5;
         this.modifiedTextureColors = newModifiedModelColors;
      } else {
         this.originalTextureColors = new short[]{originalModelColor};
         this.modifiedTextureColors = new short[]{modifiedModelColor};
      }

   }

   public void resetModelColors() {
      this.originalModelColors = null;
      this.modifiedModelColors = null;
   }

   public void changeModelColor(int originalModelColor, int modifiedModelColor) {
      if(this.originalModelColors != null) {
         for(int newOriginalModelColors = 0; newOriginalModelColors < this.originalModelColors.length; ++newOriginalModelColors) {
            if(this.originalModelColors[newOriginalModelColors] == originalModelColor) {
               this.modifiedModelColors[newOriginalModelColors] = modifiedModelColor;
               return;
            }
         }

         int[] var5 = Arrays.copyOf(this.originalModelColors, this.originalModelColors.length + 1);
         int[] newModifiedModelColors = Arrays.copyOf(this.modifiedModelColors, this.modifiedModelColors.length + 1);
         var5[var5.length - 1] = originalModelColor;
         newModifiedModelColors[newModifiedModelColors.length - 1] = modifiedModelColor;
         this.originalModelColors = var5;
         this.modifiedModelColors = newModifiedModelColors;
      } else {
         this.originalModelColors = new int[]{originalModelColor};
         this.modifiedModelColors = new int[]{modifiedModelColor};
      }

   }

   public String[] getGroundOptions() {
      return this.groundOptions;
   }

   public String[] getInventoryOptions() {
      return this.inventoryOptions;
   }

   public int getEquipSlot() {
      return this.equipSlot;
   }

   public int getEquipType() {
      return this.equipType;
   }

   public Object clone() {
      try {
         return super.clone();
      } catch (CloneNotSupportedException var2) {
         var2.printStackTrace();
         return null;
      }
   }

   public String toString() {
      return this.id + " - " + this.name;
   }
}
