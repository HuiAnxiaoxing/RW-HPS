package com.github.dr.rwserver.command;

import com.github.dr.rwserver.core.thread.Threads;
import com.github.dr.rwserver.data.global.Data;
import com.github.dr.rwserver.ga.GroupGame;
import com.github.dr.rwserver.game.EventType;
import com.github.dr.rwserver.net.game.KongZhi;
import com.github.dr.rwserver.util.game.Events;
import com.github.dr.rwserver.util.log.Log;

import java.util.concurrent.TimeUnit;

public class GameTimeLapse {
    private GameTimeLapse(){
        Events.on(EventType.GameStartEvent.class,(e)->{
            Log.clog("组"+e.getGroupId()+"开始游戏计时...");
            GroupGame.gU(e.getGroupId()).startTime=System.currentTimeMillis();
        });
        Events.on(EventType.GameOverEvent.class,(e)->{
            Log.clog("组"+e.getGroupId()+"开始战役室计时...");
            GroupGame.gU(e.getGroupId()).startTime=System.currentTimeMillis();
            GroupGame.gU(e.getGroupId()).stage=0;
        });
        Log.clog("开始战役室计时...");
    }
    public static GameTimeLapse curr=new GameTimeLapse();


    public void update(){
//        gameOverCheck();
        try{
            GroupGame.games.forEach((y,x)->{
                int lap= (int) (System.currentTimeMillis()-x.startTime);
                if(x.isStartGame){
                    if(lap>(x.stage+1)*5000*60) {
                        x.stage= (byte) (lap/300000);
                        Data.playerGroup.eachBooleanIfs(p->p.groupId==y,e -> e.sendSystemMessage("游戏已进行"+lap/60000+"分钟"));
                        Log.clog("组"+y+" 游戏已进行"+lap/60000+"分钟");
                    }
                }
            });
            KongZhi.broadCast(KongZhi.gameStateInfo());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void refresh(){
        Threads.removeScheduledFutureData("play-time");
        Log.clog("游戏计时任务开始");
        Threads.newThreadService2(this::update,1,3, TimeUnit.SECONDS,"play-time");
    }
//
//    public void gameOverCheck(){
//        if(Data.game.isStartGame&&Data.playerGroup.size()==0) {
//            Log.clog("GameTimeLapse:结束游戏");
//            Events.fire(new EventType.GameOverEvent());
//        }
//    }
}
