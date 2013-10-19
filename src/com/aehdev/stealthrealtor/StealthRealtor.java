package com.aehdev.stealthrealtor;

import java.sql.ResultSet;
import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.joda.time.DateTime;

import net.milkbowl.vault.economy.Economy;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.GlobalRegionManager;

import com.aehdev.stealthrealtor.commands.RealtorCommandExecutor;
import com.aehdev.stealthrealtor.lib.multiDB.Database;
import com.aehdev.stealthrealtor.lib.multiDB.SQLite;
import com.aehdev.stealthrealtor.threads.NotificationThread;
import com.aehdev.stealthrealtor.threads.RentalExpiryThread;

/**
 * The main Bukkit plugin class for StealthRealtor.
 */
public class StealthRealtor extends JavaPlugin
{
	/** General plugin info that comes directly from the private
	 * {@link JavaPlugin#description} in the parent. Effectively all we're
	 * doing here is converting it to public. */
	public static PluginDescriptionFile pdfFile = null;

	/** Thread that notifies managers of transactions. */
	protected NotificationThread notificationThread = null;

	/** Thread that removes region access when a rental expires. */
	protected RentalExpiryThread rentalExpiryThread = null;
	
	/** Abstracts supported economies. */
	public Economy econ = null;
	
	/** Abstracts supported databases. */
	public static Database db = null;

	/** Main logger with which we write to the server log. */
	private final Logger log = Logger.getLogger("Minecraft");

	/** Plugin-identifying string that prefixes every message we show to players */
	public static final String CHAT_PREFIX = ChatColor.DARK_AQUA + "["
			+ ChatColor.WHITE + "Realtor" + ChatColor.DARK_AQUA + "] ";

	/** Path to all our data files. */
	static String folderPath = "plugins/StealthRealtor/";

	/** Reference to WorldGuard region manager. */
	public static GlobalRegionManager worldguard = null;

	/**
	 * Setup method for when this plugin is enabled by Bukkit
	 */
	public void onEnable()
	{
		pdfFile = getDescription();	//cache plugin info
		Config.loadProperties(this);//Get the configuration via Bukkit's builtin method
		
		//check for library jodatime
		try{
			new DateTime();
		}catch(NoClassDefFoundError e){
			log.severe(String.format((Locale)null,"[%s] - Library jodatime not found! Most renting related functionality won't work.", pdfFile.getName()));
		}
		
        if(!setupEconomy())
        {
            log.severe(String.format((Locale)null,"[%s] - Shutting down: Vault economy hook not found!", pdfFile.getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        //Connect to database, do any necessary setup
       	db = new SQLite(log, "StealthRealtor","stealthrealtor",folderPath);
       	if(!setupDB()) return;
        
		//Connect to worldguard
		Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");
        if(plugin == null || !(plugin instanceof WorldGuardPlugin))
        {
            log.severe(String.format((Locale)null,"[%s] - Shutting down: Supported region plugin not found!", pdfFile.getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }else{
        	worldguard = ((WorldGuardPlugin)plugin).getGlobalRegionManager();
        	log.info(String.format((Locale)null,"[%s] %s", pdfFile.getName(), "WorldGuard support enabled."));
        }		
        
		// Register Commands
		getCommand("realtor").setExecutor(new RealtorCommandExecutor(this));

		// update the console that we've started
		try{
			ResultSet rentals = db.query("SELECT COUNT(*) FROM rentals"); rentals.next();
			long totalRentals = rentals.getLong(1);
			rentals.close();
			log.info(String.format((Locale)null,"[%s] %s", pdfFile.getName(), "Loaded with " + totalRentals + " active rental(s)"));
			log.info(String.format((Locale)null,"[%s] %s", pdfFile.getName(), "Version " + pdfFile.getVersion() + " is enabled: "));
		}catch(Exception e){
            log.severe(String.format((Locale)null,"[%s] - Shutting down: Can't select from DB.", pdfFile.getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
		}

		// Start Notification thread
		if(Config.NOTIFY_INTERVAL > 0)
		{
			notificationThread = new NotificationThread(this);
			notificationThread.start();
		}
		
		// Start Rental Expiry thread
		rentalExpiryThread = new RentalExpiryThread(this);
		rentalExpiryThread.start();
	}

	/**
	 * Shut down the plugin.
	 * Called by Bukkit when the server is shutting down, plugins are being reloaded,
	 * or we voluntarily shutdown due to errors. 
	 */
	public void onDisable()
	{
		// Stop notification thread
		if((Config.NOTIFY_INTERVAL > 0) && notificationThread != null && notificationThread.isAlive())
		{
			try
			{
				notificationThread.setRun(false);
				notificationThread.join(2000);
			}catch(InterruptedException e){
				log.warning(String.format((Locale)null,"[%s] %s", pdfFile.getName(), "NotificationThread did not exit"));
			}
		}
		
		// stop rental expiry thread
		if(rentalExpiryThread != null && rentalExpiryThread.isAlive())
		{
			try{
				rentalExpiryThread.setRun(false);
				rentalExpiryThread.join(2000);
			}catch(InterruptedException e){
				log.warning(String.format((Locale)null,"[%s] %s", pdfFile.getName(), "RentalExpiryThread did not exit"));
			}
		}

		//drop Worldguard hook
		StealthRealtor.worldguard = null;
		
		//drop Vault hook
		econ = null;
		
		//close database connection
		if(db != null) db.close();
		db = null;
		
		// update the console that we've stopped
		log.info(String.format((Locale)null,"[%s] %s", pdfFile.getName(), "Version " + pdfFile.getVersion() + " is disabled!"));
	}

	/**
	 * Attach to Vault's Economy support
	 * @return true on success
	 */
    private boolean setupEconomy()
    {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }
    
    /**
     * Ensure that the database contains the right schema.
     * @return true on success
     */
    private boolean setupDB()
    {
    	String tables,indexes;

		tables = "CREATE TABLE IF NOT EXISTS [log] ([id] INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT, [datetime] TEXT  NOT NULL, [user] TEXT  NOT NULL, [region] TEXT  NOT NULL, [world] TEXT  NOT NULL, [action] TEXT  NOT NULL, [price] REAL  NOT NULL, [days] INTEGER  NULL, [oldexpiry] TEXT  NULL, [currentvassal] TEXT  NULL, [currentking] TEXT  NOT NULL, [currenttax] REAL  NOT NULL, [currenttribute] REAL  NOT NULL, [proprietors] TEXT  NOT NULL); CREATE TABLE IF NOT EXISTS [players] ([name] TEXT  UNIQUE NOT NULL PRIMARY KEY, [lastNotify] TEXT NULL); CREATE TABLE IF NOT EXISTS [rentals] ([id] INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, [expiry] TEXT  NOT NULL, [player] TEXT  NOT NULL, [region] TEXT  NOT NULL, [world] TEXT  NOT NULL);";
		indexes = "CREATE INDEX [IDX_LOG_USER] ON [log]([user]  ASC); CREATE INDEX [IDX_LOG_CURRENTVASSAL] ON [log]([currentvassal]  ASC); CREATE INDEX [IDX_LOG_CURRENTKING] ON [log]([currentking]  ASC); CREATE UNIQUE INDEX [IDX_RENTALS_PLAYER] ON [rentals]([player] DESC, [world] DESC, [region] DESC); CREATE INDEX [IDX_RENTALS_REGION] ON [rentals]([region]  ASC, [world]  ASC);";

    	//Run CREATE TABLE statements only if the tables do not exist. Any errors here will be very bad.
    	try{
    		for(String query : tables.split(";")) db.query(query);
    	}catch(Exception e){
    		log.severe(String.format((Locale)null,"[%s] [MultiDB] - %s", pdfFile.getName(),e));
            log.severe(String.format((Locale)null,"[%s] - Shutting down: Problem checking schema.", pdfFile.getName()));
            getServer().getPluginManager().disablePlugin(this);
    		return false;
    	}
    	
    	/* Index creation queries are expected to cause errors if the indexes already exist because
    	 * querying for existence of individual ones is not worth the trouble 
    	 */
    	for(String query : indexes.split(";")) try{db.query(query,true);}catch(Exception e){}
    	
    	//trim log
    	try{
    		ResultSet reslog = db.query("SELECT COUNT(*) FROM `log`");
    		reslog.next();
    		long count = reslog.getLong(1);
    		reslog.close();
    		if(count>Config.LOG_LIMIT)
    		{
    			ResultSet resPivot = db.query("SELECT `datetime` FROM `log` ORDER BY `datetime` DESC LIMIT " + Config.LOG_LIMIT + ",1");
    			resPivot.next();
    			String pivot = resPivot.getString("datetime");
    			resPivot.close();
    			db.query("DELETE FROM `log` WHERE `datetime`<='" + pivot + "'");
    		}
    	}catch(Exception e){
    		log.warning(String.format((Locale)null,"[%s] - Couldn't trim log. Beware of ballooning log table. %s", pdfFile.getName(), e));
    	}
    	
    	return true;
    }
    
    public static void main(String args[])
    {
    	System.out.println("This is a bukkit plugin what are you doing");
    }
}
