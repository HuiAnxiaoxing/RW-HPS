package com.github.dr.rwserver.core;

import com.github.dr.rwserver.data.Player;
import com.github.dr.rwserver.data.global.Data;
import com.github.dr.rwserver.data.global.NetStaticData;
import com.github.dr.rwserver.ga.GroupGame;
import com.github.dr.rwserver.game.GameCommand;
import com.github.dr.rwserver.struct.Seq;
import com.github.dr.rwserver.util.log.Log;
import com.github.dr.rwserver.util.zip.gzip.GzipEncoder;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.IntStream;

/**
 * @author Dr
 */
public class Call {

    public static void sendMessage(Player player, String text) {
        try {
            NetStaticData.groupNet.broadcast(NetStaticData.protocolData.abstractNetPacket.getChatMessagePacket(text,player.name,player.team),player.groupId);
        } catch (IOException e) {
            Log.error("[ALL] Send Player Chat Error",e);
        }
    }

    public static void sendMessageLocal(Player player, String text, Object... obj) {
        Data.playerGroup.eachBooleanIfs(p-> player.groupId==p.groupId,e -> e.sendMessage(player,e.localeUtil.getinput(text,obj)));
    }

    public static void sendTeamMessage(int team,Player player, String text) {
        Data.playerGroup.eachBooleanIfs(e -> e.team == team&&e.groupId== player.groupId, p -> p.sendMessage(player,"[TEAM] "+text));
    }

    public static void sendSystemTeamMessageLocal(int team,String text,int gid, Object... obj) {
        Data.playerGroup.eachBooleanIfs(e -> e.team == team&&e.groupId==gid, p -> p.sendSystemMessage("[TEAM] "+p.localeUtil.getinput(text,obj)));
    }

    public static void sendSystemMessage(String text,int gid) {
        try {
            NetStaticData.groupNet.broadcast(NetStaticData.protocolData.abstractNetPacket.getSystemMessagePacket(text),gid);
        } catch (IOException e) {
            Log.error("[ALL] Send System Chat Error",e);
        }
    }

    public static void sendSystemMessageLocal(String text,int gid, Object... obj) {
        GroupGame.playerGroup(gid).forEach(e -> e.sendSystemMessage(e.localeUtil.getinput(text,obj)));
    }

    public static void sendSystemMessage(String text,int gid, Object... obj) {
        GroupGame.playerGroup(gid).forEach(e -> e.sendSystemMessage(e.localeUtil.getinput(text,obj)));
    }

    public static void sendTeamData(int gid) {
//        if (GroupGame.games.get(gid).reConnectCount.get()>0) {
//            return;
//        }
        try {
            GzipEncoder enc = NetStaticData.protocolData.abstractNetPacket.getTeamDataPacket(gid);
            Data.playerGroup.eachBooleanIfs(p->p.groupId==gid&&p.con!=null,e -> e.con.sendTeamData(enc));
        } catch (IOException e) {
            Log.error("[ALL] Send Team Error",e);
        }
    }

    public static void sendTeamData() {
        GroupGame.games.keySet().forEach(Call::sendTeamData);
    }
    public static void sendPlayerPing() {
        try{
            GroupGame.games.forEach((x,y)->{
                if(!y.isStartGame){
                    GroupGame.playerGroup(x).forEach(p->p.con.ping());
                }
                sendTeamData(x);
            });
        }catch (Throwable e){
            e.printStackTrace();
        }
    }
//    public static void killAllPlayer() {
//        Data.playerGroup.each(e -> {
//            try {
//                e.con.sendKick("Game Over");
//            } catch (IOException err) {
//                Log.error("[ALL] Kick All Player Error",e);
//            }
//        });
//    }

    public static void killPlayers(int gid) {
        GroupGame.playersByGid(Data.playerGroup,gid).forEach(e -> {
            try {
                e.con.sendKick("Game Over");
            } catch (IOException err) {
                Log.error("[ALL] Kick All Player Error",e);
            }
        });
    }

    public static void disAllPlayer() {
        Data.playerGroup.each(e -> e.con.disconnect());
    }

    public static void testPreparationPlayer(int gid) {
        new Timer().schedule(new RandyTask(gid), 0, 100);
    }

    private static class RandyTask extends TimerTask {

        private int loadTime = 0;
        private final int loadTimeMaxTry = 30;
        private int gid;
        public RandyTask(int gid) {
            this.gid = gid;
        }

        @Override
        public void run() {
            Data.playerGroup.eachBooleanIfs(e->e.groupId==gid,p -> {
                if (!p.start) {
                    loadTime += 1;
                    if (loadTime > loadTimeMaxTry) {
                        Call.sendSystemMessageLocal("start.testNo",p.groupId);
                        GroupGame.gU(p.groupId).isReady=true;
                        cancel();
                    }
                }
            });
            GroupGame.gU(gid).isReady=true;
            cancel();
        }
    }
    public static void upDataGameData(int gid) {
        Data.playerGroup.eachBooleanIfs(p->p.groupId==gid&&p.con!=null,e -> {
            try {
                e.con.sendServerInfo(false);
            } catch (IOException err) {
                Log.error("[ALL] Send System Info Error",err);
            }
        });

    }
    public static class SendGameTickCommand implements Runnable {

        @Override
        public void run() {
            GroupGame.games.forEach((gid,rule)->{
                if (!rule.isReady||rule.reConnectBreak) {
                    return;
                }
                rule.time+=15;
                final int size = rule.gameCommandCache.size();
                if (size == 0) {
                    try {
                        NetStaticData.groupNet.broadcast(NetStaticData.protocolData.abstractNetPacket.getTickPacket(rule.time),gid);
                    } catch (IOException e) {
                        Log.error("[ALL] Send Tick Failed",e);
                    }
                } else if (size == 1 ) {
                    GameCommand gameCommand =rule.gameCommandCache.poll();
                    try {
                        NetStaticData.groupNet.broadcast(NetStaticData.protocolData.abstractNetPacket.getGameTickCommandPacket(rule.time,gameCommand),gid);
                    } catch (IOException e) {
                        Log.error("[ALL] Send Game Tick Error",e);
                    }
                } else {
                    Seq<GameCommand> comm = new Seq<>(size);
                    IntStream.range(0, size).mapToObj(i -> rule.gameCommandCache.poll()).forEach(comm::add);
                    try {
                        NetStaticData.groupNet.broadcast(NetStaticData.protocolData.abstractNetPacket.getGameTickCommandsPacket(rule.time,comm),gid);
                    } catch (IOException e) {
                        Log.error("[ALL] Send Game Ticks Error",e);
                    }
                }
            });
        }
    }
}
