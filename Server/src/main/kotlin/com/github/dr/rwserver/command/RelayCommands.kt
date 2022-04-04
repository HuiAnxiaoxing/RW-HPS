/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.github.dr.rwserver.command

import com.github.dr.rwserver.data.global.Data
import com.github.dr.rwserver.data.global.Relay
import com.github.dr.rwserver.data.plugin.PluginManage
import com.github.dr.rwserver.func.StrCons
import com.github.dr.rwserver.util.IpUtil
import com.github.dr.rwserver.util.IsUtil.isBlank
import com.github.dr.rwserver.util.game.CommandHandler

class RelayCommands(handler: CommandHandler) {
    private fun registerRelayCommand(handler: CommandHandler) {
        handler.register("players", "serverCommands.players") { _: Array<String>?, log: StrCons ->
            if (Relay.serverRelayIpData.size() == 0) {
                log["No players are currently in the server."]
            } else {
                log[Relay.relayAllIP]
            }
        }
        handler.register("banrelay", "<id>", "serverCommands.banrelay") { arg: Array<String>, log: StrCons ->
            val relay = Relay.getRelay(arg[0])
            if (isBlank(relay)) {
                log["NOT RELAY ROOM"]
            } else {
                relay!!.groupNet.disconnect()
                relay.sendMsg("You are banned by the administrator, please do not occupy public resources")
                val ip = relay.admin!!.ip
                Data.core.admin.bannedIP24.add(IpUtil.ipToLong24(ip))
                relay.admin!!.disconnect()
                log["OK!  $ip The *.*.*.0 segment is disabled"]
            }

        }

        handler.register("unbanrelay", "<ip>", "serverCommands.unBanrelay") { arg: Array<String>, log: StrCons ->
            val ip = arg[0]
            Data.core.admin.bannedIP24.remove(IpUtil.ipToLong24(ip))
            log["OK!  $ip The *.*.*.0 segment is unDisabled"]
        }

        handler.register("bans",  "serverCommands.bans") { arg: Array<String>, log: StrCons ->
            if (Data.core.admin.bannedIP24.size() == 0) {
                log["No bans are currently in the server."]
            } else {
                log["Bans: {0}", Data.core.admin.bannedIP24.size()]
                val data = StringBuilder()
                for (ipLong in Data.core.admin.bannedIP24) {
                    data.append(Data.LINE_SEPARATOR)
                        .append("IP: ").append(IpUtil.long24ToIp(ipLong))
                }
                log[data.toString()]
            }
        }
    }

    companion object {
        private val localeUtil = Data.i18NBundle
    }

    init {
        registerRelayCommand(handler)

        PluginManage.runRegisterRelayCommands(handler)
    }
}