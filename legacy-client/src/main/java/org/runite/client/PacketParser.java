package org.runite.client;

import org.rs09.SystemLogger;

public final class PacketParser {

    static int anInt80 = 0;
    static byte[][][] aByteArrayArrayArray81;
    static LinkedList aLinkedList_82 = new LinkedList();
    static short aShort83 = 32767;
    static RenderAnimationDefinition aClass16_84 = new RenderAnimationDefinition();
    static int anInt86 = 0;
    static int anInt87 = 0;
    static RSInterface aClass11_88 = null;
    static int inTutorialIsland = 0; // could be boolean
    public static Class3_Sub19[] clanChatMembers;
    static long localName37;
    static int anInt3213 = 1;


    static int method823(int var0, int var1, int var2, int var3) {
        try {
            if (var2 >= -76) {
                aShort83 = -91;
            }

            return (8 & Unsorted.sceneryTypeMaskGrid[var3][var1][var0]) == 0 ? (var3 > 0 && (Unsorted.sceneryTypeMaskGrid[1][var1][var0] & 2) != 0 ? -1 + var3 : var3) : 0;
        } catch (RuntimeException var5) {
            throw ClientErrorException.clientError(var5, "ac.G(" + var0 + ',' + var1 + ',' + var2 + ',' + var3 + ')');
        }
    }

    static void method824(long[] var0, Object[] var1, int var2) {
        try {
            Class134.method1809(var0.length - 1, var0, 122, 0, var1);
        } catch (RuntimeException var4) {
            throw ClientErrorException.clientError(var4, "ac.E(" + (var0 != null ? "{...}" : "null") + ',' + (var1 != null ? "{...}" : "null") + ',' + var2 + ')');
        }
    }

    static void method825(int var1) {
        try {
            InterfaceWidget var3 = InterfaceWidget.getWidget(1, var1);
            var3.a();
        } catch (RuntimeException var4) {
            throw ClientErrorException.clientError(var4, "ac.D(" + (byte) 92 + ',' + var1 + ')');
        }
    }

    static int method826(RSString var0, int var1) {
        try {
            if (var1 != -1) {
                method826(null, 65);
            }

            if (var0 != null) {
                for (int var2 = 0; Class8.totalFriendCount > var2; ++var2) {
                    if (var0.equalsStringIgnoreCase(Class70.friendNameString[var2])) {
                        return var2;
                    }
                }

            }
            return -1;
        } catch (RuntimeException var3) {
            throw ClientErrorException.clientError(var3, "ac.B(" + (var0 != null ? "{...}" : "null") + ',' + var1 + ')');
        }
    }

    public static boolean parseIncomingPackets() {
        return IncomingPacket.parse();
    }

    static void method829() {
        try {
            Class20.flagUpdate(Class56.aClass11_886);
            ++Class75_Sub3.anInt2658;
            if (Class21.aBoolean440 && Class85.aBoolean1167) {
                int var1 = Class126.anInt1676;
                var1 -= Unsorted.anInt1881;
                if (TextureOperation20.anInt3156 > var1) {
                    var1 = TextureOperation20.anInt3156;
                }

                int var2 = Unsorted.anInt1709;
                if (var1 - -Class56.aClass11_886.width > TextureOperation20.anInt3156 + aClass11_88.width) {
                    var1 = -Class56.aClass11_886.width + TextureOperation20.anInt3156 + aClass11_88.width;
                }

                var2 -= Class95.anInt1336;
                if (Class134.anInt1761 > var2) {
                    var2 = Class134.anInt1761;
                }

                if (Class134.anInt1761 - -aClass11_88.height < var2 - -Class56.aClass11_886.height) {
                    var2 = Class134.anInt1761 + aClass11_88.height + -Class56.aClass11_886.height;
                }

                int var4 = var2 - TileData.anInt2218;
                int var3 = var1 + -Class3_Sub15.anInt2421;
                int var6 = var1 + -TextureOperation20.anInt3156 + aClass11_88.anInt247;
                int var7 = aClass11_88.anInt208 + -Class134.anInt1761 + var2;
                int var5 = Class56.aClass11_886.anInt214;
                if (Class56.aClass11_886.anInt179 < Class75_Sub3.anInt2658 && (var3 > var5 || var3 < -var5 || var4 > var5 || var4 < -var5)) {
                    NPC.aBoolean3975 = true;
                }

                CS2Script var8;
                if (Class56.aClass11_886.anObjectArray295 != null && NPC.aBoolean3975) {
                    var8 = new CS2Script();
                    var8.aClass11_2449 = Class56.aClass11_886;
                    var8.arguments = Class56.aClass11_886.anObjectArray295;
                    var8.worldSelectCursorPositionX = var6;
                    var8.scrollbarScrollAmount = var7;
                    Class43.runCS2(var8);
                }

                if (0 == TextureOperation21.anInt3069) {
                    if (NPC.aBoolean3975) {
                        if (Class56.aClass11_886.anObjectArray229 != null) {
                            var8 = new CS2Script();
                            var8.scrollbarScrollAmount = var7;
                            var8.aClass11_2438 = Class27.aClass11_526;
                            var8.worldSelectCursorPositionX = var6;
                            var8.arguments = Class56.aClass11_886.anObjectArray229;
                            var8.aClass11_2449 = Class56.aClass11_886;
                            Class43.runCS2(var8);
                        }
                        RSInterface withInter = Class27.aClass11_526;
                        if (withInter == null) {
                            withInter = Class56.aClass11_886;
                        }
                        TextureOperation12.outgoingBuffer.putOpcode(79);
                        TextureOperation12.outgoingBuffer.writeIntV2(Class56.aClass11_886.componentHash);
                        TextureOperation12.outgoingBuffer.writeShortLE(withInter.anInt191);
                        TextureOperation12.outgoingBuffer.writeInt(withInter.componentHash);
                        TextureOperation12.outgoingBuffer.writeShortLE(Class56.aClass11_886.anInt191);

                        // && client.method42(Class56.aClass11_886) != null) {
                        if (Class27.aClass11_526 == null) {
                            System.out.println("Could not send switch item packet, second interface is null!");
                        } else if (Client.method42(Class56.aClass11_886) != null) {
                            System.out.println("Shouldn't be sending packet, enabled to fix banking tabs though.");
                        }
                    } else if ((Unsorted.anInt998 == 1 || TextureOperation8.method353(-1 + Unsorted.menuOptionCount, ~-1)) && Unsorted.menuOptionCount > 2) {
                        Class132.method1801();
                    } else if (Unsorted.menuOptionCount > 0) {
                        TextureOperation9.method203(96);
                    }

                    Class56.aClass11_886 = null;
                }

            } else if (Class75_Sub3.anInt2658 > 1) {
                Class56.aClass11_886 = null;
            }
        } catch (RuntimeException var9) {
            throw ClientErrorException.clientError(var9, "ac.F(" + -1 + ')');
        }
    }

    static void updateWidgetMedia(int var0, int var1, int var2, int var4) {
       try {
          InterfaceWidget var5 = InterfaceWidget.getWidget(4, var2);
          var5.flagUpdate();
          var5.anInt3597 = var4;
          var5.anInt3596 = var0;
          var5.anInt3598 = var1;
       } catch (RuntimeException var6) {
          throw ClientErrorException.clientError(var6, "ke.E(" + var0 + ',' + var1 + ',' + var2 + ',' + var4 + ')');
       }
    }

    static void method1605(RSString var1, int var2) {
        try {
            TextureOperation12.outgoingBuffer.putOpcode(188);
            TextureOperation12.outgoingBuffer.writeByte128(var2);
            TextureOperation12.outgoingBuffer.writeLong(var1.toLong());
        } catch (RuntimeException var4) {
            throw ClientErrorException.clientError(var4, "ni.B(" + 255 + ',' + (var1 != null ? "{...}" : "null") + ',' + var2 + ')');
        }
    }

    static void resetWorldMap() {
        try {
            Unsorted.method1250(43, false);
            System.gc();
            Class117.method1719(25);

        } catch (RuntimeException var2) {
            throw ClientErrorException.clientError(var2, "af.D(" + (byte) -86 + ')');
        }
    }

    static void addItemToContainer(int itemId, int slot, int amount, int containerId) {
        RSContainer container = (RSContainer) TileData.containerTable.get(containerId);
        if (container == null) {
            container = new RSContainer();
            TileData.containerTable.put(containerId, container);
        }

        //Resize container if necessary
        if (slot >= container.itemIds.length) {
            int[] newAmounts = new int[slot + 1];
            int[] newIds = new int[slot + 1];

            int slotIndex;
            for (slotIndex = 0; slotIndex < container.itemIds.length; ++slotIndex) {
                newIds[slotIndex] = container.itemIds[slotIndex];
                newAmounts[slotIndex] = container.amounts[slotIndex];
            }

            for (slotIndex = container.itemIds.length; slot > slotIndex; ++slotIndex) {
                newIds[slotIndex] = -1;
                newAmounts[slotIndex] = 0;
            }

            container.itemIds = newIds;
            container.amounts = newAmounts;
        }

        container.itemIds[slot] = itemId;
        container.amounts[slot] = amount;
        SystemLogger.logInfo("Adding " + amount + "x " + itemId + " to container " + containerId + " at slot " + slot);
    }
}
