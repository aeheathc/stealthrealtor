package com.aehdev.stealthrealtor.threads;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import com.aehdev.stealthrealtor.StealthRealtor;
import com.aehdev.stealthrealtor.Config;
import com.aehdev.stealthrealtor.Search;
import com.aehdev.stealthrealtor.lib.multiDB.Database;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * This thread periodically checks the database for expired rentals and makes the appropriate changes to the regions.
 */
public class RentalExpiryThread extends Thread
{
	/** Reference back to the main plugin object. */
	private StealthRealtor plugin;

	/** Current state. */
	private boolean run = true;

	/** Master logger. */
	protected final Logger log = Logger.getLogger("Minecraft");
	
	/** date formatter object this thread will use a lot */
	private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	/** Reference to main database connection. */
	protected final Database db = StealthRealtor.db;
	
	/**
	 * Creates the thread.
	 * @param plugin
	 * Reference back to the main plugin object
	 */
	public RentalExpiryThread(StealthRealtor plugin)
	{
		this.plugin = plugin;
	}

	/**
	 * Sets the current state and forces the thread to recognize it.
	 * @param run whether or not the new state should be running
	 */
	public void setRun(boolean run)
	{
		this.run = run;
		synchronized(this){notify();}
	}

	/**
	 * Starts the thread. 
	 * @see java.lang.Thread#run()
	 */
	public void run()
	{
		log.info(String.format((Locale)null,
				"[%s] Starting RentalExpiryThread with Timer of %d seconds",
				plugin.getDescription().getName(),
				Config.EXPIRY_INTERVAL));
		
		while(true)
		{
			long start = System.currentTimeMillis();
			String now = sdf.format(new Date());

			try{
				LinkedList<String> rentals = new LinkedList<String>();
				ResultSet res = db.query("SELECT id,player,region,world FROM rentals WHERE expiry<'" + now + "'");
				while(res.next())
				{
					String playerName = res.getString("player");
					String regionName = res.getString("region");
					String worldName = res.getString("world");
					long id = res.getLong("id");
					rentals.add("" + id);
					World world = Bukkit.getWorld(worldName);
					if(world == null)
					{
						log.warning(String.format((Locale)null,"[%s] Invalid world name %s for expired rental", StealthRealtor.pdfFile.getName(), worldName));
					}else{
						RegionManager wg = StealthRealtor.worldguard.get(world);
						ProtectedRegion region = wg.getRegion(regionName);
						if(region == null)
						{
							log.warning(String.format((Locale)null,"[%s] Invalid region name %s for expired rental in world %s", StealthRealtor.pdfFile.getName(), regionName, worldName));
						}else{
							region.setMembers(new DefaultDomain());
							boolean saved = true;
							try{
								wg.save();
							}catch(ProtectionDatabaseException e){
								saved = false;
							}
							if(saved)
								log.info(String.format((Locale)null,"[%s] Rental expired. Region:%s World:%s Renter:%s", StealthRealtor.pdfFile.getName(), regionName, worldName, playerName));
							else
								log.warning(String.format((Locale)null,"[%s] WorldGuard error resetting region members for rental expiry. Region:%s World:%s Renter:%s", StealthRealtor.pdfFile.getName(), regionName, worldName, playerName));
						}
					}
				}
				res.close();
				if(rentals.size() > 0) db.query("DELETE FROM rentals WHERE id IN(" + Search.join(rentals, ",") + ")");
			}catch(Exception e){
				log.warning(String.format((Locale)null,"[%s] Couldn't process rental expiration: %s", StealthRealtor.pdfFile.getName(), e));
				break;
			}
			
			// wait the configured amount of time before updates, but stop waiting if the thread is told to stop
			if(!run) break;
			while((System.currentTimeMillis()-start)/1000 < Config.EXPIRY_INTERVAL)
			{
				long millisToWait = (Config.EXPIRY_INTERVAL * 1000) - (System.currentTimeMillis()-start); 
				try{synchronized(this){wait(millisToWait);}}catch(InterruptedException e){}
				if(!run) break;
			}
		}
	}
}
