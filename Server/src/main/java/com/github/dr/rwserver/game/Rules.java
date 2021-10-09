package com.github.dr.rwserver.game;

import com.github.dr.rwserver.core.thread.Threads;
import com.github.dr.rwserver.custom.CustomEvent;
import com.github.dr.rwserver.data.Player;
import com.github.dr.rwserver.data.global.Data;
import com.github.dr.rwserver.io.Packet;
import com.github.dr.rwserver.struct.OrderedMap;
import com.github.dr.rwserver.struct.Seq;
import com.github.dr.rwserver.util.IsUtil;
import com.github.dr.rwserver.util.encryption.Base64;
import com.github.dr.rwserver.util.encryption.Sha;
import com.github.dr.rwserver.util.file.FileUtil;
import com.github.dr.rwserver.util.file.LoadConfig;
import com.github.dr.rwserver.util.log.Log;
import com.github.dr.rwserver.util.zip.zip.ZipDecoder;

import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Dr
 */
public class Rules {
    /** 端口 */
    public int port = 5123;
    /** 是否已启动游戏 */
    public boolean isStartGame = false;
    public boolean isReady=false;
    public int time=0;
    public long startTime;
    public byte stage;
    /** 倍数 */
    public float income = 1f;
    /** 初始钱 */
    public int credits = 0;
    /** 最大玩家 */
    public int maxPlayer;
    public int gMaxPlayer;
    /** 地图数据 */
    public final GameMaps maps = new GameMaps();
    /** nukes */
    public boolean noNukes = false;
    /** 初始单位 */
    public int initUnit = 1;
    /** 迷雾 */
    public int mist = 2;
    /** 共享控制 */
    public boolean sharedControl = false;
    /** 密码 */
    public final String passwd;
    /** 按键包缓存 */
    public final LinkedBlockingQueue<GameCommand> gameCommandCache = new LinkedBlockingQueue<>();
    /** 混战分配 */
    public boolean amTeam = false;
    /** 队伍数据 */
    public volatile Player[] playerData;
    /** 最大发言长度 */
    public final int maxMessageLen;
    /** 最大单位数 */
    public final int maxUnit;
    /** AFK */
    public boolean isAfk = true;
    /** only Admin */
    public final boolean oneAdmin;
    /** ip多语言支持 */
    public final boolean ipCheckMultiLanguageSupport;
    /** 重连暂停 */
    public boolean reConnectBreak = false;
    /** 重连缓存 GameSave */
    public volatile Packet gameSaveCache = null;
    /** 是否启用重连 */
    public boolean reConnect;
    /** 是否启用胜负判定 */
    public final boolean winOrLose;
    /** 胜负判定时间 */
    public final int winOrLoseTime;
    /** 是否第一次启动读取MOD */
    public boolean oneReadUnitList;
    /** 共享控制 */
    public int sharedControlPlayer = 0;
    /** Mpa Lock */
    public boolean mapLock = false;

    /** AD */
    public  String enterAd;
    public  String startAd;
    public final String maxPlayerAd;
    public final String startPlayerAd;
    public final String serverUpID;

    /* */
    public final boolean webApi;
    public final String webUrl;
    public final int webApiPort;
    public final boolean webApiSsl;
    public final String webApiSslKetPath;
    public final String webApiSslPasswd;
    /* */
    public boolean lockTeam = false;
    /* */
    public final int startMinPlayerSize;

    public final String subtitle;

    public final OrderedMap<String,GameMaps.MapData> mapsData = new OrderedMap<>(8);

    public Rules(LoadConfig config) {
        startTime=System.currentTimeMillis();
        subtitle = config.readString("subtitle","");

        int port = config.readInt("port",5123);
        String passwdCache = config.readString("passwd","");
        passwd = IsUtil.notIsBlank(passwdCache) ? new BigInteger(1, new Sha().sha256Array(passwdCache)).toString(16).toUpperCase(Locale.ROOT) : "";

        enterAd = config.readString("enterServerAd","");
        startAd = config.readString("startAd","");
        maxPlayerAd = config.readString("maxPlayerAd","");
        startPlayerAd = config.readString("startPlayerAd","");
        serverUpID = config.readString("serverUpID","");

        if (config.readBoolean("readMap",false)) {
            try {
                checkMaps();
                Log.clog(Data.localeUtil.getinput("server.load.maps"));
            } catch (Exception exp) {
                Log.debug("Read Error",exp);
            }
        }
        maxMessageLen = config.readInt("maxMessageLen",40);
        maxUnit = config.readInt("maxUnit",200);
        Data.core.defIncome = config.readFloat("defIncome",100f);
        if(Data.core.defIncome<100) Data.core.defIncome=100;
        Data.core.serverName = config.readString("serverName","RW-HPS");
        oneAdmin = config.readBoolean("oneAdmin",true);
        ipCheckMultiLanguageSupport = config.readBoolean("iPCheckMultiLanguageSupport",false);

        reConnect = config.readBoolean("reConnect",true);
        winOrLose = config.readBoolean("winOrLose",false);
        winOrLoseTime = config.readInt("winOrLoseTime",30000);

        webApi = config.readBoolean("webApi",false);
        webUrl = config.readString("webUrl","");
        webApiPort = config.readInt("webApiPort",0);
        webApiSsl = config.readBoolean("webApiSsl",false);
        webApiSslKetPath = config.readString("webApiSslKetPath","");
        webApiSslPasswd = config.readString("webApiSslPasswd","");
        startMinPlayerSize = config.readInt("startMinPlayerSize",0);
        init(config.readInt("maxPlayer",10),port);
        gMaxPlayer=config.readInt("gMaxPlayer",120);
//        autoLoadOrUpdate(config);
    }

    public void init() {
        new CustomEvent();
    }

    public void init(int maxPlayer,int port) {
        this.maxPlayer = maxPlayer;
        playerData = new Player[maxPlayer];
        this.port = port;
        income = Data.core.defIncome;
    }

    public void re() {
        gameCommandCache.clear();
        gameSaveCache=null;
        Arrays.fill(playerData, null);
        income = Data.core.defIncome;
        initUnit = 1;
        mist = 2;
        sharedControl = false;
        isStartGame=false;
        isReady=false;
        time=0;
        //重新配置
        enterAd = Data.config.readString("enterServerAd","");
        startAd = Data.config.readString("startAd","");
        gMaxPlayer=Data.config.readInt("gMaxPlayer",120);
        reConnect =Data.config.readBoolean("reConnect",true);
    }

    public void checkMaps() {
        Seq<File> list = FileUtil.getFolder(Data.Plugin_Maps_Path).getFileListNotNullSize();
        list.each(e -> {
            final String original = Base64.isBase64(e.getName()) ? Base64.decodeString(e.getName()) : e.getName();
            final String postpone = original.substring(original.lastIndexOf("."));
            final String name = original.substring(0, original.length()-postpone.length());
            switch (postpone) {
                case ".tmx":
                    try {
                        mapsData.put(name,new GameMaps.MapData(GameMaps.MapType.customMap, GameMaps.MapFileType.file, name));
                    } catch (Exception exception) {
                        Log.error("read tmx Maps",exception);
                    }
                    break;
                case ".save":
                    try {
                        mapsData.put(name,new GameMaps.MapData(GameMaps.MapType.savedGames, GameMaps.MapFileType.file, name));
                    } catch (Exception exception) {
                        Log.error("read save Maps",exception);
                    }
                    break;
                case ".zip":
                    try {
                        Seq<String> zipTmx = new ZipDecoder(e).getTheFileNameOfTheSpecifiedSuffixInTheZip("tmx");
                        zipTmx.each(zipMapName -> mapsData.put(zipMapName,new GameMaps.MapData(GameMaps.MapType.customMap, GameMaps.MapFileType.zip , zipMapName, original)));
                        Seq<String> zipSave = new ZipDecoder(e).getTheFileNameOfTheSpecifiedSuffixInTheZip("save");
                        zipSave.each(zipSaveName -> mapsData.put(zipSaveName,new GameMaps.MapData(GameMaps.MapType.savedGames, GameMaps.MapFileType.zip , zipSaveName, original)));

                    } catch (Exception exception) {
                        Log.error("ZIP READ",exception);
                    }
                    break;
                default:
                    break;
            }
        });
    }

    private void autoLoadOrUpdate(LoadConfig config) {
        if (config.readBoolean("autoReLoadMap",false)) {
            Threads.newThreadService2(() -> {
                if (IsUtil.notIsBlank(this) && !this.isStartGame) {
                    this.mapsData.clear();
                    this.checkMaps();
                }
            },0,1, TimeUnit.MINUTES,"AutoReLoadMap");
        }
    }
}
