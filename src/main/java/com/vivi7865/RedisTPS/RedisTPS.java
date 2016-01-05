package com.vivi7865.RedisTPS;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.logging.Level;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
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
	            rsc.del("RedisTPS.heartbeat." + serverID);
	            rsc.del("RedisTPS.tps." + serverID);
	            rsc.del("RedisTPS.players." + serverID);
	            rsc.del("RedisTPS.ram_total." + serverID);
	            rsc.del("RedisTPS.ram_free." + serverID);
	            rsc.del("RedisTPS.ram_max." + serverID);
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
			int runcount = -1;
            public void run() {
            	boolean doHeartbeat = false;
            	boolean doTPS = false;
            	boolean doPlayers = false;
            	boolean doMemory = false; 
				boolean doEntityCheck = false;
				boolean doChunkCheck = false;

            	if (runcount == -1) {
            		// First run
            		doHeartbeat = true;
            		doTPS = true;
            		doPlayers = true;
            		doMemory = true;
					doEntityCheck = true;
					doChunkCheck = true;
            		runcount = 1;
            	} else {
            		// Every time after that
	            	if (runcount == 86400) runcount = 0;  // loop every 24h
	            	runcount++;
	
	            	if (Config.intervalHeartbeat > 0 && (runcount % Config.intervalHeartbeat) == 0) doHeartbeat = true; 
	            	if (Config.intervalTPS > 0 && (runcount % Config.intervalTPS) == 0) doTPS = true; 
	            	if (Config.intervalPlayers > 0 && (runcount % Config.intervalPlayers) == 0) doPlayers = true; 
	            	if (Config.intervalMemory > 0 && (runcount % Config.intervalMemory) == 0) doMemory = true; 
					if (Config.intervalEntities > 0 && (runcount % Config.intervalEntities) == 0) doEntityCheck = true;
					if (Config.intervalChunks > 0 && (runcount % Config.intervalChunks) == 0) doChunkCheck = true;
            	}

            	// Abort this run if we don't have anything to do
				if (!doHeartbeat && !doTPS && !doPlayers && !doMemory && !doEntityCheck && !doChunkCheck) return;

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
	                    if (doHeartbeat) {
							pipeline.set("RedisTPS.heartbeat." + serverID, String.valueOf(time));
	                        pipeline.expire("RedisTPS.heartbeat." + serverID, (Config.intervalHeartbeat + 5));
	                    }

	                    if (doTPS) {
							double tps = Math.round(TPS.getTPS(100) * 100.0) / 100.0d;
							if (tps > 20.0) {
								// Cap TPS at 20
								tps = 20.0;
							}
							pipeline.set("RedisTPS.tps." + serverID, String.valueOf(tps));
	                        pipeline.expire("RedisTPS.tps." + serverID, (Config.intervalTPS + 5));
	                    }

	                    if (doPlayers) {
							pipeline.set("RedisTPS.players." + serverID, String.valueOf(Bukkit.getOnlinePlayers().length));
	                        pipeline.expire("RedisTPS.players." + serverID, (Config.intervalPlayers + 5));
	                    }
	
	                    if (doMemory) {
							pipeline.set("RedisTPS.ram_max." + serverID, String.valueOf(getMaxRam()));
							pipeline.set("RedisTPS.ram_total." + serverID, String.valueOf(getTotalRam()));
							pipeline.set("RedisTPS.ram_free." + serverID, String.valueOf(getFreeRam()));
	                        pipeline.expire("RedisTPS.ram_max." + serverID, (Config.intervalMemory + 5));
	                        pipeline.expire("RedisTPS.ram_total." + serverID, (Config.intervalMemory + 5));
	                        pipeline.expire("RedisTPS.ram_free." + serverID, (Config.intervalMemory + 5));
	                    }

						if (doEntityCheck) {
							for (World w : Config.entityWorlds) {
								pipeline.set("RedisTPS.entities." + w.getName(), String.valueOf(w.getEntities().size()));
		                        pipeline.expire("RedisTPS.entities." + w.getName(), (Config.intervalEntities + 5));
								pipeline.set("RedisTPS_livingentities." + w.getName(), String.valueOf(w.getLivingEntities().size()));
		                        pipeline.expire("RedisTPS.livingentities." + w.getName(), (Config.intervalEntities + 5));
							}
						}

						if (doChunkCheck) {
							for (World w : Config.chunkWorlds) {
								pipeline.set("RedisTPS.chunks." + w.getName(), String.valueOf(w.getLoadedChunks().length));
		                        pipeline.expire("RedisTPS.chunks." + w.getName(), (Config.intervalChunks + 5));
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
        }, 200L, 20L);
		
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
