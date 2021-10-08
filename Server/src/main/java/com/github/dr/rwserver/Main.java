package com.github.dr.rwserver;

import com.github.dr.rwserver.command.ClientCommands;
import com.github.dr.rwserver.command.LogCommands;
import com.github.dr.rwserver.command.ServerCommands;
import com.github.dr.rwserver.core.Core;
import com.github.dr.rwserver.core.Initialization;
import com.github.dr.rwserver.data.global.Data;
import com.github.dr.rwserver.data.global.NetStaticData;
import com.github.dr.rwserver.data.plugin.PluginEventManage;
import com.github.dr.rwserver.data.plugin.PluginManage;
import com.github.dr.rwserver.dependent.LibraryManager;
import com.github.dr.rwserver.func.StrCons;
import com.github.dr.rwserver.game.Event;
import com.github.dr.rwserver.game.EventType;
import com.github.dr.rwserver.net.game.YouXiBan;
import com.github.dr.rwserver.net.netconnectprotocol.GameVersionPacket;
import com.github.dr.rwserver.plugin.UpList;
import com.github.dr.rwserver.struct.Seq;
import com.github.dr.rwserver.util.encryption.Autograph;
import com.github.dr.rwserver.util.encryption.Base64;
import com.github.dr.rwserver.util.file.FileUtil;
import com.github.dr.rwserver.util.file.LoadConfig;
import com.github.dr.rwserver.util.game.CommandHandler;
import com.github.dr.rwserver.util.game.Events;
import com.github.dr.rwserver.util.io.IoReadConversion;
import com.github.dr.rwserver.util.log.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.dr.rwserver.util.IsUtil.notIsBlank;

/**
 * @author Dr
 */
public class Main {

	/*
	 * TODO 防逆向
	 * 设置多个检查点, 定期检查, 如果发现问题就加密或混淆部分数据
	 */
	public static void main(String[] args) {
		final Initialization initialization = new Initialization();

//		Log.set("ERROR");
		Log.setPrint(true);
		Logger.getLogger("io.netty").setLevel(Level.OFF);

		System.out.println(Data.localeUtil.getinput("server.login"));
		Log.clog("Load ing...");

		//byte[] bytes = new Sha().sha256Array("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92");
		//String text = new BigInteger(1, bytes).toString(16).toUpperCase(Locale.ROOT);
		//Log.clog(ExtractUtil.bytesToHex(bytes));
		//Log.clog(ExtractUtil.bytesToHex(new BigInteger(ExtractUtil.hexToByteArray(text.toLowerCase(Locale.ROOT))).toByteArray()));
		//Log.clog(new Sha().sha256("123456"));
		//Log.clog(Arrays.toString(ExtractUtil.hexToByteArray("ff")));
		//Core.mandatoryExit();

		/* 防止修改签名 */
		/* CP #1 */
		if (!new Autograph().verify(Main.class.getProtectionDomain().getCodeSource().getLocation())) {
			Log.skipping("The server was modified and refused to start");
			Core.mandatoryExit();
		}

		FileUtil.setFilePath((args.length > 0) ? Base64.decodeString(args[0]) : null);

		//Data.core.settings.load();
		Data.core.load();

		//loadCoreJar((args.length > 1) ? Base64.decodeString(args[1]) : null);

		Log.clog(Data.localeUtil.getinput("server.hi"));

		Data.config = new LoadConfig(Data.Plugin_Data_Path,"Config.json");

//		initialization.startInit();

		/* 命令加载 */
		new ServerCommands(Data.SERVERCOMMAND);
		new ClientCommands(Data.CLIENTCOMMAND);
		new LogCommands(Data.LOGCOMMAND);
		Log.clog(Data.localeUtil.getinput("server.load.command"));

		/* Event加载 */
		PluginEventManage.add(new Event());
		Log.clog(Data.localeUtil.getinput("server.load.events"));

		/* 初始化Plugin */
		PluginManage.init(FileUtil.getFolder(Data.Plugin_Plugins_Path));
		PluginManage.runOnEnable();
		PluginManage.runRegisterClientCommands(Data.CLIENTCOMMAND);
		PluginManage.runRegisterServerCommands(Data.SERVERCOMMAND);
		PluginManage.runRegisterEvents();

		/* Core Net */
		loadNetCore();

		/* Load Save Unit */
		loadUnitList();

		/* 按键监听 */
//		Threads.newThreadCore(Main::buttonMonitoring);

		/* 加载完毕 */
		Events.fire(new EventType.ServerLoadEvent());

		/* 初始化Plugin Init */
		PluginManage.runInit();

		Log.clog("Load Plugin Jar : {0}",PluginManage.getLoadSize());


		/* 默认直接启动服务器 */
		new YouXiBan().registerServerCommands(Data.SERVERCOMMAND);
		Data.SERVERCOMMAND.handleMessage("start",(StrCons) Log::clog);
		new UpList().registerServerCommands(Data.SERVERCOMMAND);
		Data.SERVERCOMMAND.handleMessage("timer n",(StrCons) Log::clog);
	}

	private static void loadCoreJar(String libPath) {
		LibraryManager lib;
		if (notIsBlank(libPath)) {
			lib = new LibraryManager(libPath);
		} else {
			lib = new LibraryManager(true,Data.Plugin_Lib_Path);
		}
		lib.importLib("io.netty","netty-all","4.1.66.Final");
		lib.importLib("com.ip2location","ip2location-java","8.5.0");
		lib.importLib("com.alibaba","fastjson","1.2.58");
		//lib.importLib("org.bouncycastle","bcprov-jdk15on","1.69");
		loadKtJar(lib);
		//lib.importLib("org.quartz-scheduler","quartz","2.3.2");
		//lib.importLib("com.github.oshi","oshi-core","5.5.0");
		//lib.importLib("net.java.dev.jna","jna","5.7.0");
		//lib.importLib("org.slf4j","slf4j-api","1.7.30");
		lib.loadToClassLoader();
		lib.removeOldLib();
	}

	private static void loadKtJar(LibraryManager lib) {
		//lib.importLib("org.jetbrains.kotlin","bkotlin-stdlib","1.5.21");
	}

	@SuppressWarnings("InfiniteLoopStatement")
	private static void buttonMonitoring() {
		BufferedReader bufferedReader = IoReadConversion.streamBufferRead(System.in);
		while (true) {
			try {
				System.out.println(Thread.currentThread().getName());
				String str = bufferedReader.readLine();
				CommandHandler.CommandResponse response = Data.SERVERCOMMAND.handleMessage(str, (StrCons) Log::clog);
				if (response != null && response.type != CommandHandler.ResponseType.noCommand) {
					if (response.type != CommandHandler.ResponseType.valid) {
						String text;
						if (response.type == CommandHandler.ResponseType.manyArguments) {
							text = "Too many arguments. Usage: " + response.command.text + " " + response.command.paramText;
						} else if (response.type == CommandHandler.ResponseType.fewArguments) {
							text = "Too few arguments. Usage: " + response.command.text + " " + response.command.paramText;
						} else {
							text = "Unknown command. Check help";
						}
						Log.clog(text);
					}
				}
			} catch (Exception e) {
				Log.clog("Error");
				//e.printStackTrace();
			}
		}
	}

	public static void loadNetCore() {
		NetStaticData.protocolData.setNetConnectPacket(new GameVersionPacket(),"2.0.0");
		try {
			DataOutputStream stream = Data.utilData.stream;
			stream.writeInt(1);
			Seq<String> list = FileUtil.readFileListString(Objects.requireNonNull(Main.class.getResourceAsStream("/unitData-114")));
			stream.writeInt(list.size());
			String[] unitdata;
			for (String str : list) {
				unitdata = str.split("%#%");
				stream.writeUTF(unitdata[0]);
				stream.writeInt(Integer.parseInt(unitdata[1]));
				stream.writeBoolean(true);
				stream.writeBoolean(false);
				stream.writeLong(0);
				stream.writeLong(0);
			}
		} catch (Exception e) {
			Log.error(e);
		}
		Log.clog("Load OK 1.14 Protocol");
	}

	public static void loadUnitList() {
		if(Data.core.unitBase64.size() > 0) {
			try {
				Data.utilData.buffer.reset();
				DataOutputStream stream = Data.utilData.stream;
				stream.writeInt(1);
				stream.writeInt(Data.core.unitBase64.size());

				String[] unitdata;
				for (String str : Data.core.unitBase64) {
					unitdata = str.split("%#%");
					stream.writeUTF(unitdata[0]);
					stream.writeInt(Integer.parseInt(unitdata[1]));
					stream.writeBoolean(true);
					if (unitdata.length > 2) {
						stream.writeBoolean(true);
						stream.writeUTF(unitdata[2]);
					} else {
						stream.writeBoolean(false);
					}
					stream.writeLong(0);
					stream.writeLong(0);
				}
				Log.clog("Load Mod Ok.");
			} catch (Exception exp) {
				Log.error("[Server] Load Setting Unit List Error",exp);
			}
		}
	}
}