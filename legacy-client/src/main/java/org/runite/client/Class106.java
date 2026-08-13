package org.runite.client;

final class Class106 {

    static boolean aBoolean1441 = true;
    static int rightMargin = 0;
    static int anInt1446 = 0;
    static boolean paramUserUsingInternetExplorer = false;
    int anInt1447;
    int anInt1449;
    int anInt1450;

    static void method1642(RSString var1) {
        try {
            if (null != PacketParser.clanChatMembers) {

                long var3 = var1.toLong();
                int var2 = 0;
                if (var3 != 0L) {
                    while (PacketParser.clanChatMembers.length > var2 && var3 != PacketParser.clanChatMembers[var2].linkableKey) {
                        ++var2;
                    }

                    if (var2 < PacketParser.clanChatMembers.length && null != PacketParser.clanChatMembers[var2]) {
                        TextureOperation12.outgoingBuffer.putOpcode(162);
                        TextureOperation12.outgoingBuffer.writeLong(PacketParser.clanChatMembers[var2].linkableKey);
                    }
                }
            }
        } catch (RuntimeException var5) {
            throw ClientErrorException.clientError(var5, "od.C(" + 3803 + ',' + (var1 != null ? "{...}" : "null") + ')');
        }
    }

}
