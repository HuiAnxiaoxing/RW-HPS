package com.github.dr.rwserver.net.netconnectprotocol;

import com.github.dr.rwserver.data.Player;
import com.github.dr.rwserver.data.global.Data;
import com.github.dr.rwserver.game.GameCommand;
import com.github.dr.rwserver.game.GameMaps;
import com.github.dr.rwserver.io.GameInputStream;
import com.github.dr.rwserver.io.GameOutputStream;
import com.github.dr.rwserver.io.Packet;
import com.github.dr.rwserver.net.core.AbstractNetPacket;
import com.github.dr.rwserver.struct.Seq;
import com.github.dr.rwserver.util.PacketType;
import com.github.dr.rwserver.util.log.Log;
import com.github.dr.rwserver.util.zip.gzip.GzipEncoder;

import java.io.IOException;

/**
 * @author Dr
 */
public class GameVersionPacket implements AbstractNetPacket {
    @Override
    public Packet getSystemMessageByteBuf(String msg) throws IOException {
        return getChatMessageByteBuf(msg,"SERVER",5);
    }

    @Override
    public Packet getChatMessageByteBuf(String msg, String sendBy, int team) throws IOException {
        GameOutputStream o = new GameOutputStream();
        o.writeString(msg);
        o.writeByte(3);
        o.writeBoolean(true);
        o.writeString(sendBy);
        o.writeInt(team);
        o.writeInt(team);
        return o.createPacket(PacketType.PACKET_SEND_CHAT);
    }

    @Override
    public Packet getPingByteBuf(Player player) throws IOException {
        player.timeTemp = System.currentTimeMillis();
        GameOutputStream o = new GameOutputStream();
        o.writeLong(1000L);
        o.writeByte(0);
        return o.createPacket(PacketType.PACKET_HEART_BEAT);
    }

    @Override
    public Packet getTickByteBuf(int tick) throws IOException {
        GameOutputStream o = new GameOutputStream();
        o.writeInt(tick);
        o.writeInt(0);
        return o.createPacket(PacketType.PACKET_TICK);
    }

    @Override
    public Packet getGameTickCommandByteBuf(int tick, GameCommand cmd) throws IOException {
        GameOutputStream o = new GameOutputStream();
        o.writeInt(tick);
        o.writeInt(1);
        GzipEncoder enc = GzipEncoder.getGzipStream("c", false);
        enc.stream.write(cmd.arr);
        o.flushEncodeData(enc);
        return o.createPacket(10);
    }

    @Override
    public Packet getGameTickCommandsByteBuf(int tick, Seq<GameCommand> cmd) throws IOException {
        GameOutputStream o = new GameOutputStream();
        o.writeInt(tick);
        o.writeInt(cmd.size());
        for (GameCommand c : cmd) {
            GzipEncoder enc = GzipEncoder.getGzipStream("c", false);
            enc.stream.write(c.arr);
            o.flushEncodeData(enc);
        }
        return o.createPacket(10);
    }


    @Override
    public GzipEncoder getTeamDataByteBuf() throws IOException {
        GzipEncoder enc = GzipEncoder.getGzipStream("teams",true);
        for (int i = 0; i < Data.game.maxPlayer; i++) {
            try {
                Player player = Data.game.playerData[i];
                if (player == null) {
                    enc.stream.writeBoolean(false);
                } else {
                    enc.stream.writeBoolean(true);
                    enc.stream.writeInt(0);
                    player.writePlayer(enc.stream);
                }
            } catch (Exception e) {
                Log.error("[ALL/Player] Get Server Team Info",e);
            }
        }
        return enc;
    }

    @Override
    public Packet convertGameSaveDataByteBuf(Packet packet) throws IOException {
        try (GameInputStream stream = new GameInputStream(packet)) {
            GameOutputStream o = new GameOutputStream();
            o.writeByte(stream.readByte());
            o.writeInt(stream.readInt());
            o.writeInt(stream.readInt());
            o.writeFloat(stream.readFloat());
            o.writeFloat(stream.readFloat());
            o.writeBoolean(false);
            o.writeBoolean(false);
            stream.readBoolean();
            stream.readBoolean();
            stream.readString();
            byte[] bytes = stream.readStreamBytes();
            o.writeString("gameSave");
            o.flushMapData(bytes.length, bytes);
            return o.createPacket(35);
        }
    }

    @Override
    public Packet getStartGameByteBuf() throws IOException {
        GameOutputStream o = new GameOutputStream();
        o.writeByte(0);
        // 0->本地 1->自定义 2->保存的游戏
        o.writeInt(Data.game.maps.mapType.ordinal());
        if (Data.game.maps.mapType == GameMaps.MapType.defaultMap) {
            o.writeString("maps/skirmish/" + Data.game.maps.mapPlayer + Data.game.maps.mapName + ".tmx");
        } else {
            o.flushMapData(Data.game.maps.mapData.mapSize,Data.game.maps.mapData.bytesMap);
            o.writeString("SAVE:" + Data.game.maps.mapName + ".tmx");
        }
        o.writeBoolean(false);
        return o.createPacket(PacketType.PACKET_START_GAME);
    }

    @Override
    public String getPacketMapName(byte[] bytes) throws IOException {
        try (GameInputStream stream = new GameInputStream(bytes)) {
            stream.readString();
            stream.readInt();
            stream.readInt();
            return stream.readString();
        }
    }

    @Override
    public Packet getExitByteBuf() throws IOException {
        GameOutputStream o = new GameOutputStream();
        o.writeString("exited");
        return o.createPackets(111);
    }
}
