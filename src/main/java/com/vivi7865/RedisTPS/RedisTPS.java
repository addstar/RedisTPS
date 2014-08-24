package com.vivi7865.RedisTPS;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisTPS extends JavaPlugin implements Listener {
	@SuppressWarnings("unused")
	private static PluginDescriptionFile plugDesc;
	public static File dataFolder;
	static JedisPool pool;
	static String serverID;

	public void onDisable() {
		Bukkit.getScheduler().cancelTasks(this);
		Jedis rsc = pool.getResource();
		try {
            rsc.hdel("RedisTPS_heartbeats", serverID);
            rsc.hdel("RedisTPS_TPS", serverID);
            rsc.hdel("RedisTPS_Players", serverID);
        } finally {
            pool.returnResource(rsc);
        }
        pool.destroy();
	}


	public void onEnable() {
		dataFolder = this.getDataFolder();
		plugDesc = this.getDescription();
		
		saveDefaultConfig();
		new Config(this, dataFolder);
		
		getLogger().info("Server ID: " + serverID);
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new TPS(), 100L, 1L);
		
		Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            public void run() {
            	long time;
            	if (Config.ntpHost.isEmpty()) {
            		time = new Date().getTime();
            	} else {
                	time = getTime();
            	}
            	Jedis rsc = pool.getResource();
                try {
                    Pipeline pipeline = rsc.pipelined();
                    pipeline.hset("RedisTPS_heartbeats", serverID, String.valueOf(time));
                    pipeline.hset("RedisTPS_TPS", serverID, String.valueOf(Math.round(TPS.getTPS(100) * 100.0) / 100.0d));
                    pipeline.hset("RedisTPS_Players", serverID, String.valueOf(Bukkit.getOnlinePlayers().length));
                    pipeline.expire("RedisTPS_heartbeats", (Config.heartbeatTimeout + 1));

                    if (Config.checkOthers) {
	                    Response<Map<String, String>> response = pipeline.hgetAll("RedisTPS_heartbeats");
	                    pipeline.sync();
	                    
	                    for (String key : response.get().keySet()) {
	                    	if (key == serverID) continue;
	                    	
	                    	if ((time - Long.parseLong(rsc.hget("RedisTPS_heartbeats", key))) > ((Config.heartbeatTimeout * 1000) + 500)) {
	                    		getLogger().log(Level.WARNING, "Server " + key + " has no refresh hearbeat for " + Config.heartbeatTimeout + " seconds, did it crash ?");
	                    		rsc.hdel("RedisTPS_heartbeats", key);
	                    	}
	                    }
                    }
                } catch (JedisConnectionException e) {
                    getLogger().log(Level.SEVERE, "Unable to refresh heartbeat, did your Redis server go away?", e);
                    pool.returnBrokenResource(rsc);
                } finally {
                	pool.returnResource(rsc);
                }
            }
        }, 0, 20*Config.checkInterval);
		
	}
	
	public long getTime() {
		
		try { 
			NTPUDPClient timeClient = new NTPUDPClient();
			InetAddress inetAddress;
			inetAddress = InetAddress.getByName(Config.getNTPHost());
			TimeInfo timeInfo = timeClient.getTime(inetAddress);
			long returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();
			Date time = new Date(returnTime);
			return time.getTime();
		} catch (UnknownHostException e) {
			getLogger().log(Level.SEVERE, "Unknown host, did your NTP server host is wrong?", e);
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "Unable to get time from NTP server, did your NTP server go away?", e);
		}
		return 0;
	}
	
	//@EventHandler
	public void onPJ(PlayerJoinEvent e) {
		
	}
	
	public void setServerID(String serverID) {
		RedisTPS.serverID = serverID;
	}

	public static JedisPool getPool() {
		return pool;
	}

	public void setPool(JedisPool pool) {
		RedisTPS.pool = pool;
	}
	
}
