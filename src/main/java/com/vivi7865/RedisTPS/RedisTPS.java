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
import org.bukkit.plugin.java.JavaPlugin;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisTPS extends JavaPlugin implements Listener {
	public static File dataFolder;
	static JedisPool pool;
	static String serverID;
	final Runtime runtime = Runtime.getRuntime();

	public void onDisable() {
		Bukkit.getScheduler().cancelTasks(this);
		Jedis rsc = pool.getResource();
		if (rsc != null) {
			try {
	            rsc.hdel("RedisTPS_Heartbeats", serverID);
	            rsc.hdel("RedisTPS_TPS", serverID);
	            rsc.hdel("RedisTPS_Players", serverID);
	        } finally {
	            pool.returnResource(rsc);
	        }
	        pool.destroy();
		}
	}


	public void onEnable() {
		dataFolder = this.getDataFolder();
		
		saveDefaultConfig();
		new Config(this, dataFolder);
		
		getLogger().info("Server ID: " + serverID);
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new TPS(), 100L, 1L);
		
		Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
			int runcount = 0;
            public void run() {
            	boolean doHeartbeat = false;
            	boolean doTPS = false;
            	boolean doPlayers = false;
            	boolean doMemory = false; 
            	boolean doCheckOthers = false;

            	if (runcount == 86400) runcount = 0;  // loop every 24h
            	runcount++;

            	if (Config.intervalHeartbeat > 0 && (runcount % Config.intervalHeartbeat) == 0) doHeartbeat = true; 
            	if (Config.intervalTPS > 0 && (runcount % Config.intervalTPS) == 0) doTPS = true; 
            	if (Config.intervalPlayers > 0 && (runcount % Config.intervalPlayers) == 0) doPlayers = true; 
            	if (Config.intervalMemory > 0 && (runcount % Config.intervalMemory) == 0) doMemory = true; 
            	if (Config.checkOthers > 0 && (runcount % Config.checkOthers) == 0) doCheckOthers = true; 

            	// Abort this run if we don't have anything to do
            	if (!doHeartbeat && !doTPS && !doPlayers && !doMemory && !doCheckOthers) return;

            	long time;
            	if (Config.ntpHost.isEmpty()) {
            		time = new Date().getTime();
            	} else {
                	time = getTime();
            	}

            	if (pool == null) {
            		getLogger().warning("Not connected to Redis!");
            		return;
            	}

            	Jedis rsc = null;
            	try {
            		rsc = pool.getResource();
            	} catch (JedisConnectionException e) {
            		getLogger().log(Level.SEVERE, "Unable to obtain valid Redis resource, did your Redis server go away?");
            	}
            	
            	if (rsc != null) {
	                try {
	                    Pipeline pipeline = rsc.pipelined();
	                    if (doHeartbeat) pipeline.hset("RedisTPS_Heartbeats", serverID, String.valueOf(time));
	                    if (doTPS) pipeline.hset("RedisTPS_TPS", serverID, String.valueOf(Math.round(TPS.getTPS(100) * 100.0) / 100.0d));
	                    if (doPlayers) pipeline.hset("RedisTPS_Players", serverID, String.valueOf(Bukkit.getOnlinePlayers().length));
	
	                    if (doMemory) {
	                    	pipeline.hset("RedisTPS_RamMax", serverID, String.valueOf(getMaxRam()));
	                    	pipeline.hset("RedisTPS_RamTotal", serverID, String.valueOf(getTotalRam()));
	                    	pipeline.hset("RedisTPS_RamFree", serverID, String.valueOf(getFreeRam()));
	                        pipeline.expire("RedisTPS_RamMax", (Config.intervalMemory + 5));
	                        pipeline.expire("RedisTPS_RamTotal", (Config.intervalMemory + 5));
	                        pipeline.expire("RedisTPS_RamFree", (Config.intervalMemory + 5));
	                    }
	
	                    if (doCheckOthers) {
	                        pipeline.expire("RedisTPS_Heartbeats", (Config.heartbeatTimeout + 1));
		                    Response<Map<String, String>> response = pipeline.hgetAll("RedisTPS_Heartbeats");
		                    pipeline.sync();
		                    
		                    for (String key : response.get().keySet()) {
		                    	if (key == serverID) continue;
		                    	
		                    	if ((time - Long.parseLong(response.get().get(key))) > ((Config.heartbeatTimeout * 1000) + 500)) {
		                    		getLogger().log(Level.WARNING, "Server " + key + " has no refresh hearbeat for " + Config.heartbeatTimeout + " seconds, did it crash ?");
		                    		rsc.hdel("RedisTPS_Heartbeats", key);
		                    	}
		                    }
	                    }
	                } catch (JedisConnectionException e) {
	                    getLogger().log(Level.SEVERE, "Unable to refresh stats, did your Redis server go away?", e);
	                    pool.returnBrokenResource(rsc);
	                } finally {
	                	pool.returnResource(rsc);
	                }
            	}
            }
        }, 100L, 20L);
		
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
	
	public void setServerID(String serverID) {
		RedisTPS.serverID = serverID;
	}

	public static JedisPool getPool() {
		return pool;
	}

	public void setPool(JedisPool pool) {
		RedisTPS.pool = pool;
	}
	
	public final int getFreeRam() {
        return Math.round((float)(runtime.freeMemory() / 1048576L));
    }
 
	public final int getMaxRam() {
        return Math.round((float)(runtime.maxMemory() / 1048576L));
    }

    public final int getTotalRam() {
        return Math.round((float)(runtime.totalMemory() / 1048576L));
    }
    
    public final int getUsedRam() {
        return getTotalRam() - getFreeRam();
    }
}
