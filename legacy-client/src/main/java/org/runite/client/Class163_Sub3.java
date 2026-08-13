package org.runite.client;

import java.util.Objects;

final class Class163_Sub3 extends Class163 {

    static int[] anIntArray2999;
    static RSString[] aStringArray3003 = new RSString[100];
    static boolean aBoolean3004 = true;
    static byte[][] aByteArrayArray3005;
    static int[] anIntArray3007 = new int[]{-1, -1, 1, 1};


    static void method2229(long var0) {
        try {
            if (var0 != 0) {
                if ((100 > Class8.totalFriendCount || TextureOperation3.disableGEBoxes) && Class8.totalFriendCount < 200) {
                    RSString var3 = Objects.requireNonNull(Unsorted.decodeBase37(var0)).longToRSString();

                    int var4;
                    for (var4 = 0; Class8.totalFriendCount > var4; ++var4) {
                        if (var0 == Class50.friendNameLong[var4]) {
                            BufferedDataStream.addChat(RSString.parse(""), 0, RSString.stringCombiner(new RSString[]{var3, TextCore.HasFriendsAlready}), -1);
                            return;
                        }
                    }

                    for (var4 = 0; var4 < Class3_Sub28_Sub5.ignoreCount; ++var4) {
                        if (Class114.ignoreList[var4] == var0) {
                            BufferedDataStream.addChat(RSString.parse(""), 0, RSString.stringCombiner(new RSString[]{TextCore.HasPleaseRemove, var3, TextCore.HasIgnoreToFriends}), -1);
                            return;
                        }
                    }

                    if (var3.equalsString(Class102.player.displayName)) {
                        BufferedDataStream.addChat(RSString.parse(""), 0, TextCore.HasOnOwnFriendsList, -1);
                    } else {
                        Class70.friendNameString[Class8.totalFriendCount] = var3;
                        Class50.friendNameLong[Class8.totalFriendCount] = var0;
                        Unsorted.friendWorldId[Class8.totalFriendCount] = 0;
                        Unsorted.friendWorldName[Class8.totalFriendCount] = RSString.parse("");
                        Class57.anIntArray904[Class8.totalFriendCount] = 0;
                        Unsorted.aBooleanArray73[Class8.totalFriendCount] = false;
                        ++Class8.totalFriendCount;
                        Class110.socialUpdateTick = PacketParser.anInt3213;
                        TextureOperation12.outgoingBuffer.putOpcode(120);
                        TextureOperation12.outgoingBuffer.writeLong(var0);
                    }
                } else {
                    BufferedDataStream.addChat(RSString.parse(""), 0, TextCore.HasFriendsListFull, -1);
                }
            }
        } catch (RuntimeException var5) {
            throw ClientErrorException.clientError(var5, "fb.C(" + var0 + ',' + (byte) -91 + ')');
        }
    }

}
