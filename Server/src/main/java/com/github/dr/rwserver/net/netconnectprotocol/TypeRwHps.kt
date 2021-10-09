package com.github.dr.rwserver.net.netconnectprotocol

import com.github.dr.rwserver.ga.GroupGame
import com.github.dr.rwserver.io.Packet
import com.github.dr.rwserver.net.core.AbstractNetConnect
import com.github.dr.rwserver.net.core.TypeConnect
import com.github.dr.rwserver.util.PacketType
import com.github.dr.rwserver.util.Time.millis
import com.github.dr.rwserver.util.log.Log

class TypeRwHps : TypeConnect {
    @Throws(Exception::class)
    override fun typeConnect(con: AbstractNetConnect, packet: Packet) {
        con.setLastReceivedTime()
        if (packet.type == PacketType.PACKET_ADD_GAMECOMMAND) {
            con.player?.let { GroupGame.gU(it.groupId) }
            con.receiveCommand(packet)
            con.player!!.lastMoveTime = millis()
        } else {
            when (packet.type) {
                PacketType.PACKET_PREREGISTER_CONNECTION -> con.registerConnection(packet)
                PacketType.PACKET_PLAYER_INFO -> con.getPlayerInfo(packet)
                PacketType.PACKET_HEART_BEAT_RESPONSE -> {
                    val player = con.player
                    player!!.ping = (System.currentTimeMillis() - player.timeTemp).toInt() shr 1
                }
                PacketType.PACKET_ADD_CHAT -> con.receiveChat(packet)
                PacketType.PACKET_DISCONNECT -> {
                    try{
                        Log.clog("组"+con.player?.groupId+"玩家"+con.player?.name+"主动断开")
                    }catch (e:Throwable){}
                    con.disconnect()
                }
                PacketType.PACKET_ACCEPT_START_GAME -> con.player!!.start = true
                PacketType.PACKET_SERVER_DEBUG -> con.debug(packet)
                PacketType.PACKET_SYNC ->GroupGame.gU(con.player?.groupId!!).gameSaveCache = packet

                118 -> con.sendRelayServerTypeReply(packet)

                else -> {
                }
            }
        }
    }

    override val version: String
        get() = "2.0.0"
}