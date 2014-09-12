package com.vivi7865.RedisTPS;

import java.io.File;

import org.bukkit.Bukkit;
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
	static int checkOthers;
	static int intervalTPS, intervalPlayers, intervalMemory, intervalHeartbeat;
	
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
        checkOthers = conf.getInt("interval.check-others", 0);

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
	                    plugin.setPool(new JedisPool(config, redisServer, redisPort, 0, finalRedisPassword));
				}
            });
            
            
        }
	}
	
	public static String getNTPHost() {
		return ntpHost;
	}

}
