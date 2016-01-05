package com.vivi7865.RedisTPS;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class Config {
	static File file;
	static FileConfiguration conf;
	RedisTPS plugin;
	static String ntpHost;
	static int heartbeatTimeout;
	static int intervalTPS, intervalPlayers, intervalMemory, intervalHeartbeat, intervalEntities, intervalChunks;
	static List<World> chunkWorlds = new ArrayList<World>();
	static List<World> entityWorlds = new ArrayList<World>();
	
	public Config(RedisTPS plug, File dataFolder) {
		this.plugin = plug;
		file = new File(dataFolder, "config.yml");
		conf = YamlConfiguration.loadConfiguration(file);
		
		final String redisServer = conf.getString("redis-server", "localhost");
		final int redisPort = conf.getInt("redis-port", 6379);
        String redisPassword = conf.getString("redis-password");
        String serverID = conf.getString("server-id");
        ntpHost = conf.getString("NTP-host");
		heartbeatTimeout = conf.getInt("heartbeat-timeout", 10);
		
		intervalTPS = conf.getInt("interval.tps", 3);
		intervalPlayers = conf.getInt("interval.players", 20);
		intervalMemory = conf.getInt("interval.memory", 60);
		intervalHeartbeat = conf.getInt("interval.heartbeat", 5);
		intervalEntities = conf.getInt("interval.entities", 30);
		intervalChunks = conf.getInt("interval.chunks", 30);

		List<String> strWorlds = (List<String>) conf.getStringList("chunk-worlds");
		if ((strWorlds != null) && (strWorlds.size() > 0)) {
			for (String w : strWorlds) {
				World world = plugin.getServer().getWorld(w);
				if (world == null) {
					plugin.getLogger().log(Level.WARNING, "Invalid world \"" + w + "\" in chunk world list!");
				} else {
					chunkWorlds.add(world);
				}
			}
		}

		strWorlds = (List<String>) conf.getStringList("entity-worlds");
		if ((strWorlds != null) && (strWorlds.size() > 0)) {
			for (String w : strWorlds) {
				World world = plugin.getServer().getWorld(w);
				if (world == null) {
					plugin.getLogger().log(Level.WARNING, "Invalid world \"" + w + "\" in entity world list!");
				} else {
					entityWorlds.add(world);
				}
			}
		}

        if (redisPassword != null && (redisPassword.isEmpty() || redisPassword.equals("none"))) {
            redisPassword = null;
        }
        
		if (Config.ntpHost.isEmpty()) {
			plug.getLogger().info("NTP client disabled.");
		} else {
			plug.getLogger().info("NTP client enabled.");
		}
		
        if (serverID == null || serverID.isEmpty()) {
            serverID = Bukkit.getServerName();
        }
        plugin.setServerID(serverID);

        if (redisServer != null && !redisServer.isEmpty()) {
            final String finalRedisPassword = redisPassword;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {

				public void run() {
	                    JedisPoolConfig config = new JedisPoolConfig();
	                    config.setMaxTotal(conf.getInt("max-redis-connections", -1));
	                    config.setJmxEnabled(false);
	                    JedisPool pool = new JedisPool(config, redisServer, redisPort, 0, finalRedisPassword);
	                    plugin.setPool(pool);
	                    pool.getResource().clientSetname("RedisTPS-" + Bukkit.getServerName());
				}
            });
            
            
        }
	}
	
	public static String getNTPHost() {
		return ntpHost;
	}

}
