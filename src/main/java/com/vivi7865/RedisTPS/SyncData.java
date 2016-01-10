package com.vivi7865.RedisTPS;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AbstractFuture;

public class SyncData {
	// Data
	public Map<World, List<Entity>> allEntities;
	public int playerCount;
	public Map<World, Integer> chunkCount;
	
	public SyncData() {
		allEntities = Maps.newIdentityHashMap();
		chunkCount = Maps.newIdentityHashMap();
	}
	
	public static class Loader extends AbstractFuture<SyncData> implements Runnable {
		// Settings
		public boolean doPlayerCheck;
		public boolean doEntityCheck;
		public boolean doChunkCheck;
		
		public Loader(boolean doPlayerCheck, boolean doEntityCheck, boolean doChunkCheck) {
			this.doPlayerCheck = doPlayerCheck;
			this.doEntityCheck = doEntityCheck;
			this.doChunkCheck = doChunkCheck;
		}
		
		public Loader() {
		}
		
		@Override
		public void run() {
			try {
				SyncData data = new SyncData();
				
				if (doPlayerCheck) {
					data.playerCount = Bukkit.getOnlinePlayers().length;
				}
				
				if (doEntityCheck) {
					for (World w : Config.entityWorlds) {
						data.allEntities.put(w, w.getEntities());
					}
				}
				
				if (doChunkCheck) {
					for (World w : Config.chunkWorlds) {
						data.chunkCount.put(w, w.getLoadedChunks().length);
					}
				}
				
				set(data);
			} catch (Throwable e) {
				setException(e);
			}
		}
	}
}
