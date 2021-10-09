package com.github.dr.rwserver.net.netconnectprotocol

import com.github.dr.rwserver.core.Call
import com.github.dr.rwserver.core.thread.Threads.getIfScheduledFutureData
import com.github.dr.rwserver.core.thread.Threads.removeScheduledFutureData
import com.github.dr.rwserver.data.Player
import com.github.dr.rwserver.data.global.Data
import com.github.dr.rwserver.data.global.NetStaticData
import com.github.dr.rwserver.ga.GroupGame
import com.github.dr.rwserver.game.EventType.*
import com.github.dr.rwserver.game.GameCommand
import com.github.dr.rwserver.io.GameInputStream
import com.github.dr.rwserver.io.GameOutputStream
import com.github.dr.rwserver.io.Packet
import com.github.dr.rwserver.net.core.AbstractNetConnect
import com.github.dr.rwserver.net.game.ConnectionAgreement
import com.github.dr.rwserver.net.game.KongZhi
import com.github.dr.rwserver.net.game.cal.CalUt
import com.github.dr.rwserver.util.ExtractUtil
import com.github.dr.rwserver.util.IsUtil
import com.github.dr.rwserver.util.PacketType
import com.github.dr.rwserver.util.RandomUtil
import com.github.dr.rwserver.util.encryption.Game
import com.github.dr.rwserver.util.game.CommandHandler
import com.github.dr.rwserver.util.game.CommandHandler.CommandResponse
import com.github.dr.rwserver.util.game.Events
import com.github.dr.rwserver.util.log.Log
import com.github.dr.rwserver.util.log.Log.error
import com.github.dr.rwserver.util.zip.gzip.GzipEncoder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

/**
 * @author Dr
 * @date 2020/9/5 17:02:33
 */

class GameVersionServer(connectionAgreement: ConnectionAgreement) : AbstractGameVersion(connectionAgreement) {
    private var playerConnectKey: String? = null
    private val sync = ReentrantLock(true)

    override fun getVersionNet(connectionAgreement: ConnectionAgreement): AbstractNetConnect {
        return GameVersionServer(connectionAgreement)
    }

    override val version: String
        get() = "1.14 RW-HPS"

    override fun sendSystemMessage(msg: String) {
        if (!player.noSay) {
            super.sendSystemMessage(msg)
        }
    }

    @Throws(IOException::class)
    override fun sendServerInfo(utilData: Boolean) {
        val o = GameOutputStream()
        o.writeString(Data.SERVER_ID)
        o.writeInt(NetStaticData.protocolData.gameNetVersion)
        /* 地图 */
        o.writeInt(GroupGame.gU(player.groupId).maps.mapType.ordinal)
        o.writeString(GroupGame.gU(player.groupId).maps.mapPlayer + GroupGame.gU(player.groupId).maps.mapName)
        o.writeInt(GroupGame.gU(player.groupId).credits)
        o.writeInt(GroupGame.gU(player.groupId).mist)
        o.writeBoolean(true)
        o.writeInt(1)
        o.writeByte(7)
        o.writeBoolean(false)
        /* Admin Ui */
        o.writeBoolean(player.isAdmin)
        o.writeInt(GroupGame.gU(player.groupId).maxUnit)
        o.writeInt(GroupGame.gU(player.groupId).maxUnit)
        o.writeInt(GroupGame.gU(player.groupId).initUnit)
        o.writeFloat(GroupGame.gU(player.groupId).income)
        /* NO Nukes */
        o.writeBoolean(GroupGame.gU(player.groupId).noNukes)
        o.writeBoolean(false)
        o.writeBoolean(utilData)
        if (utilData) {
            o.flushEncodeData(Data.utilData)
        }

        /* 共享控制 */
        o.writeBoolean(GroupGame.gU(player.groupId).sharedControl)
        o.writeBoolean(false)
        o.writeBoolean(false)
        // 允许观众
        o.writeBoolean(true)
        o.writeBoolean(false)
        sendPacket(o.createPacket(PacketType.PACKET_SERVER_INFO))
    }

    override fun sendSurrender() {
        try {
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { stream ->
                    stream.writeByte(player.site)
                    val a =
                        ExtractUtil.hexToByteArray("000000ffffffffffffffff0000000000000000ffffffffffffffff00022d3100000001000000000000000000000000640000000000")
                    for (b in a) {
                        stream.writeByte(b.toInt())
                    }
                    val cmd = GameCommand(player.site, buffer.toByteArray())
                    GroupGame.gU(player.groupId).gameCommandCache.offer(cmd)
                    Call.sendSystemMessage(Data.localeUtil.getinput("player.surrender", player.name),player.groupId)
                }
            }
        } catch (ignored: Exception) {
        }
    }

    @Throws(IOException::class)
    override fun receiveChat(p: Packet) {
        GameInputStream(p).use { stream ->
            var message: String? = stream.readString()
            var response: CommandResponse? = null
            if(message?.contains("-i desync:Player") == false &&
                !message.contains("-i 同步:Player") &&
                !message.contains("suppressing desync errors")
                    ){
                KongZhi.broadCast("-chat\n"+player.uuid+"\n"+message)
                Log.clog("[{0}]: {1}", player.name, message)
            }
            if (player.isAdmin && getIfScheduledFutureData("AfkCountdown")) {
                removeScheduledFutureData("AfkCountdown")
                Call.sendMessage(player, Data.localeUtil.getinput("afk.clear", player.name))
            }
            if (message!!.startsWith(".") || message.startsWith("-") || message.startsWith("_")) {
                val strEnd = min(message.length, 3)
                response = if ("qc" == message.substring(1, strEnd)) {
                    Data.CLIENTCOMMAND.handleMessage("/" + message.substring(5), player)
                } else {
                    Data.CLIENTCOMMAND.handleMessage("/" + message.substring(1), player)
                }
            }
            if (response == null || response.type == CommandHandler.ResponseType.noCommand) {
                if (message.length > GroupGame.gU(player.groupId).maxMessageLen) {
                    sendSystemMessage(Data.localeUtil.getinput("message.maxLen"))
                    return
                }
                message = Data.core.admin.filterMessage(player, message)
                if (message == null) {
                    return
                }
                Call.sendMessage(player, message)
                Events.fire(PlayerChatEvent(player, message))
            } else {
                if (response.type != CommandHandler.ResponseType.valid) {
                    val text: String
                    text = if (response.type == CommandHandler.ResponseType.manyArguments) {
                        "Too many arguments. Usage: " + response.command.text + " " + response.command.paramText
                    } else if (response.type == CommandHandler.ResponseType.fewArguments) {
                        "Too few arguments. Usage: " + response.command.text + " " + response.command.paramText
                    } else {
                        "Unknown command. Check .help"
                    }
                    player.sendSystemMessage(text)
                }
            }
        }
    }
    @Throws(IOException::class)
    override fun receiveCommand(p: Packet) {
        //PlayerOperationUnitEvent
        GroupGame.MOVE_LOCK.get(player.groupId-1).lock()
        try {
            GameInputStream(GameInputStream(p).getDecodeBytes()).use { inStream ->
                val outStream = GameOutputStream()
                outStream.writeByte(inStream.readByte())
                val boolean1 = inStream.readBoolean()
                outStream.writeBoolean(boolean1)
                if (boolean1) {
                    outStream.writeInt(inStream.readInt())
                    val int1 = inStream.readInt()
                    outStream.writeInt(int1)
                    if (int1 == -2) {
                        outStream.writeString(inStream.readString())
                    }
                    outStream.writeBytes(inStream.stream.readNBytes(28))
                    outStream.writeIsString(inStream)
                }
                outStream.writeBytes(inStream.stream.readNBytes(10))
                val boolean3 = inStream.readBoolean()
                outStream.writeBoolean(boolean3)
                if (boolean3) {
                    outStream.writeBytes(inStream.stream.readNBytes(8))
                }
                outStream.writeBoolean(inStream.readBoolean())
                val int2 = inStream.readInt()
                outStream.writeInt(int2)
                for (i in 0 until int2) {
                    outStream.writeBytes(inStream.stream.readNBytes(8))
                }
                val boolean4 = inStream.readBoolean()
                outStream.writeBoolean(boolean4)
                if (boolean4) {
                    outStream.writeByte(inStream.readByte())
                }
                val boolean5 = inStream.readBoolean()
                outStream.writeBoolean(boolean5)
                if (boolean5) {
                    outStream.writeBytes(inStream.stream.readNBytes(8))
                }
                outStream.writeBytes(inStream.stream.readNBytes(8))
                outStream.writeString(inStream.readString())
                outStream.writeBoolean(inStream.readBoolean())
                inStream.readShort()
                outStream.writeShort(GroupGame.gU(player.groupId).sharedControlPlayer.toShort())
                outStream.flushData(inStream)
                GroupGame.gU(player.groupId).gameCommandCache.offer(GameCommand(player.site, outStream.getPacketBytes()))
            }
        } catch (e: Exception) {
            Log.error(e)
        } finally {
            GroupGame.MOVE_LOCK.get(player.groupId-1).unlock()
        }
    }

    @Throws(IOException::class)
    override fun getGameSaveData(packet: Packet): ByteArray {
        GameInputStream(packet).use { stream ->
            val o = GameOutputStream()
            o.writeByte(stream.readByte())
            o.writeInt(stream.readInt())
            o.writeInt(stream.readInt())
            o.writeFloat(stream.readFloat())
            o.writeFloat(stream.readFloat())
            o.writeBoolean(false)
            o.writeBoolean(false)
            stream.readBoolean()
            stream.readBoolean()
            stream.readString()
            return stream.readStreamBytes()
        }
    }

    @Throws(IOException::class)
    override fun sendStartGame() {
        sendServerInfo(true)
        sendPacket(NetStaticData.protocolData.abstractNetPacket.getStartGamePacket(player.groupId))
        if (IsUtil.notIsBlank(GroupGame.gU(player.groupId).startAd)) {
            sendSystemMessage(GroupGame.gU(player.groupId).startAd)
        }
    }

    override fun sendTeamData(gzip: GzipEncoder) {
        try {
            val rule=GroupGame.gU(player.groupId);
            val o = GameOutputStream()
            /* 玩家位置 */
            o.writeInt(player.site)
            o.writeBoolean(rule.isStartGame)
            /* 最大玩家 */
            o.writeInt(rule.maxPlayer)
            o.flushEncodeData(gzip)
            /* 迷雾 */
            o.writeInt(rule.mist)
            o.writeInt(rule.credits)
            o.writeBoolean(true)
            /* AI Difficulty ?*/
            o.writeInt(1)
            o.writeByte(5)
            o.writeInt(rule.maxUnit)
            o.writeInt(rule.maxUnit)
            /* 初始单位 */
            o.writeInt(rule.initUnit)
            /* 倍速 */
            o.writeFloat(rule.income)
            /* NO Nukes */
            o.writeBoolean(rule.noNukes)
            o.writeBoolean(false)
            o.writeBoolean(false)
            /* 共享控制 */
            o.writeBoolean(rule.sharedControl)
            /* 游戏暂停 */
            o.writeBoolean(false)
            sendPacket(o.createPacket(PacketType.PACKET_TEAM_LIST))
        } catch (e: IOException) {
            Log.error("Team", e)
        }
    }

    @Throws(IOException::class)
    override fun getPlayerInfo(p: Packet): Boolean {
        try {
            GameInputStream(p).use { stream ->
                stream.readString()
                stream.readInt()
                stream.readInt()
                stream.readInt()
                var name = stream.readString()
//                Log.debug("name", name)
                val passwd = stream.isReadString()
//                Log.debug("passwd", passwd)
                stream.readString()
                val uuid = stream.readString()
//                Log.debug("uuid", uuid)
                 stream.readInt()
                val token = stream.readString()
//                Log.debug("token", token)
                /*
            if (!token.equals(playerConnectKey)) {
                sendKick("You Open Mod?");
                return false;
            }
             */
//                val playerConnectPasswdCheck = PlayerConnectPasswdCheckEvent(this, passwd)
//                Events.fire(playerConnectPasswdCheck)
//                if (playerConnectPasswdCheck.result) {
//                    return true
//                }
//                if (IsUtil.notIsBlank(playerConnectPasswdCheck.name)) {
//                    name = playerConnectPasswdCheck.name
//                }
//
//             val playerJoinName = PlayerJoinNameEvent(name)
//                Events.fire(playerJoinName)
//                if (IsUtil.notIsBlank(playerJoinName.resultName)) {
//                    name = playerJoinName.resultName
//                }

                inputPassword = false

                if (Data.playerGroup.size() >=Data.game.gMaxPlayer) {
                    if (IsUtil.isBlank(Data.game.maxPlayerAd)) {
                        sendKick("服务器没有位置 # The server has no free location")
                    } else {
                        sendKick(Data.game.maxPlayerAd)
                    }
                    Log.clog("因服务器已满，拒绝连接："+player.con.ip)
                    return false
                }
                val re = AtomicBoolean(false)
                Data.playerAll.each(
                    { i: Player -> i.uuid == uuid }
                ) { e: Player? ->
                    re.set(true)
                    player = e!!
                    player.con = this
                    super.isDis=false;
                    Data.playerGroup.add(e)
                }
                if (!re.get()||!GroupGame.gU(player.groupId).isStartGame) {
                    var localeUtil = Data.localeUtilMap["CN"]

                    if (Data.game.ipCheckMultiLanguageSupport) {
                        val rec = Data.ip2Location.IPQuery(connectionAgreement.ip)
                        if ("OK" != rec.status) {
                            localeUtil = Data.localeUtilMap[rec.countryShort]
                        }
                    }
                    player = Player.addPlayer(GroupGame.newPlayerGroupId(), this, uuid, name, localeUtil)
                    connectionAgreement.add(NetStaticData.groupNet,player.groupId)
                    Call.sendTeamData(player.groupId)
                    sendServerInfo(true)
                    if (IsUtil.notIsBlank(GroupGame.gU(player.groupId).enterAd)) {
                        sendSystemMessage(GroupGame.gU(player.groupId).enterAd)
                    }
                    sendSystemMessage("此服务器为rw-hps分支，注意，稳定性不保证！")
                    sendSystemMessage("欢迎加入，您在第"+player.groupId+"组房间")
                }else if (reConnect()){
                    Call.sendSystemMessage("玩家 "+player.name+"重连完成",player.groupId);
                    Log.clog("玩家 "+player.name+"重连完成",player.groupId)
                    Events.fire(PlayerReJoinEvent(player))
                }else return false
//                connectionAgreement.add(NetStaticData.groupNet,player.groupId)
//                Call.sendTeamData(player.groupId)
                CalUt.channelInfos.get(connectionAgreement.getChannel().id())?.p = player
                Events.fire(PlayerJoinEvent(player))
                return true
            }
        } finally {
            playerConnectKey = null
        }
    }

    @Throws(IOException::class)
    override fun registerConnection(p: Packet) {
        // 生成随机Key;
        val keyLen = 6
        val key = RandomUtil.generateInt(keyLen)
        playerConnectKey = Game.connectKey(key)
        GameInputStream(p).use { stream ->
            // Game Pkg Name
            stream.readString()
            // 返回都是1 有啥用
            stream.readInt()
            stream.readInt()
            stream.readInt()
            val o = GameOutputStream()
            o.writeString(Data.SERVER_ID)
            o.writeInt(1)
            o.writeInt(NetStaticData.protocolData.gameNetVersion)
            o.writeInt(NetStaticData.protocolData.gameNetVersion)
            o.writeString("com.corrodinggames.rts.server")
            o.writeString(Data.core.serverConnectUuid)
            o.writeInt(key)
            sendPacket(o.createPacket(PacketType.PACKET_REGISTER_CONNECTION))
        }
    }

    /**
     *
     */
    override fun sendErrorPasswd() {
        try {
            val o = GameOutputStream()
            o.writeInt(0)
            sendPacket(o.createPacket(PacketType.PACKET_PASSWD_ERROR))
            inputPassword = true
        } catch (e: Exception) {
            Log.error("[Player] sendErrorPasswd", e)
        }
    }

    override fun disconnect() {
        if (super.isDis) {
            return
        }
        super.isDis = true
        if (IsUtil.notIsBlank(player)) {
            Data.playerGroup.remove(player)
            if (!GroupGame.gU(player.groupId).isStartGame) {
                Data.playerAll.remove(player)
                player.clear()
                GroupGame.gU(player.groupId).playerData[player.site] = null
            }
            Events.fire(PlayerLeaveEvent(player))
        }
        super.close(NetStaticData.groupNet)
    }

    override fun getGameSave() {
        try {
            val o = GameOutputStream()
            o.writeByte(0)
            o.writeInt(0)
            o.writeInt(0)
            o.writeFloat(0f)
            o.writeFloat(0f)
            o.writeBoolean(true)
            o.writeBoolean(false)
            val gzipEncoder = GzipEncoder.getGzipStream("gameSave", false)
            val io = gzipEncoder.stream
            io.writeUTF("This is RW-HPS!")
            o.flushEncodeData(gzipEncoder)
            sendPacket(o.createPacket(35))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    override fun sendGameSave(packet: Packet) {
        sendPacket(packet)
    }

    override fun  reConnect() :Boolean{
        val groupRule = GroupGame.gU(player.groupId)
        try {
            Log.clog("玩家"+player.name+"尝试重连:"+player.con.ip)
            if (!groupRule.reConnect) {
                sendKick("抱歉，不支持重连")
                return false;
            }
            if(groupRule.reConnectBreak) {
                sendKick("其他玩家正在重连，请稍后再试")
                return false;
            };
            connectionAgreement.add(NetStaticData.groupNet,player.groupId)
//            Call.sendTeamData(player.groupId)
            val enc = NetStaticData.protocolData.abstractNetPacket.getTeamDataPacket(player.groupId)
            player.con.sendTeamData(enc)
            sendServerInfo(true)
            sendPacket(NetStaticData.protocolData.abstractNetPacket.getStartGamePacket(player.groupId))
            try {
                groupRule.reConnectBreak=true
                val iterator: Iterator<Player> = Data.playerGroup.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    if(player.groupId!=next.groupId||player==next||!next.canSave||next.con==null) continue;
                    var times=6;
                    next.con.getGameSave();
                    while (times-->0){
                        if(groupRule.gameSaveCache!=null) break;
                        Call.sendSystemMessage(player.name+"正在重连，尝试获取"+next.name+"保存包（"+times+")",player.groupId)
                        Thread.sleep(1000)
                    }
                    if(groupRule.gameSaveCache!=null&&groupRule.gameSaveCache.type!=0) break;
                    next.canSave=false;
                }
                if(groupRule.gameSaveCache==null) {
                    sendKick("无法获取保存包")
                    return false
                };
                try {
                    val saveC=NetStaticData.protocolData.abstractNetPacket.convertGameSaveDataPacket(groupRule.gameSaveCache);
                    NetStaticData.groupNet.broadcast(saveC,player.groupId)
                    return true;
                } catch (e: IOException) {
                   throw RuntimeException(e)
                }
            } catch (e: Throwable) {
                Log.error(e)
                throw RuntimeException(e)
            } finally {
                groupRule.reConnectBreak=false
                groupRule.gameSaveCache = null
            }
        } catch (e: Throwable) {
            Log.error("[Player] Send GameSave ReConnect Error", e)
            connectionAgreement.close(NetStaticData.groupNet)
            e.printStackTrace()
            super.isDis=true
            return false;
        }
    }
    private fun reConAutoCloseBreak(gid:Int){
        if (GroupGame.games[gid]!!.isStartGame) {
            val players = GroupGame.playersByGid(Data.playerGroup, gid)
            if (players.size >= 1 ) {
                removeScheduledFutureData("Gameover$gid")
            }
            if (players.stream().map { p: Player -> p.team }.distinct().toArray().size > 1) {
                removeScheduledFutureData("Gameover-t$gid")
            }
        }
    }
    override fun sendRelayServerType(msg: String) {
        try {
            val o = GameOutputStream()
            // 理论上是随机数？
            o.writeByte(1)
            o.writeInt(5) //可能和-AX一样
            o.writeString(msg)
            sendPacket(o.createPacket(117)) //->118
            inputPassword = true
        } catch (e: java.lang.Exception) {
            error(e)
        }
    }

    override fun sendRelayServerTypeReply(packet: Packet) {
        GameInputStream(packet).use { stream ->
            stream.buffer.readNBytes(5)
            //这个就是回复 但是我找不到什么好方法
            //stream.readString()
        }
    }
}