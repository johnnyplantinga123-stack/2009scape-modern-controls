package org.runite.client;

import org.rs09.Discord;
import org.rs09.SlayerTracker;
import org.rs09.XPGainDraw;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class IncomingPacket {
    // If you see "NXT naming," it is a placeholder to see which ones don't need any further work

    /**
     * Named And Identified
     */
    /// map
    static final int UPDATE_CURRENT_LOCATION = 26;
    static final int CLEAR_GROUND_ITEMS = 112;
    static final int UPDATE_SCENE_GRAPH = 162;
    static final int DYNAMIC_SCENE_GRAPH = 214;
    static final int BATCH_LOCATION_PACKET = 230;

    // TODO: these are the batched packets that modify projectiles, locs, etc.
    static final int LOCATION_PACKET_14 = 14;
    static final int LOCATION_PACKET_16 = 16;
    static final int LOCATION_PACKET_17 = 17;
    static final int LOCATION_PACKET_20 = 20;
    static final int LOCATION_PACKET_33 = 33;
    static final int LOCATION_PACKET_97 = 97;
    static final int LOCATION_PACKET_104 = 104;
    static final int LOCATION_PACKET_121 = 121;
    static final int LOCATION_PACKET_135 = 135;
    static final int LOCATION_PACKET_179 = 179;
    static final int LOCATION_PACKET_195 = 195;
    static final int LOCATION_PACKET_202 = 202;
    static final int LOCATION_PACKET_240 = 240;

    /// updates
    static final int NPC_INFO = 32; // NXT naming
    static final int PLAYER_INFO = 225; // NXT naming

    /// var{p,c,bit}
    static final int VARBIT_SMALL = 37; // NXT naming
    static final int VARBIT_LARGE = 84; // NXT naming
    static final int VARP_SMALL = 60; // NXT naming
    static final int VARP_LARGE = 226; // NXT naming
    static final int CLIENT_SETVARC_SMALL = 65; // NXT naming
    static final int CLIENT_SETVARC_LARGE = 69; // NXT naming
    static final int RESET_CLIENT_VARCACHE = 89; // NXT naming (?)
    static final int FORCE_VARP_REFRESH = 128;

    /// chat
    static final int MESSAGE_GAME = 70; // NXT naming
    static final int IF_SETCOLOUR = 2; // NXT naming
    static final int CHAT_FILTER_SETTINGS = 232; // NXT naming

    static final int MESSAGE_PRIVATE = 0; // NXT naming
    static final int MESSAGE_QUICKCHAT_PRIVATE = 247; // NXT naming

    static final int MESSAGE_PRIVATE_ECHO = 71; // NXT naming
    static final int MESSAGE_QUICKCHAT_PRIVATE_ECHO = 141; // NXT naming

    static final int MESSAGE_CLANCHANNEL = 54; // NXT naming
    static final int CLAN_QUICK_CHAT = 81;

    /// {friends,ignore,clan} packets
    static final int JOIN_CLAN_CHAT = 55;
    static final int FRIENDLIST_LOADED = 197; // NXT naming (?)

    static final int UPDATE_FRIENDLIST = 62; // NXT naming (?)
    static final int UPDATE_IGNORELIST = 126; // NXT naming

    /// interfaces
    static final int IF_SETHIDE = 21; // NXT naming (?)
    static final int IF_SETANIM = 36; // NXT naming (?)
    static final int IF_SETOBJECT = 50; // NXT naming
    static final int IF_SETPLAYERHEAD = 66; // NXT naming (?) - double check if this is correct or if it's IF_SETPLAYERMODEL_SELF etc
    static final int IF_SETNPCHEAD = 73; // NXT naming
    static final int IF_SETMODEL = 130; // NXT naming
    static final int IF_SETPOSITION = 119; // NXT naming
    static final int IF_SETANGLE = 132; // NXT naming
    static final int IF_OPENSUB = 145; // NXT naming (?)
    static final int IF_CLOSESUB = 149; // NXT naming (?)
    static final int IF_OPENTOP = 155; // NXT naming (?)
    static final int SET_INTERFACE_SETTINGS = 165;
    static final int IF_SETSCROLLPOS = 220; // NXT naming
    // none of these are exactly NXT naming, need to identify differences and when one would be used over the other
    static final int IF_SETTEXT1 = 171;
    static final int IF_SETTEXT2 = 48;
    static final int IF_SETTEXT3 = 123;
    // no clue on original names... yet
    static final int WIDGETSTRUCT_SETTING = 9;
    static final int INTERFACE_ANIMATE_ROTATE = 207;
    static final int GAME_FRAME_UNK = 209;
    static final int DELETE_INVENTORY = 191;
    static final int SWITCH_WIDGET = 176;
    static final int GENERATE_CHAT_HEAD_FROM_BODY = 111;

    /// inventory
    static final int INTERFACE_ITEMS_CLEAR = 144;
    static final int UPDATE_INV_PARTIAL = 22; // NXT naming
    static final int UPDATE_INV_FULL = 105; // NXT naming

    /// misc
    static final int LOGOUT = 86; // NXT naming
    static final int UPDATE_RUNENERGY = 234; // NXT naming
    static final int UPDATE_RUNWEIGHT = 159; // NXT naming (?)
    static final int UPDATE_REBOOT_TIMER = 85; // NXT naming
    static final int UPDATE_STAT = 38; // NXT naming
    static final int MIDI_SONG = 4; // NXT naming
    static final int MIDI_JINGLE = 208; // NXT naming
    static final int SYNTH_SOUND = 172; // NXT naming
    static final int HINT_ARROW = 217; // NXT naming (?)
    static final int UPDATE_UID192 = 169; // NXT naming (?)
    static final int URL_OPEN = 42; // NXT naming
    static final int CAM_RESET = 24; // NXT naming
    static final int CAM_SHAKE = 27; // NXT naming
    static final int SPOTANIM_SPECIFIC = 56; // NXT naming
    static final int NPC_ANIM_SPECIFIC = 102; // NXT naming
    static final int CAM_LOOKAT = 125; // NXT naming
    static final int RESET_ANIMS = 131; // NXT naming
    static final int LAST_LOGIN_INFO = 164; // NXT naming (?)
    static final int CAM_FORCEANGLE = 187; // NXT naming
    static final int LOC_ANIM_SPECIFIC = 235; // NXT naming

    // TODO:
    static final int RUN_CS2 = 115;
    static final int SET_INTERACTION = 44;
    static final int GRAND_EXCHANGE_OFFERS = 116;
    static final int CLEAR_MINIMAP_FLAG = 153;
    static final int SET_MINIMAP_STATE = 192;
    static final int REFLECTION_CHEAT_CHECK = 114;
    static final int TELEPORT_LOCAL_PLAYER = 13;
    static final int SET_SETTINGS_STRING = 142;
    static final int SET_WALK_TEXT = 160;
    static final int CAMERA_DETACH = 154;
    static final int UPDATE_CLAN = 196;

    public static boolean parse()
    {
        int opcode = Unsorted.packetOpcode;
        int inTutorialIsland = PacketParser.inTutorialIsland;

        if(opcode == VARP_SMALL)
        {
            int id = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            byte value = BufferedDataStream.incomingBuffer.readSignedNegativeByte();
            VarpHelpers.setVarp(value, id);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == RUN_CS2)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            RSString argTypes = BufferedDataStream.incomingBuffer.readString();
            Object[] scriptArgs = new Object[argTypes.length() - -1];
            for (int counter = argTypes.length() + -1; counter >= 0; --counter) {
                if ('s' == argTypes.charAt(counter)) {
                    scriptArgs[1 + counter] = BufferedDataStream.incomingBuffer.readString();
                } else {
                    scriptArgs[1 + counter] = BufferedDataStream.incomingBuffer.readInt();
                }
            }

            scriptArgs[0] = BufferedDataStream.incomingBuffer.readInt(); // script ID
            Class146.setPacketTracker(tracknum);
            CS2Script script = new CS2Script();
            script.arguments = scriptArgs;
            Class43.runCS2(script);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == MESSAGE_GAME)
        {
            RSString message = BufferedDataStream.incomingBuffer.readString();
            RSString playerName;
            RSString var41;
            long nameAsLong;
            boolean isIgnored;
            int n;
            if (message.endsWith(TextCore.cmdTradeReq)) {
                playerName = message.substring(0, message.indexOf(RSString.parse(":"), 65), 0);
                nameAsLong = playerName.toLong();
                isIgnored = false;

                for (n = 0; Class3_Sub28_Sub5.ignoreCount > n; ++n) {
                    if (nameAsLong == Class114.ignoreList[n]) {
                        isIgnored = true;
                        break;
                    }
                }

                if (!isIgnored && inTutorialIsland == 0) {
                    BufferedDataStream.addChat(playerName, 4, TextCore.HasWishToTrade, (byte) -83 + 82);
                }
            } else if (message.endsWith(TextCore.cmdChalReq)) {
                playerName = message.substring(0, message.indexOf(RSString.parse(":"), 75), 0);
                nameAsLong = playerName.toLong();
                isIgnored = false;

                for (n = 0; n < Class3_Sub28_Sub5.ignoreCount; ++n) {
                    if (Class114.ignoreList[n] == nameAsLong) {
                        isIgnored = true;
                        break;
                    }
                }

                if (!isIgnored && inTutorialIsland == 0) {
                    var41 = message.substring(1 + message.indexOf(RSString.parse(":"), 101), message.length() + -9, (byte) -83 ^ -83);
                    BufferedDataStream.addChat(playerName, 8, var41, (byte) -83 ^ 82);
                }
            } else if (message.endsWith(TextCore.HasAssistRequest)) {
                isIgnored = false;
                playerName = message.substring(0, message.indexOf(RSString.parse(":"), 96), 0);
                nameAsLong = playerName.toLong();

                for (n = 0; n < Class3_Sub28_Sub5.ignoreCount; ++n) {
                    if (nameAsLong == Class114.ignoreList[n]) {
                        isIgnored = true;
                        break;
                    }
                }

                if (!isIgnored && inTutorialIsland == 0) {
                    BufferedDataStream.addChat(playerName, 10, RSString.parse(""), -1);
                }
            } else if (message.endsWith(TextCore.HasClan)) {
                playerName = message.substring(0, message.indexOf(TextCore.HasClan, (byte) -83 ^ -50), 0);
                BufferedDataStream.addChat(RSString.parse(""), 11, playerName, -1);
            } else if (message.endsWith(TextCore.HasTrade)) {
                playerName = message.substring(0, message.indexOf(TextCore.HasTrade, 102), 0);
                if (0 == inTutorialIsland) {
                    BufferedDataStream.addChat(RSString.parse(""), 12, playerName, -1);
                }
            } else if (message.endsWith(TextCore.HasAssist)) {
                playerName = message.substring(0, message.indexOf(TextCore.HasAssist, 121), 0);
                if (inTutorialIsland == 0) {
                    BufferedDataStream.addChat(RSString.parse(""), 13, playerName, -1);
                }
            } else if (message.endsWith(TextCore.HasDuelStake)) {
                isIgnored = false;
                playerName = message.substring(0, message.indexOf(RSString.parse(":"), 115), 0);
                nameAsLong = playerName.toLong();

                for (n = 0; Class3_Sub28_Sub5.ignoreCount > n; ++n) {
                    if (nameAsLong == Class114.ignoreList[n]) {
                        isIgnored = true;
                        break;
                    }
                }

                if (!isIgnored && inTutorialIsland == 0) {
                    BufferedDataStream.addChat(playerName, 14, RSString.parse(""), -1);
                }
            } else if (message.endsWith(TextCore.HasDuelFriend)) {
                playerName = message.substring(0, message.indexOf(RSString.parse(":"), 118), 0);
                isIgnored = false;
                nameAsLong = playerName.toLong();

                for (n = 0; n < Class3_Sub28_Sub5.ignoreCount; ++n) {
                    if (nameAsLong == Class114.ignoreList[n]) {
                        isIgnored = true;
                        break;
                    }
                }

                if (!isIgnored && 0 == inTutorialIsland) {
                    BufferedDataStream.addChat(playerName, 15, RSString.parse(""), -1);
                }
            } else if (message.endsWith(TextCore.HasClanRequest)) {
                playerName = message.substring(0, message.indexOf(RSString.parse(":"), (byte) -83 + 138), 0);
                nameAsLong = playerName.toLong();
                isIgnored = false;

                for (n = 0; n < Class3_Sub28_Sub5.ignoreCount; ++n) {
                    if (Class114.ignoreList[n] == nameAsLong) {
                        isIgnored = true;
                        break;
                    }
                }

                if (!isIgnored && inTutorialIsland == 0) {
                    BufferedDataStream.addChat(playerName, 16, RSString.parse(""), -1);
                }
            } else if (message.endsWith(TextCore.HasAllyReq)) {
                playerName = message.substring(0, message.indexOf(RSString.parse(":"), (byte) -83 + 189), (byte) -83 + 83);
                isIgnored = false;
                nameAsLong = playerName.toLong();

                for (n = 0; n < Class3_Sub28_Sub5.ignoreCount; ++n) {
                    if (nameAsLong == Class114.ignoreList[n]) {
                        isIgnored = true;
                        break;
                    }
                }

                if (!isIgnored && inTutorialIsland == 0) {
                    var41 = message.substring(1 + message.indexOf(RSString.parse(":"), 92), message.length() - 9, (byte) -83 ^ -83);
                    BufferedDataStream.addChat(playerName, 21, var41, -1);
                }
            } else {
                BufferedDataStream.addChat(RSString.parse(""), 0, message, (byte) -83 + 82);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETTEXT3)
        {
            int id = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            RSString value = BufferedDataStream.incomingBuffer.readString();

            Class146.setPacketTracker(tracknum);
            InterfaceWidget.setWidgetText(value, id);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == BATCH_LOCATION_PACKET)
        {
            Class39.currentChunkY = BufferedDataStream.incomingBuffer.readUnsignedByte128();
            Class39.currentChunkX = BufferedDataStream.incomingBuffer.readUnsigned128Byte();

            while (BufferedDataStream.incomingBuffer.index < Unsorted.packetSize) {
                Unsorted.packetOpcode = BufferedDataStream.incomingBuffer.readUnsignedByte();
                Class39.parseLocationPacket((byte) -82);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CLEAR_MINIMAP_FLAG)
        {
            Unsorted.packetOpcode = -1;
            Class65.mapFlagX = 0;
            return true;
        }

        if(opcode == IF_SETSCROLLPOS)
        {
            int id = BufferedDataStream.incomingBuffer.readIntV2();
            int pos = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();

            Class146.setPacketTracker(tracknum);
            TextureOperation29.update12(pos, id);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CLAN_QUICK_CHAT)
        {
            long username = BufferedDataStream.incomingBuffer.readLong();
            BufferedDataStream.incomingBuffer.readSignedByte();
            long clanName = BufferedDataStream.incomingBuffer.readLong();
            int top = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int bot = BufferedDataStream.incomingBuffer.readMedium();
            int rights = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int quickChatId = BufferedDataStream.incomingBuffer.readUnsignedShort();

            boolean ignored = false;
            long messageId = ((long)top << 32) + bot;
            int n = 0;

            check:
            while (true) {
                if (100 > n) {
                    if (Class163_Sub2_Sub1.recentMessages[n] != messageId) {
                        ++n;
                        continue;
                    }

                    ignored = true;
                    break;
                }

                if (1 >= rights) {
                    for (n = 0; n < Class3_Sub28_Sub5.ignoreCount; ++n) {
                        if (username == Class114.ignoreList[n]) {
                            ignored = true;
                            break check;
                        }
                    }
                }
                break;
            }

            if (!ignored && 0 == inTutorialIsland) {
                Class163_Sub2_Sub1.recentMessages[MouseListeningClass.messageCounter] = messageId;
                MouseListeningClass.messageCounter = (1 + MouseListeningClass.messageCounter) % 100;
                RSString chat = QuickChat.load(quickChatId).toChat(BufferedDataStream.incomingBuffer);
                if (rights == 2 || 3 == rights) {
                    MessageManager.addChat(quickChatId, 20, chat, Objects.requireNonNull(Unsorted.decodeBase37(clanName)).longToRSString(), RSString.stringCombiner(new RSString[]{RSString.parse("<img=1>"), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString()}));
                } else if (rights == 1) {
                    MessageManager.addChat(quickChatId, 20, chat, Objects.requireNonNull(Unsorted.decodeBase37(clanName)).longToRSString(), RSString.stringCombiner(new RSString[]{RSString.parse("<img=0>"), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString()}));
                } else {
                    MessageManager.addChat(quickChatId, 20, chat, Objects.requireNonNull(Unsorted.decodeBase37(clanName)).longToRSString(), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString());
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == JOIN_CLAN_CHAT)
        {
            Class167.anInt2087 = PacketParser.anInt3213;
            long ownerName37 = BufferedDataStream.incomingBuffer.readLong();
            int amtUsers;
            int n;
            int count;
            int var11;
            boolean var32;

            if (ownerName37 == 0) {
                Class161.chatOwnerName = null;
                Unsorted.packetOpcode = -1;
                RSInterface.chatroomName = null;
                PacketParser.clanChatMembers = null;
                Unsorted.clanMemberCount = 0;
                return true;
            } else {
                long chatroomName37 = BufferedDataStream.incomingBuffer.readLong();
                RSInterface.chatroomName = Unsorted.decodeBase37(chatroomName37);
                Class161.chatOwnerName = Unsorted.decodeBase37(ownerName37);
                Player.aByte3953 = BufferedDataStream.incomingBuffer.readSignedByte();
                amtUsers = BufferedDataStream.incomingBuffer.readUnsignedByte();
                if (255 != amtUsers) {
                    Unsorted.clanMemberCount = amtUsers;
                    Class3_Sub19[] members = new Class3_Sub19[100];

                    for (n = 0; n < Unsorted.clanMemberCount; ++n) {
                        members[n] = new Class3_Sub19();
                        members[n].linkableKey = BufferedDataStream.incomingBuffer.readLong();
                        members[n].username = Unsorted.decodeBase37(members[n].linkableKey);
                        members[n].worldId = BufferedDataStream.incomingBuffer.readUnsignedShort();
                        members[n].rights = BufferedDataStream.incomingBuffer.readSignedByte();
                        members[n].worldName = BufferedDataStream.incomingBuffer.readString();
                        if (members[n].linkableKey == PacketParser.localName37) {
                            Class91.userClanRights = members[n].rights;
                        }
                    }

                    count = Unsorted.clanMemberCount;

                    while (count > 0) {
                        var32 = true;
                        --count;

                        for (var11 = 0; var11 < count; ++var11) {
                            if (members[var11].username.method1559(members[var11 - -1].username) > 0) {
                                var32 = false;
                                Class3_Sub19 var9 = members[var11];
                                members[var11] = members[1 + var11];
                                members[var11 + 1] = var9;
                            }
                        }

                        if (var32) {
                            break;
                        }
                    }

                    PacketParser.clanChatMembers = members;
                }
                Unsorted.packetOpcode = -1;
                return true;
            }
        }

        if(opcode == LAST_LOGIN_INFO)
        {
            int ip32 = BufferedDataStream.incomingBuffer.readIntV1();
            Class136.lastlogAdress = Class38.gameSignlink.dnsResolve((byte) -83 ^ -82, ip32);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == PLAYER_INFO)
        {
            PlayerRendering.updatePlayers();
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETTEXT2)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            RSString text = BufferedDataStream.incomingBuffer.readString();
            int id = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();

            Class146.setPacketTracker(tracknum);
            InterfaceWidget.setWidgetText(text, id);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CHAT_FILTER_SETTINGS)
        {
            CS2Script.anInt3101 = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Class24.anInt467 = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Class45.anInt734 = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == SET_INTERACTION)
        {
            int cursorId = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            if (cursorId == 65535) {
                cursorId = -1;
            }

            int top = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int optId = BufferedDataStream.incomingBuffer.readUnsignedByte();
            RSString option = BufferedDataStream.incomingBuffer.readString();
            if (1 <= optId && optId <= 8) {
                if (option.equalsStringIgnoreCase(TextCore.HasNull)) {
                    option = null;
                }

                Class91.playerOptions[-1 + optId] = option;
                TextureOperation35.playerOptCursor[optId + -1] = cursorId;
                Class1.secondaryOption[optId + -1] = top == 0;
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == VARP_LARGE)
        {
            int value = BufferedDataStream.incomingBuffer.readInt();
            int id = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            VarpHelpers.setVarp(value, id);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETHIDE)
        {
            int value = BufferedDataStream.incomingBuffer.readUnsignedNegativeByte();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int id = BufferedDataStream.incomingBuffer.readIntLE();

            Class146.setPacketTracker(tracknum);
            TextureOperation4.updateVisibility(id, value);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_OPENSUB)
        {
            int parentId = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            int reset = BufferedDataStream.incomingBuffer.readUnsignedByte128();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();

            Class146.setPacketTracker(tracknum);
            if (reset == 2) {
                PacketParser.resetWorldMap();
            }
            ConfigInventoryDefinition.parentWidgetId = parentId;
            TextureOperation20.method232(parentId);
            Class124.method1746(false, (byte) -64);
            TextureOperation24.method226(ConfigInventoryDefinition.parentWidgetId);

            for (int ix = 0; ix < 100; ++ix) {
                Unsorted.aBooleanArray3674[ix] = true;
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CLIENT_SETVARC_LARGE)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            int value = BufferedDataStream.incomingBuffer.readInt();
            int id = BufferedDataStream.incomingBuffer.readUnsignedShort128();

            Class146.setPacketTracker(tracknum);
            TextureOperation19.updateVarC(id, value, 1);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == MESSAGE_QUICKCHAT_PRIVATE_ECHO)
        {
            long name = BufferedDataStream.incomingBuffer.readLong();
            int chatId = BufferedDataStream.incomingBuffer.readUnsignedShort();
            RSString message = QuickChat.load(chatId).toChat(BufferedDataStream.incomingBuffer);
            MessageManager.addChat(chatId, 19, message, null, Objects.requireNonNull(Unsorted.decodeBase37(name)).longToRSString());
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_UID192)
        {
            Class162.writeRandom(BufferedDataStream.incomingBuffer);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == RESET_CLIENT_VARCACHE)
        {
            TextureOperation6.resetTransientVarP(-117);
            BufferedDataStream.flagActiveInterfacesForUpdate();
            Class36.varpUpdateCount += 32;
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CAM_LOOKAT)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int tx = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int tz = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int cy = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int step = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int dur = BufferedDataStream.incomingBuffer.readUnsignedByte();

            Class146.setPacketTracker(tracknum);
            Class164_Sub1.method2238(cy, tz, step, tx, dur);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETANIM)
        {
            int id = BufferedDataStream.incomingBuffer.readIntV2();
            int value = BufferedDataStream.incomingBuffer.readSignedShortLE();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();

            Class146.setPacketTracker(tracknum);
            Class131.updateWidgetAnimation(id, value);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == WIDGETSTRUCT_SETTING)
        {
            int value = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            int parent = BufferedDataStream.incomingBuffer.readIntLE();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int end = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            Class3_Sub1 settings;
            if (end == 65535) {
                end = -1;
            }

            int start = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            if (start == 65535) {
                start = -1;
            }

            Class146.setPacketTracker(tracknum);
            for (int slot = start; end >= slot; ++slot) {
                long ptr = (long) slot + ((long) parent << 32);
                Class3_Sub1 prev = (Class3_Sub1) Class124.widgetSettings.get(ptr);
                if (null != prev) {
                    settings = new Class3_Sub1(prev.accessMask, value);
                    prev.unlink();
                } else if (slot == -1) {
                    settings = new Class3_Sub1(Objects.requireNonNull(Unsorted.getRSInterface(parent)).settings.accessMask, value);
                } else {
                    settings = new Class3_Sub1(0, value);
                }

                Class124.widgetSettings.put(ptr, settings);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == SPOTANIM_SPECIFIC)
        {
            int delay = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int height = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            int target = BufferedDataStream.incomingBuffer.readIntV1();
            int gfxId = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            if (target >> 30 == 0) {
                SequenceDefinition animDef;
                if (target >> 29 != 0) {
                    int npcId = 65535 & target;
                    NPC npc = NPC.npcs[npcId];
                    if (null != npc) {
                        if (gfxId == 65535) {
                            gfxId = -1;
                        }
                        boolean animated = gfxId == -1 || -1 == npc.graphicId || SequenceDefinition.getAnimationDefinition(GraphicDefinition.getGraphicDefinition(npc.graphicId).animationId).forcedPriority <= SequenceDefinition.getAnimationDefinition(GraphicDefinition.getGraphicDefinition(gfxId).animationId).forcedPriority;

                        if (animated) {
                            npc.anInt2761 = 0;
                            npc.graphicId = gfxId;
                            npc.gfxDelay = Class44.anInt719 + delay;
                            npc.anInt2805 = 0;
                            if (npc.gfxDelay > Class44.anInt719) {
                                npc.anInt2805 = -1;
                            }

                            npc.gfxPosY = height;
                            npc.anInt2826 = 1;
                            if (npc.graphicId != -1 && Class44.anInt719 == npc.gfxDelay) {
                                int anim = GraphicDefinition.getGraphicDefinition(npc.graphicId).animationId;
                                if (anim != -1) {
                                    animDef = SequenceDefinition.getAnimationDefinition(anim);
                                    if (null != animDef.frames) {
                                        Unsorted.method1470(npc.yAxis, animDef, npc.xAxis, false, 0);
                                    }
                                }
                            }
                        }
                    }
                } else if (target >> 28 != 0) {
                    int playerId = target & 65535;
                    Player player;
                    if (playerId == Class3_Sub1.localIndex) {
                        player = Class102.player;
                    } else {
                        player = Unsorted.players[playerId];
                    }

                    if (null != player) {
                        if (gfxId == 65535) {
                            gfxId = -1;
                        }
                        boolean animated = gfxId == -1 || player.graphicId == -1 || SequenceDefinition.getAnimationDefinition(GraphicDefinition.getGraphicDefinition(player.graphicId).animationId).forcedPriority <= SequenceDefinition.getAnimationDefinition(GraphicDefinition.getGraphicDefinition(gfxId).animationId).forcedPriority;

                        if (animated) {
                            player.gfxDelay = delay + Class44.anInt719;
                            player.gfxPosY = height;
                            player.graphicId = gfxId;

                            player.anInt2826 = 1;
                            player.anInt2761 = 0;
                            player.anInt2805 = 0;
                            if (player.gfxDelay > Class44.anInt719) {
                                player.anInt2805 = -1;
                            }

                            if (player.graphicId != -1 && Class44.anInt719 == player.gfxDelay) {
                                int anim = GraphicDefinition.getGraphicDefinition(player.graphicId).animationId;
                                if (anim != -1) {
                                    animDef = SequenceDefinition.getAnimationDefinition(anim);
                                    if (null != animDef.frames) {
                                        Unsorted.method1470(player.yAxis, animDef, player.xAxis, player == Class102.player, 0);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                int plane = 3 & target >> 28;
                int posX = ((target & 0xffffb65) >> 14) - Class131.lowestVisibleTileX;
                int posZ = (target & 16383) - Texture.lowestVisibleTileZ;
                if (posX >= 0 && posZ >= 0 && 104 > posX && posZ < 104) {
                    posZ = posZ * 128 - -64;
                    posX = 128 * posX + 64;
                    PositionedGraphicObject var50 = new PositionedGraphicObject(gfxId, plane, posX, posZ, -height + Scenery.sceneryPositionHash(plane, 1, posX, posZ), delay, Class44.anInt719);
                    TextureOperation17.aLinkedList_3177.pushBack(new Class3_Sub28_Sub2(var50));
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == INTERFACE_ANIMATE_ROTATE)
        {
            int ptr = BufferedDataStream.incomingBuffer.readIntV2();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int pitchStep = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int yawStep = BufferedDataStream.incomingBuffer.readUnsignedShort128();

            Class146.setPacketTracker(tracknum);
            Class114.showcaseRoll(yawStep + (pitchStep << 16), ptr);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_STAT)
        {
            BufferedDataStream.flagActiveInterfacesForUpdate();
            int level = BufferedDataStream.incomingBuffer.readUnsignedByte128();//Level
            int experience = BufferedDataStream.incomingBuffer.readIntV1();//Skillxp
            int skillId = BufferedDataStream.incomingBuffer.readUnsignedByte();//Skill ID

            int gain = experience - XPGainDraw.getLastXp()[skillId];
            XPGainDraw.addGain(gain, skillId);
            XPGainDraw.getLastXp()[skillId] = experience;

            Class133.experience[skillId] = experience;//XP for Skill ID
            TextureOperation17.level[skillId] = level;//Level for Skill ID
            Class3_Sub20.maxLevel[skillId] = 1;

            for (int counter = 0; 98 > counter; ++counter) {
                if (ItemDefinition.levelxpLookup[counter] <= experience) { //Checks xp less than or equal to level 98 or 11805606 xp
                    Class3_Sub20.maxLevel[skillId] = counter + 2;
                }
            }
            //Calculate xp till next level
            //System.out.println("xp Gained: " + (ItemDefinition.anIntArray781[level - 1] - experience));

            Client.anIntArray3780[Unsorted.bitwiseAnd(31, Class49.anInt815++)] = skillId;
            Unsorted.packetOpcode = -1;
            return true;
        }

        if (opcode == LOCATION_PACKET_104
            || opcode == LOCATION_PACKET_121
            || opcode == LOCATION_PACKET_97
            || opcode == LOCATION_PACKET_14
            || opcode == LOCATION_PACKET_202
            || opcode == LOCATION_PACKET_135
            || opcode == LOCATION_PACKET_17
            || opcode == LOCATION_PACKET_16
            || opcode == LOCATION_PACKET_240
            || opcode == LOCATION_PACKET_33
            || opcode == LOCATION_PACKET_20
            || opcode == LOCATION_PACKET_195
            || opcode == LOCATION_PACKET_179)
        {
            Class39.parseLocationPacket((byte) -99);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_CLOSESUB)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int id = BufferedDataStream.incomingBuffer.readInt();

            Class146.setPacketTracker(tracknum);
            Class3_Sub31 pointer = TextureOperation23.openInterfaces.get(id);
            if (null != pointer) {
                TextureOperation19.closeWidget(true, pointer);
            }

            if (null != TextureOperation27.aClass11_3087) {
                Class20.flagUpdate(TextureOperation27.aClass11_3087);
                TextureOperation27.aClass11_3087 = null;
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if (opcode == CAM_FORCEANGLE)
        {
            int yaw = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int pitch = BufferedDataStream.incomingBuffer.readUnsignedShort();

            Class146.setPacketTracker(tracknum);
            GraphicDefinition.yawTarget = yaw;
            Unsorted.pitchTarget = pitch;
            if (Class133.cameraMode == 2) {
                Class139.cameraPitch = Unsorted.pitchTarget;
                TextureOperation28.cameraYaw = GraphicDefinition.yawTarget;
            }

            Unsorted.clampCameraAngle();
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETANGLE)
        {
            int pitch = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int scale = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            int yaw = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            int ptr = BufferedDataStream.incomingBuffer.readInt();

            Class146.setPacketTracker(tracknum);
            Unsorted.updateView((byte) -124, scale, ptr, yaw, pitch);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CLEAR_GROUND_ITEMS)
        {
            Class39.currentChunkX = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Class39.currentChunkY = BufferedDataStream.incomingBuffer.readUnsignedNegativeByte();

            for (int x = Class39.currentChunkX; x < 8 + Class39.currentChunkX; ++x) {
                for (int z = Class39.currentChunkY; 8 + Class39.currentChunkY > z; ++z) {
                    if (null != Class39.groundItems[WorldListCountry.localPlane][x][z]) {
                        Class39.groundItems[WorldListCountry.localPlane][x][z] = null;
                        Class128.updateGroundItems(z, x);
                    }
                }
            }

            for (Scenery var68 = (Scenery) Scenery.sceneryList.startIteration(); null != var68; var68 = (Scenery) Scenery.sceneryList.nextIteration()) {
                if (Class39.currentChunkX <= var68.x && 8 + Class39.currentChunkX > var68.x && var68.y >= Class39.currentChunkY && 8 + Class39.currentChunkY > var68.y && var68.plane == WorldListCountry.localPlane) {
                    var68.anInt2259 = 0;
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == INTERFACE_ITEMS_CLEAR)
        {
            int id = BufferedDataStream.incomingBuffer.readIntV2();
            RSInterface inter = Unsorted.getRSInterface(id);

            for (int n = 0; Objects.requireNonNull(inter).itemIds.length > n; ++n) {
                inter.itemIds[n] = -1;
                inter.itemIds[n] = 0;
            }

            Class20.flagUpdate(inter);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETMODEL)
        {
            int id = BufferedDataStream.incomingBuffer.readIntLE();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            int modelId = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            if (modelId == 65535) {
                modelId = -1;
            }

            Class146.setPacketTracker(tracknum);
            PacketParser.updateWidgetMedia(-1, 1, id, modelId);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == SET_MINIMAP_STATE)
        {
            Class161.minimapState = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == TELEPORT_LOCAL_PLAYER)
        {
            int pos1 = BufferedDataStream.incomingBuffer.readUnsigned128Byte();
            int flags = BufferedDataStream.incomingBuffer.readUnsignedByte128();
            int pos2 = BufferedDataStream.incomingBuffer.readUnsignedByte();
            WorldListCountry.localPlane = flags >> 1;
            Class102.player.updatePlayerPosition(pos1, (flags & 1) == 1, pos2);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_FRIENDLIST)
        {
            long name = BufferedDataStream.incomingBuffer.readLong();
            int worldId = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int x = BufferedDataStream.incomingBuffer.readUnsignedByte();
            boolean isIgnored = true;
            if (name < 0L) {
                name &= Long.MAX_VALUE;
                isIgnored = false;
            }

            RSString worldName = RSString.parse("");
            if (worldId > 0) {
                worldName = BufferedDataStream.incomingBuffer.readString();
            }

            RSString username = Objects.requireNonNull(Unsorted.decodeBase37(name)).longToRSString();

            for (int n = 0; n < Class8.totalFriendCount; ++n) {
                if (name == Class50.friendNameLong[n]) {
                    if (Unsorted.friendWorldId[n] != worldId) {
                        Unsorted.friendWorldId[n] = worldId;
                        if (0 < worldId) {
                            BufferedDataStream.addChat(RSString.parse(""), 5, RSString.stringCombiner(new RSString[]{username, TextCore.HasLoggedIn}), -1);
                        }

                        if (worldId == 0) {
                            BufferedDataStream.addChat(RSString.parse(""), 5, RSString.stringCombiner(new RSString[]{username, TextCore.HasLoggedOut}), -1);
                        }
                    }

                    Unsorted.friendWorldName[n] = worldName;
                    Class57.anIntArray904[n] = x;
                    username = null;
                    Unsorted.aBooleanArray73[n] = isIgnored;
                    break;
                }
            }

            boolean sorting;
            if (null != username && 200 > Class8.totalFriendCount) {
                Class50.friendNameLong[Class8.totalFriendCount] = name;
                Class70.friendNameString[Class8.totalFriendCount] = username;
                Unsorted.friendWorldId[Class8.totalFriendCount] = worldId;
                Unsorted.friendWorldName[Class8.totalFriendCount] = worldName;
                Class57.anIntArray904[Class8.totalFriendCount] = x;
                Unsorted.aBooleanArray73[Class8.totalFriendCount] = isIgnored;
                ++Class8.totalFriendCount;
            }

            Class110.socialUpdateTick = PacketParser.anInt3213;
            int friendCount = Class8.totalFriendCount;

            while (friendCount > 0) {
                --friendCount;
                sorting = true;

                for (int n = 0; n < friendCount; ++n) {
                    if (CS2Script.userCurrentWorldID != Unsorted.friendWorldId[n] && Unsorted.friendWorldId[n - -1] == CS2Script.userCurrentWorldID || Unsorted.friendWorldId[n] == 0 && Unsorted.friendWorldId[n - -1] != 0) {
                        sorting = false;
                        int var12 = Unsorted.friendWorldId[n];
                        Unsorted.friendWorldId[n] = Unsorted.friendWorldId[n - -1];
                        Unsorted.friendWorldId[1 + n] = var12;
                        RSString var64 = Unsorted.friendWorldName[n];
                        Unsorted.friendWorldName[n] = Unsorted.friendWorldName[n + 1];
                        Unsorted.friendWorldName[n - -1] = var64;
                        RSString var57 = Class70.friendNameString[n];
                        Class70.friendNameString[n] = Class70.friendNameString[n + 1];
                        Class70.friendNameString[n - -1] = var57;
                        long var15 = Class50.friendNameLong[n];
                        Class50.friendNameLong[n] = Class50.friendNameLong[n - -1];
                        Class50.friendNameLong[n - -1] = var15;
                        int var17 = Class57.anIntArray904[n];
                        Class57.anIntArray904[n] = Class57.anIntArray904[n - -1];
                        Class57.anIntArray904[1 + n] = var17;
                        boolean var18 = Unsorted.aBooleanArray73[n];
                        Unsorted.aBooleanArray73[n] = Unsorted.aBooleanArray73[n - -1];
                        Unsorted.aBooleanArray73[n - -1] = var18;
                    }
                }

                if (sorting) {
                    break;
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == SET_WALK_TEXT)
        {
            if (0 == Unsorted.packetSize) {
                TextureOperation32.walkText = TextCore.HasWalkHere;
            } else {
                TextureOperation32.walkText = BufferedDataStream.incomingBuffer.readString();
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == FORCE_VARP_REFRESH)
        {
            for (int var = 0; var < ItemDefinition.varp.length; ++var) {
                if (ItemDefinition.varp[var] != Class57.varp[var]) {
                    ItemDefinition.varp[var] = Class57.varp[var];
                    Class46.refreshMagicVarp(98, var);
                    Class44.cs2_updatedVarP[Unsorted.bitwiseAnd(Class36.varpUpdateCount++, 31)] = var;
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CAMERA_DETACH)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int var19 = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int modelId = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int counter = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int var6 = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int var30 = BufferedDataStream.incomingBuffer.readUnsignedByte();

            Class146.setPacketTracker(tracknum);
            Class3_Sub20.method390(true, var6, counter, var30, (byte) -124, modelId, var19);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == MESSAGE_QUICKCHAT_PRIVATE)
        {
            long name = BufferedDataStream.incomingBuffer.readLong();
            int top = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int bot = BufferedDataStream.incomingBuffer.readMedium();
            int rights = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int chatId = BufferedDataStream.incomingBuffer.readUnsignedShort();
            boolean ignore = false;
            long messageId = ((long)top << 32) + bot;
            int ix = 0;

            check:
            while (true) {
                if (100 > ix) {
                    if (Class163_Sub2_Sub1.recentMessages[ix] != messageId) {
                        ++ix;
                        continue;
                    }

                    ignore = true;
                    break;
                }

                if (rights <= 1) {
                    for (ix = 0; Class3_Sub28_Sub5.ignoreCount > ix; ++ix) {
                        if (name == Class114.ignoreList[ix]) {
                            ignore = true;
                            break check;
                        }
                    }
                }
                break;
            }

            if (!ignore && inTutorialIsland == 0) {
                Class163_Sub2_Sub1.recentMessages[MouseListeningClass.messageCounter] = messageId;
                MouseListeningClass.messageCounter = (1 + MouseListeningClass.messageCounter) % 100;
                RSString chat = QuickChat.load(chatId).toChat(BufferedDataStream.incomingBuffer);
                if (rights == 2) {
                    MessageManager.addChat(chatId, 18, chat, null, RSString.stringCombiner(new RSString[]{RSString.parse("<img=1>"), Objects.requireNonNull(Unsorted.decodeBase37(name)).longToRSString()}));
                } else if (1 == rights) {
                    MessageManager.addChat(chatId, 18, chat, null, RSString.stringCombiner(new RSString[]{RSString.parse("<img=0>"), Objects.requireNonNull(Unsorted.decodeBase37(name)).longToRSString()}));
                } else {
                    MessageManager.addChat(chatId, 18, chat, null, Objects.requireNonNull(Unsorted.decodeBase37(name)).longToRSString());
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == SWITCH_WIDGET)
        {
            int source = BufferedDataStream.incomingBuffer.readIntV1();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int target = BufferedDataStream.incomingBuffer.readIntV1();
            Class146.setPacketTracker(tracknum);
            Class3_Sub31 srcWidget = TextureOperation23.openInterfaces.get(source);
            Class3_Sub31 tgtWidget = TextureOperation23.openInterfaces.get(target);
            if (null != tgtWidget) {
                TextureOperation19.closeWidget(null == srcWidget || tgtWidget.widgetId != srcWidget.widgetId, tgtWidget);
            }

            if (null != srcWidget) {
                srcWidget.unlink();
                TextureOperation23.openInterfaces.put(target, srcWidget);
            }

            RSInterface widget = Unsorted.getRSInterface(source);
            if (widget != null) {
                Class20.flagUpdate(widget);
            }

            widget = Unsorted.getRSInterface(target);
            if (null != widget) {
                Class20.flagUpdate(widget);
                Unsorted.method2104(widget, true, 48);
            }

            if (ConfigInventoryDefinition.parentWidgetId != -1) {
                Class3_Sub8.runScripts(28, 1, ConfigInventoryDefinition.parentWidgetId);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CAM_SHAKE)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int cameraId = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int jitter = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int amplitude = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int frequency = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int shake4 = BufferedDataStream.incomingBuffer.readUnsignedShort();

            Class146.setPacketTracker(tracknum);
            WaterfallShader.customCameraActive[cameraId] = true;
            TextureOperation14.cameraJitter[cameraId] = jitter;
            Class166.cameraAmplitude[cameraId] = amplitude;
            TextureOperation3.cameraFrequency[cameraId] = frequency;
            Class163_Sub1_Sub1.anIntArray4009[cameraId] = shake4;

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETCOLOUR)
        {
            int id = BufferedDataStream.incomingBuffer.readIntV1();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int color = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();

            Class146.setPacketTracker(tracknum);
            CSConfigCachefile.setColor(color, id);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_REBOOT_TIMER)
        {
            Class38_Sub1.systemUpdateTime = BufferedDataStream.incomingBuffer.readUnsignedShort() * 30;
            Unsorted.packetOpcode = -1;
            Class140_Sub6.genericCs2UpdateTime = PacketParser.anInt3213;
            return true;
        }

        if(opcode == REFLECTION_CHEAT_CHECK)
        {
            TextureOperation3.method305(Class38.gameSignlink, BufferedDataStream.incomingBuffer, Unsorted.packetSize);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == CLIENT_SETVARC_SMALL)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            int value = BufferedDataStream.incomingBuffer.readUnsignedNegativeByte();
            int id = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();

            Class146.setPacketTracker(tracknum);
            TextureOperation19.updateVarC(id, value, (byte) -83 ^ -84);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_RUNENERGY)
        {
            BufferedDataStream.flagActiveInterfacesForUpdate();
            Unsorted.runEnergy = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Class140_Sub6.genericCs2UpdateTime = PacketParser.anInt3213;
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == GAME_FRAME_UNK)
        {
            if (-1 != ConfigInventoryDefinition.parentWidgetId) {
                Class3_Sub8.runScripts(48, 0, ConfigInventoryDefinition.parentWidgetId);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == DELETE_INVENTORY)
        {
            int invId = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            CS2Methods.deleteInventory(invId);
            QuickChatDefinition.cs2_updatedInv[Unsorted.bitwiseAnd(31, Unsorted.invUpdateCount++)] = Unsorted.bitwiseAnd(invId, 32767);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == NPC_ANIM_SPECIFIC)
        {
            int npcId = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            int value = BufferedDataStream.incomingBuffer.readUnsigned128Byte();
            int animationId = BufferedDataStream.incomingBuffer.readUnsignedShort();

            NPC npc = NPC.npcs[npcId];
            if (null != npc) {
                Unsorted.animateNpc(value, animationId, 39, npc);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_RUNWEIGHT)
        {
            BufferedDataStream.flagActiveInterfacesForUpdate();
            MouseListeningClass.runWeight = BufferedDataStream.incomingBuffer.readSignedShort();
            Class140_Sub6.genericCs2UpdateTime = PacketParser.anInt3213;
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == MESSAGE_PRIVATE_ECHO)
        {
            long name = BufferedDataStream.incomingBuffer.readLong();
            RSString message = Font.load(Objects.requireNonNull(Class32.toChat(BufferedDataStream.incomingBuffer).properlyCapitalize()));
            BufferedDataStream.addChat(Objects.requireNonNull(Unsorted.decodeBase37(name)).longToRSString(), 6, message, (byte) -83 ^ 82);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == URL_OPEN)
        {
            if (TextureOperation30.fullScreenFrame != null) {
                GameObject.graphicsSettings(false, Unsorted.anInt2577, -1, -1);
            }

            byte[] var22 = new byte[Unsorted.packetSize];
            BufferedDataStream.incomingBuffer.readBytesIsaac((byte) 30, 0, var22, Unsorted.packetSize);
            RSString url = TextureOperation33.bufferToString(var22, Unsorted.packetSize, 0);
            if (null == GameShell.frame && (3 == Signlink.anInt1214 || !Signlink.osName.startsWith("win") || Class106.paramUserUsingInternetExplorer)) {
                Class99.openUrl(url, (byte) 127, true);
            } else {
                TextureOperation5.aString_3295 = url;
                Unsorted.aBoolean2154 = true;
                AudioThread.aClass64_351 = Class38.gameSignlink.method1452(new String(url.method1568(), StandardCharsets.ISO_8859_1), true);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == GENERATE_CHAT_HEAD_FROM_BODY)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int id = BufferedDataStream.incomingBuffer.readIntV2();
            int value1 = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            int value2 = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            int value3 = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();

            Class146.setPacketTracker(tracknum);
            PacketParser.updateWidgetMedia(value1, 7, id, value2 << 16 | value3);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == VARBIT_SMALL)
        {
            int value = BufferedDataStream.incomingBuffer.readUnsignedByte128();
            int id = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            VarpHelpers.setVarbit((byte) -122, value, id);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_OPENTOP)
        {
            int type = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int pointer = BufferedDataStream.incomingBuffer.readIntV2();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int widgetId = BufferedDataStream.incomingBuffer.readUnsignedShort();

            Class146.setPacketTracker(tracknum);
            Class3_Sub31 ptr = TextureOperation23.openInterfaces.get(pointer);
            if (null != ptr) {
                TextureOperation19.closeWidget(ptr.widgetId != widgetId, ptr);
            }

            Class21.method914(widgetId, pointer, type);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == RESET_ANIMS)
        {
            for (int n = 0; n < Unsorted.players.length; ++n) {
                if (Unsorted.players[n] != null) {
                    Unsorted.players[n].animationId = -1;
                }
            }

            for (int n = 0; n < NPC.npcs.length; ++n) {
                if (null != NPC.npcs[n]) {
                    NPC.npcs[n].animationId = -1;
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == HINT_ARROW)
        {
            int flags = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Class96 mapMarker = new Class96();
            int slot = flags >> 6;
            mapMarker.type = flags & 63;
            mapMarker.anInt1351 = BufferedDataStream.incomingBuffer.readUnsignedByte();
            if (mapMarker.anInt1351 >= 0 && Class166.hint_headicons.length > mapMarker.anInt1351) {
                if (mapMarker.type == 1 || 10 == mapMarker.type) {
                    mapMarker.actorTargetId = BufferedDataStream.incomingBuffer.readUnsignedShort();
                    BufferedDataStream.incomingBuffer.index += 3;
                } else if (mapMarker.type >= 2 && 6 >= mapMarker.type) {
                    if (mapMarker.type == 2) {
                        mapMarker.anInt1346 = 64;
                        mapMarker.anInt1350 = 64;
                    }

                    if (mapMarker.type == 3) {
                        mapMarker.anInt1346 = 0;
                        mapMarker.anInt1350 = 64;
                    }

                    if (4 == mapMarker.type) {
                        mapMarker.anInt1346 = 128;
                        mapMarker.anInt1350 = 64;
                    }

                    if (5 == mapMarker.type) {
                        mapMarker.anInt1346 = 64;
                        mapMarker.anInt1350 = 0;
                    }

                    if (mapMarker.type == 6) {
                        mapMarker.anInt1346 = 64;
                        mapMarker.anInt1350 = 128;
                    }

                    mapMarker.type = 2;
                    mapMarker.targetX = BufferedDataStream.incomingBuffer.readUnsignedShort();
                    mapMarker.anInt1347 = BufferedDataStream.incomingBuffer.readUnsignedShort();
                    mapMarker.anInt1353 = BufferedDataStream.incomingBuffer.readUnsignedByte();
                }

                mapMarker.playerModelId = BufferedDataStream.incomingBuffer.readUnsignedShort();
                if (mapMarker.playerModelId == 65535) {
                    mapMarker.playerModelId = -1;
                }

                ClientErrorException.hintMapMarkers[slot] = mapMarker;
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_IGNORELIST)
        {
            Class3_Sub28_Sub5.ignoreCount = Unsorted.packetSize / 8;

            for (int n = 0; Class3_Sub28_Sub5.ignoreCount > n; ++n) {
                Class114.ignoreList[n] = BufferedDataStream.incomingBuffer.readLong();
                TextureOperation7.aStringArray3341[n] = Unsorted.decodeBase37(Class114.ignoreList[n]);
            }

            Class110.socialUpdateTick = PacketParser.anInt3213;
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == NPC_INFO)
        {
            NPCRendering.updateNpcs(8169);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETPOSITION)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int ptr = BufferedDataStream.incomingBuffer.readIntLE();
            int x = BufferedDataStream.incomingBuffer.readSignedShort();
            int y = BufferedDataStream.incomingBuffer.readSignedShort128();

            Class146.setPacketTracker(tracknum);
            RSInterface.method2271(x, ptr, y);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == LOC_ANIM_SPECIFIC)
        {
            int slot = BufferedDataStream.incomingBuffer.readUnsigned128Byte();
            int type = slot >> 2;
            int rotation = 3 & slot;
            int type2 = Class75.anIntArray1107[type];
            int animationID = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int position = BufferedDataStream.incomingBuffer.readInt();
            if (65535 == animationID) {
                animationID = -1;
            }

            int z = 16383 & position;
            int x = 16383 & position >> 14;
            x -= Class131.lowestVisibleTileX;
            z -= Texture.lowestVisibleTileZ;
            int plane = 3 & position >> 28;
            Class50.method1131(plane, 110, rotation, type, z, type2, x, animationID);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == MESSAGE_PRIVATE)
        {
            long username = BufferedDataStream.incomingBuffer.readLong();
            int top = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int bot = BufferedDataStream.incomingBuffer.readMedium();
            int rights = BufferedDataStream.incomingBuffer.readUnsignedByte();
            boolean ignore = false;
            long messageId = bot + ((long)top << 32);

            int ix = 0;
            check:
            while (true) {
                if (ix >= 100) {
                    if (rights <= 1) {
                        if ((!Class3_Sub15.aBoolean2433 || Class121.aBoolean1641) && !TextureOperation31.aBoolean3166) {
                            for (ix = 0; ix < Class3_Sub28_Sub5.ignoreCount; ++ix) {
                                if (Class114.ignoreList[ix] == username) {
                                    ignore = true;
                                    break check;
                                }
                            }
                        } else {
                            ignore = true;
                        }
                    }
                    break;
                }

                if (Class163_Sub2_Sub1.recentMessages[ix] == messageId) {
                    ignore = true;
                    break;
                }

                ++ix;
            }

            if (!ignore && inTutorialIsland == 0) {
                Class163_Sub2_Sub1.recentMessages[MouseListeningClass.messageCounter] = messageId;
                MouseListeningClass.messageCounter = (MouseListeningClass.messageCounter - -1) % 100;
                RSString chat = Font.load(Objects.requireNonNull(Class32.toChat(BufferedDataStream.incomingBuffer).properlyCapitalize()));
                if (rights == 2 || rights == 3) {
                    BufferedDataStream.addChat(RSString.stringCombiner(new RSString[]{RSString.parse("<img=1>"), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString()}), 7, chat, -1);
                } else if (rights == 1) {
                    BufferedDataStream.addChat(RSString.stringCombiner(new RSString[]{RSString.parse("<img=" + (rights - 1) + ">"), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString()}), 7, chat, -1);
                } else {
                    BufferedDataStream.addChat(RSString.stringCombiner(new RSString[]{RSString.parse("<img=" + (rights - 1) + ">"), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString()}), 7, chat, -1);
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == MESSAGE_CLANCHANNEL)
        {
            long username = BufferedDataStream.incomingBuffer.readLong();
            BufferedDataStream.incomingBuffer.readSignedByte();
            long chatname = BufferedDataStream.incomingBuffer.readLong();
            int top = BufferedDataStream.incomingBuffer.readUnsignedShort();
            int bot = BufferedDataStream.incomingBuffer.readMedium();
            long messageId = (top << 32) + bot;
            int rights = BufferedDataStream.incomingBuffer.readUnsignedByte();
            boolean ignored = false;

            int ix = 0;
            check:
            while (true) {
                if (ix >= 100) {
                    if (1 >= rights) {
                        if ((!Class3_Sub15.aBoolean2433 || Class121.aBoolean1641) && !TextureOperation31.aBoolean3166) {
                            for (ix = 0; Class3_Sub28_Sub5.ignoreCount > ix; ++ix) {
                                if (Class114.ignoreList[ix] == username) {
                                    ignored = true;
                                    break check;
                                }
                            }
                        } else {
                            ignored = true;
                        }
                    }
                    break;
                }

                if (Class163_Sub2_Sub1.recentMessages[ix] == messageId) {
                    ignored = true;
                    break;
                }

                ++ix;
            }

            if (!ignored && 0 == inTutorialIsland) {
                Class163_Sub2_Sub1.recentMessages[MouseListeningClass.messageCounter] = messageId;
                MouseListeningClass.messageCounter = (MouseListeningClass.messageCounter + 1) % 100;
                RSString chat = Font.load(Objects.requireNonNull(Class32.toChat(BufferedDataStream.incomingBuffer).properlyCapitalize()));
                if (rights == 2 || rights == 3) {
                    TextureOperation1.method221(-1, chat, RSString.stringCombiner(new RSString[]{RSString.parse("<img=1>"), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString()}), Objects.requireNonNull(Unsorted.decodeBase37(chatname)).longToRSString(), 9);
                } else if (rights == 1) {
                    TextureOperation1.method221(-1, chat, RSString.stringCombiner(new RSString[]{RSString.parse("<img=0>"), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString()}), Objects.requireNonNull(Unsorted.decodeBase37(chatname)).longToRSString(), 9);
                } else {
                    if (rights == 0)
                        TextureOperation1.method221(-1, chat, Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString(), Objects.requireNonNull(Unsorted.decodeBase37(chatname)).longToRSString(), 9);
                    else
                        TextureOperation1.method221(-1, chat, RSString.stringCombiner(new RSString[]{RSString.parse("<img=" + (rights - 1) + ">"), Objects.requireNonNull(Unsorted.decodeBase37(username)).longToRSString()}), Objects.requireNonNull(Unsorted.decodeBase37(chatname)).longToRSString(), 9);
                }
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == DYNAMIC_SCENE_GRAPH)
        {
            Class39.parseMapRegion(true);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == SYNTH_SOUND)
        {
            int trackId = BufferedDataStream.incomingBuffer.readUnsignedShort();
            if (trackId == 65535) {
                trackId = -1;
            }
            int volume = BufferedDataStream.incomingBuffer.readUnsignedByte();
            int delay = BufferedDataStream.incomingBuffer.readUnsignedShort();

            AudioHandler.playSfx(volume, trackId, delay);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETPLAYERHEAD)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            int id = BufferedDataStream.incomingBuffer.readIntV1();

            Class146.setPacketTracker(tracknum);
            int set = 0;
            if (Class102.player.identityKit != null) {
                set = Class102.player.identityKit.method1163();
            }

            PacketParser.updateWidgetMedia(-1, 3, id, set);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == IF_SETTEXT1)
        {
            int id = BufferedDataStream.incomingBuffer.readIntV2();
            RSString text = BufferedDataStream.incomingBuffer.readString();
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort128();

            Class146.setPacketTracker(tracknum);
            Unsorted.setWidgetText(text, id);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == VARBIT_LARGE)
        {
            int value = BufferedDataStream.incomingBuffer.readIntLE();
            int id = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            VarpHelpers.setVarbit((byte) -106, value, id);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_INV_PARTIAL)
        {
            int componentHash = BufferedDataStream.incomingBuffer.readInt();
            int containerIdd = BufferedDataStream.incomingBuffer.readUnsignedShort();
            RSInterface iface;
            if (componentHash < -70000) {
                containerIdd += 32768;
            }

            if (componentHash < 0) {
                iface = null;
            } else {
                iface = Unsorted.getRSInterface(componentHash);
            }

            int slot;
            int itemAmount;
            int itemId;
            for (; Unsorted.packetSize > BufferedDataStream.incomingBuffer.index; PacketParser.addItemToContainer(itemAmount - 1, slot, itemId, containerIdd)) {
                slot = BufferedDataStream.incomingBuffer.getSmart();
                itemAmount = BufferedDataStream.incomingBuffer.readUnsignedShort();
                itemId = 0;
                if (itemAmount != 0) {
                    itemId = BufferedDataStream.incomingBuffer.readUnsignedByte();
                    if (itemId == 255) {
                        itemId = BufferedDataStream.incomingBuffer.readInt();
                    }
                }

                if (iface != null && slot >= 0 && slot < iface.itemIds.length) {
                    iface.itemIds[slot] = itemAmount;
                    iface.amounts[slot] = itemId;
                }
            }

            if (iface != null) {
                Class20.flagUpdate(iface);
            }

            BufferedDataStream.flagActiveInterfacesForUpdate();
            QuickChatDefinition.cs2_updatedInv[Unsorted.bitwiseAnd(Unsorted.invUpdateCount++, 31)] = Unsorted.bitwiseAnd(32767, containerIdd);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if (opcode == CAM_RESET)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShort();
            Class146.setPacketTracker(tracknum);
            Class3_Sub28_Sub5.resetCameraEffects();

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == LOGOUT)
        {
            XPGainDraw.reset();
            SlayerTracker.reset();
            Discord.updatePresence("At the login screen","","");

            Class167.logoutReset((byte) 46);
            Unsorted.packetOpcode = -1;
            return false;
        }

        if(opcode == GRAND_EXCHANGE_OFFERS)
        {
            int nodeModelId = BufferedDataStream.incomingBuffer.readUnsignedByte();
            if (BufferedDataStream.incomingBuffer.readUnsignedByte() == 0) {
                TextureOperation29.aClass133Array3393[nodeModelId] = new Class133();
            } else {
                --BufferedDataStream.incomingBuffer.index;
                TextureOperation29.aClass133Array3393[nodeModelId] = new Class133(BufferedDataStream.incomingBuffer);
            }

            Unsorted.packetOpcode = -1;
            Class121.anInt1642 = PacketParser.anInt3213;
            return true;
        }

        if(opcode == IF_SETNPCHEAD)
        {
            int npcId = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int id = BufferedDataStream.incomingBuffer.readIntLE();
            if (npcId == 65535) {
                npcId = -1;
            }

            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            Class146.setPacketTracker(tracknum);
            PacketParser.updateWidgetMedia(-1, 2, id, npcId);

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_SCENE_GRAPH)
        {
            Class39.parseMapRegion(false);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == SET_INTERFACE_SETTINGS)
        {
            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            int end = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            if (end == 65535) {
                end = -1;
            }

            int pointer = BufferedDataStream.incomingBuffer.readInt();
            int start = BufferedDataStream.incomingBuffer.readUnsignedShort128();
            int accessMask = BufferedDataStream.incomingBuffer.readIntV1();
            if (start == 65535) {
                start = -1;
            }

            Class146.setPacketTracker(tracknum);
            Class3_Sub1 settings;
            for (int slot = start; slot <= end; ++slot) {
                long ptr = ((long) pointer << 32) - -((long) slot);
                Class3_Sub1 prev = (Class3_Sub1) Class124.widgetSettings.get(ptr);
                if (prev == null) {
                    if (-1 == slot) {
                        settings = new Class3_Sub1(accessMask, Objects.requireNonNull(Unsorted.getRSInterface(pointer)).settings.anInt2202);
                    } else {
                        settings = new Class3_Sub1(accessMask, -1);
                    }
                } else {
                    settings = new Class3_Sub1(accessMask, prev.anInt2202);
                    prev.unlink();
                }

                Class124.widgetSettings.put(ptr, settings);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == FRIENDLIST_LOADED)
        {
            CS2Script.contactServerState = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Class110.socialUpdateTick = PacketParser.anInt3213;
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_CLAN)
        {
            long username = BufferedDataStream.incomingBuffer.readLong();
            int worldId = BufferedDataStream.incomingBuffer.readUnsignedShort();
            byte rights = BufferedDataStream.incomingBuffer.readSignedByte();
            boolean isIgnored = (Long.MIN_VALUE & username) != 0;

            if (isIgnored) {
                if (Unsorted.clanMemberCount == 0) {
                    Unsorted.packetOpcode = -1;
                    return true;
                }

                username &= Long.MAX_VALUE;

                int n;
                for (n = 0; n < Unsorted.clanMemberCount && (username != PacketParser.clanChatMembers[n].linkableKey || PacketParser.clanChatMembers[n].worldId != worldId); ++n) {
                }

                if (n < Unsorted.clanMemberCount) {
                    while (n < -1 + Unsorted.clanMemberCount) {
                        PacketParser.clanChatMembers[n] = PacketParser.clanChatMembers[1 + n];
                        ++n;
                    }

                    --Unsorted.clanMemberCount;
                    PacketParser.clanChatMembers[Unsorted.clanMemberCount] = null;
                }
            } else {
                RSString worldName = BufferedDataStream.incomingBuffer.readString();
                Class3_Sub19 member = new Class3_Sub19();
                member.linkableKey = username;
                member.username = Unsorted.decodeBase37(member.linkableKey);
                member.rights = rights;
                member.worldName = worldName;
                member.worldId = worldId;

                // sort members
                int m;
                int n;
                for (n = -1 + Unsorted.clanMemberCount; n >= 0; --n) {
                    m = PacketParser.clanChatMembers[n].username.method1559(member.username);
                    if (m == 0) {
                        PacketParser.clanChatMembers[n].worldId = worldId;
                        PacketParser.clanChatMembers[n].rights = rights;
                        PacketParser.clanChatMembers[n].worldName = worldName;
                        if (PacketParser.localName37 == username) {
                            Class91.userClanRights = rights;
                        }

                        Class167.anInt2087 = PacketParser.anInt3213;
                        Unsorted.packetOpcode = -1;
                        return true;
                    }

                    if (m < 0) {
                        break;
                    }
                }

                if (PacketParser.clanChatMembers.length <= Unsorted.clanMemberCount) {
                    Unsorted.packetOpcode = -1;
                    return true;
                }

                for (m = Unsorted.clanMemberCount + -1; m > n; --m) {
                    PacketParser.clanChatMembers[1 + m] = PacketParser.clanChatMembers[m];
                }

                if (Unsorted.clanMemberCount == 0) {
                    PacketParser.clanChatMembers = new Class3_Sub19[100];
                }

                PacketParser.clanChatMembers[1 + n] = member;
                if (PacketParser.localName37 == username) {
                    Class91.userClanRights = rights;
                }

                ++Unsorted.clanMemberCount;
            }

            Unsorted.packetOpcode = -1;
            Class167.anInt2087 = PacketParser.anInt3213;
            return true;
        }

        if(opcode == IF_SETOBJECT)
        {
            int slot = BufferedDataStream.incomingBuffer.readInt();
            int id = BufferedDataStream.incomingBuffer.readIntV2();
            int itemId = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            if (65535 == itemId) {
                itemId = -1;
            }

            int tracknum = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            Class146.setPacketTracker(tracknum);
            RSInterface var34 = Unsorted.getRSInterface(id);
            ItemDefinition itemDef;
            if (Objects.requireNonNull(var34).usingScripts) {
                Class140_Sub6.update9(id, slot, itemId);
                itemDef = ItemDefinition.getItemDefinition(itemId);
                Unsorted.updateView((byte) -128, itemDef.iconScale, id, itemDef.iconYAw, itemDef.iconPitch);
                Class84.method1420(id, itemDef.anInt768, itemDef.anInt754, itemDef.anInt792);
            } else {
                if (-1 == itemId) {
                    var34.modelType = 0;
                    Unsorted.packetOpcode = -1;
                    return true;
                }

                itemDef = ItemDefinition.getItemDefinition(itemId);
                var34.pitch = itemDef.iconPitch;
                var34.scale = 100 * itemDef.iconScale / slot;
                var34.modelType = 4;
                var34.mediaId = itemId;
                var34.yawId = itemDef.iconYAw;
                Class20.flagUpdate(var34);
            }

            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_INV_FULL)
        {
            int componentHash = BufferedDataStream.incomingBuffer.readInt();
            int containerId = BufferedDataStream.incomingBuffer.readUnsignedShort();
            RSInterface iface;
            int counter;
            int slot;
            int amount;

            if (componentHash < -70000) {
                containerId += 32768;
            }

            if (0 <= componentHash) {
                iface = Unsorted.getRSInterface(componentHash);
            } else {
                iface = null;
            }

            if (iface != null) {
                for (counter = 0; iface.itemIds.length > counter; ++counter) {
                    iface.itemIds[counter] = 0;
                    iface.amounts[counter] = 0;
                }
            }

            CS2Methods.resetContainer(containerId);
            counter = BufferedDataStream.incomingBuffer.readUnsignedShort();

            for (slot = 0; counter > slot; ++slot) {
                amount = BufferedDataStream.incomingBuffer.readUnsigned128Byte();
                if (255 == amount) {
                    amount = BufferedDataStream.incomingBuffer.readInt();
                }

                int itemId = BufferedDataStream.incomingBuffer.readUnsignedShort();
                if (null != iface && iface.itemIds.length > slot) {
                    iface.itemIds[slot] = itemId;
                    iface.amounts[slot] = amount;
                }

                PacketParser.addItemToContainer(-1 + itemId, slot, amount, containerId);
            }

            if (iface != null) {
                Class20.flagUpdate(iface);
            }

            BufferedDataStream.flagActiveInterfacesForUpdate();
            QuickChatDefinition.cs2_updatedInv[Unsorted.bitwiseAnd(Unsorted.invUpdateCount++, 31)] = Unsorted.bitwiseAnd(32767, containerId);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == SET_SETTINGS_STRING)
        {
            LinkableRSString.setSettingsString(BufferedDataStream.incomingBuffer.readString());
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == UPDATE_CURRENT_LOCATION)
        {
            Class39.currentChunkX = BufferedDataStream.incomingBuffer.readUnsignedNegativeByte();
            Class39.currentChunkY = BufferedDataStream.incomingBuffer.readUnsignedByte();
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == MIDI_SONG)
        {
            int id = BufferedDataStream.incomingBuffer.readUnsignedShortLE128();
            if (id == 65535) {
                id = -1;
            }

            AudioHandler.playMusic(id);
            Unsorted.packetOpcode = -1;
            return true;
        }

        if(opcode == MIDI_JINGLE)
        {
            // TODO: use volume!
            int volume = BufferedDataStream.incomingBuffer.getTriByte2();
            int id = BufferedDataStream.incomingBuffer.readUnsignedShortLE();
            if (id == 65535) {
                id = -1;
            }

            AudioHandler.playJingle(id);
            Unsorted.packetOpcode = -1;
            return true;
        }

        Class49.reportError("T1 - " + Unsorted.packetOpcode + "," + Class7.anInt2166 + "," + Class24.anInt469 + " - " + Unsorted.packetSize, null);
        Class167.logoutReset((byte) 46);
        return true;
    }
}
