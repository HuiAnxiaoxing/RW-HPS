/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package cn.rwhps.server.game

import cn.rwhps.server.core.Call
import cn.rwhps.server.core.NetServer
import cn.rwhps.server.core.thread.Threads
import cn.rwhps.server.core.thread.TimeTaskData
import cn.rwhps.server.data.global.Data
import cn.rwhps.server.data.global.NetStaticData
import cn.rwhps.server.data.player.Player
import cn.rwhps.server.game.event.EventType
import cn.rwhps.server.net.Administration.PlayerInfo
import cn.rwhps.server.net.netconnectprotocol.realize.GameVersionServer
import cn.rwhps.server.plugin.event.AbstractEvent
import cn.rwhps.server.util.Time
import cn.rwhps.server.util.Time.millis
import cn.rwhps.server.util.game.Events
import cn.rwhps.server.util.log.Log
import cn.rwhps.server.util.log.Log.debug
import cn.rwhps.server.util.log.Log.error
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * @author RW-HPS/Dr
 */
class Event : AbstractEvent {
    override fun registerPlayerJoinEvent(player: Player) {
        if (player.name.isBlank() || player.name.length > 20) {
            player.kickPlayer(player.getinput("kick.name.failed"))
            return
        }

        if (Data.core.admin.bannedUUIDs.contains(player.uuid)) {
            try {
                player.kickPlayer(player.i18NBundle.getinput("kick.ban"))
            } catch (ioException: IOException) {
                error("[Player] Send Kick Player Error", ioException)
            }
            return
        }

        if (Data.core.admin.playerDataCache.containsKey(player.uuid)) {
            val info = Data.core.admin.playerDataCache[player.uuid]
            if (info.timesKicked > millis()) {
                try {
                    player.kickPlayer(player.i18NBundle.getinput("kick.you.time"))
                } catch (ioException: IOException) {
                    error("[Player] Send Kick Player Error", ioException)
                }
                return
            } else {
                player.muteTime = info.timeMute
            }
        }

        Call.sendSystemMessage(Data.i18NBundle.getinput("player.ent", player.name))


        if (Data.game.playerManage.playerGroup.size() >= Data.config.AutoStartMinPlayerSize && TimeTaskData.AutoStartTask == null) {
            var flagCount = 0
            TimeTaskData.AutoStartTask = Threads.newThreadService2({
                flagCount++

                if (flagCount < 60) {
                    if ((flagCount - 55) > 0) {
                        Call.sendSystemMessage(Data.i18NBundle.getinput("auto.start",(60 - flagCount)))
                    }
                    return@newThreadService2
                }
                TimeTaskData.stopAutoStartTask()

                TimeTaskData.stopPlayerAfkTask()
                if (Data.game.maps.mapData != null) {
                    Data.game.maps.mapData!!.readMap()
                }

                val enc = NetStaticData.RwHps.abstractNetPacket.getTeamDataPacket()

                Data.game.playerManage.playerGroup.each { e: Player ->
                    try {
                        e.con!!.sendTeamData(enc)
                        e.con!!.sendStartGame()
                        e.lastMoveTime = Time.concurrentSecond()
                    } catch (err: IOException) {
                        error("Start Error", err)
                    }
                }

                Data.game.isStartGame = true
                if (Data.game.sharedControl) {
                    Data.game.playerManage.playerGroup.each { it.sharedControl = true }
                }

                Data.game.playerManage.updateControlIdentifier()
                Call.testPreparationPlayer()
                Events.fire(EventType.GameStartEvent())
            },0,1,TimeUnit.SECONDS)
        }
        // ConnectServer("127.0.0.1",5124,player.con)
    }

    override fun registerPlayerConnectPasswdCheckEvent(abstractNetConnect: GameVersionServer, passwd: String): Array<String> {
        if ("" != Data.game.passwd) {
            if (passwd != Data.game.passwd) {
                try {
                    abstractNetConnect.sendErrorPasswd()
                } catch (ioException: IOException) {
                    debug("Event Passwd", ioException)
                }
                return arrayOf("true", "")
            }
        }
        return arrayOf("false", "")
    }

    override fun registerPlayerLeaveEvent(player: Player) {
        if (Data.config.OneAdmin && player.isAdmin && Data.game.playerManage.playerGroup.size() > 0) {
            try {
                val p = Data.game.playerManage.playerGroup[0]
                p.isAdmin = true
                Call.upDataGameData()
                player.isAdmin = false
                Call.sendSystemMessage("give.ok", p.name)
            } catch (ignored: IndexOutOfBoundsException) {
            }
        }

        Data.core.admin.playerDataCache.put(player.uuid, PlayerInfo(player.uuid, player.kickTime, player.muteTime))

        if (Data.game.isStartGame) {
            player.sharedControl = true
            Call.sendSystemMessage("player.dis", player.name)
            Call.sendTeamData()

        } else {
            Call.sendSystemMessage("player.disNoStart", player.name)
        }

        if (Data.game.playerManage.playerGroup.size() <= Data.config.AutoStartMinPlayerSize && TimeTaskData.AutoStartTask != null) {
            TimeTaskData.stopAutoStartTask()
        }
    }

    override fun registerGameStartEvent() {
        Data.core.admin.playerDataCache.clear()
        Log.clog("[Start New Game]")
    }

    override fun registerGameOverEvent() {
        if (Data.game.maps.mapData != null) {
            Data.game.maps.mapData!!.clean()
        }
        NetServer.reLoadServer()
        System.gc()
    }

    override fun registerPlayerBanEvent(player: Player) {
        Data.core.admin.bannedUUIDs.add(player.uuid)
        Data.core.admin.bannedIPs.add(player.con!!.ip)
        try {
            player.kickPlayer(player.i18NBundle.getinput("kick.ban"))
        } catch (ioException: IOException) {
            error("[Player] Send Kick Player Error", ioException)
        }
        Call.sendSystemMessage("ban.yes", player.name)
    }

    override fun registerPlayerIpBanEvent(player: Player) {
        Data.core.admin.bannedIPs.add(player.con!!.ip)
        try {
            player.kickPlayer("kick.ban")
        } catch (ioException: IOException) {
            error("[Player] Send Kick Player Error", ioException)
        }
        Call.sendSystemMessage("ban.yes", player.name)
    }
}