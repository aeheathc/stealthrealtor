package com.aehdev.stealthrealtor;

import java.util.Arrays;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Stores global basic configuration for StealthRealtor.
 * Most of this is user-configurable via the config file.
 * Config file is managed in {@link StealthRealtor#loadProperties}
 */
public class Config
{
	/** Maximum number of real-life days for a rental. */
	public static int MAX_RENT_DAYS = 30;

	/** Maximum number of regions each player can rent at once; Unlimited = -1 */
	public static int MAX_RENT_REGIONS = -1;

	/** Percentage to be taken from each transaction and go to a ruler instead of the region owner. 0 = no tax; 100 = All proceeds go to the ruler */
	public static double TAX_PERCENT = 0;

	/** Person recieving tax money for transactions outside any fief */
	public static String KING = "NonExistingPlayer";
	
	/** Array of region names serving as "fiefs" where transactions within them will have their taxes go to the owner of the fief region (vassal) instead of the king. The region names here will be matched in any world. */
	public static String[] FIEFS = {};
	
	/** List based copy of the fief list for advanced operations. */
	public static List<String> FIEFLIST = null;
	
	/** Percentage of tax proceeds within fiefs that goes to the king. 0 = Vassal gets all taxes; 100 = King gets all taxes */
	public static double TRIBUTE_PERCENT = 0;
	
	/** When buying a region in a fief, require a permission node in the form "stealthrealtor.passport.fiefname.buy" or the global override. Used when you have multiple factions and don't want players just buying up land wherever. */
	public static boolean PASSPORT_BUY = false;

	/** When renting a region in a fief, require a permission node in the form "stealthrealtor.passport.fiefname.buy" or the global override. */
	public static boolean PASSPORT_RENT = false;
	
	/** Number of seconds between transaction notifications; 0 = Disable notifications */
	public static int NOTIFY_INTERVAL = 300;

	/** Maximum number of transactions saved in log for notification purposes. */
	public static int LOG_LIMIT = 500;
	
	/** Number of seconds between rental expiry checks. Not user configurable since it is related to the resolution of the time remaining given in the info command.*/
	public static int EXPIRY_INTERVAL = 60;
	
	/**
	 * Read the config file and load options when present, or write default
	 * options when not present.
	 * @param plugin
	 * main plugin class reference so we can call its inherited method {@link getConfig}
	 */
	public static void loadProperties(StealthRealtor plugin)
	{
		FileConfiguration config = plugin.getConfig();
		config.options().copyDefaults(true);

		MAX_RENT_DAYS =		config.getInt(			"limits.rent-days"			);
		MAX_RENT_REGIONS =	config.getInt(			"limits.rent-regions"		);
		TAX_PERCENT =		config.getDouble(		"feudal.tax"				);
		KING =				config.getString(		"feudal.king"				);
		FIEFS =				config.getStringList(	"feudal.fiefs"				).toArray(FIEFS);
		TRIBUTE_PERCENT =	config.getDouble(		"feudal.tribute"			);
		PASSPORT_BUY =		config.getBoolean(		"feudal.passport-buy"		);
		PASSPORT_RENT =		config.getBoolean(		"feudal.passport-rent"		);
		NOTIFY_INTERVAL =	config.getInt(			"log.notify-interval"		);
		LOG_LIMIT =			config.getInt(			"log.limit"					);
		
		if(MAX_RENT_DAYS < 1) MAX_RENT_DAYS = 30;
		if(TAX_PERCENT > 100 || TAX_PERCENT < 0) TAX_PERCENT = 0;
		if(TRIBUTE_PERCENT > 100 || TRIBUTE_PERCENT < 0) TRIBUTE_PERCENT = 0;

		plugin.saveConfig();
		
		FIEFLIST = Arrays.asList(FIEFS);
	}
}
