package com.aehdev.stealthrealtor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.World;
import org.joda.time.Duration;

import com.aehdev.stealthrealtor.lib.multiDB.Database;
import com.sk89q.worldguard.protection.UnsupportedIntersectionException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

// TODO: Auto-generated Javadoc
/**
 * Search utility functions.
 */
public class Search
{
	/** The logging object with which we write to the server log. */
	protected static final Logger log = Logger.getLogger("Minecraft");
	
	/** Reference to database handler. */
	protected static final Database db = StealthRealtor.db;
	
	/**
	 * Concatenate elements of an array into a glue-separated string, similar to PHP's implode().
	 * 
	 * @param array the elements to join
	 * @param glue the separator
	 * @param if true, use the database escaping function on each item before putting it in the string
	 * @return the joined string
	 */
	public static String join(String[] array, String glue, boolean dbescape)
	{
		StringBuilder joined = new StringBuilder();
		if(array.length>0) joined.append(array[0]);
		for(int i=1; i<array.length; i++)
		{
			joined.append(glue);
			joined.append(dbescape ? db.escape(array[i]) : array[i]);
		}
		return joined.toString();
	}
	
	/**
	 * Concatenate, not doing escaping by default
	 * 
	 * @param array the elements to join
	 * @param glue the separator
	 * @return the joined string
	 */
	public static String join(String[] array, String glue)
	{
		return join(array, glue, false);
	}

	/**
	 * Concatenate elements of a list into a glue-separated string, similar to PHP's implode().
	 * 
	 * @param list the elements to join
	 * @param glue the separator
	 * @return the joined string
	 */
	public static String join(Collection<String> list, String glue)
	{
		StringBuilder joined = new StringBuilder();
		for(String element: list)
		{
			joined.append(element);
			joined.append(glue);
		}
		joined.delete(joined.lastIndexOf(glue), joined.length());
		return joined.toString();
	}
	
	/**
	 * Concatenate names of regions in a list into a glue-separated string, similar to PHP's implode(), but with chat formatting.
	 * 
	 * @param list the elements to join
	 * @param glue the separator
	 * @return the joined string
	 */
	public static String joinRegion(List<ProtectedRegion> list, String glue)
	{
		StringBuilder rgnStr = new StringBuilder();
		if(list.size() > 0)
		{
			for(ProtectedRegion f : list)
			{
				rgnStr.append(f.getId());
				rgnStr.append(glue);
			}
			rgnStr.delete(rgnStr.lastIndexOf(glue),rgnStr.length());
		}
		return rgnStr.toString();
	}
	
	/**
	 * Returns the amount of time remaining until a given datetime (format: yyyy-MM-dd HH:mm:ss)
	 * in a friendly human-readable format using units appropriate to the magnitude.
	 * 
	 * @param datetime datetime to measure to
	 * @return the duration string, or null on parse failure
	 */
	public static String timeRemaining(String datetime)
	{
		Date now = new Date();
		Date expiryDate;
		try{
			expiryDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(datetime);
		}catch(ParseException e){
			return null;
		}
		Duration dur = new Duration(now.getTime(), expiryDate.getTime());
		String durStr = "";
		if(dur.getStandardDays()>1)
		{
			durStr = dur.getStandardDays() + " days";
		}else if(dur.getStandardHours()>0){
			durStr = dur.getStandardHours() + " hours";
		}else{
			durStr = dur.getStandardMinutes() + " minutes";
		}
		return durStr;
	}
	
	/**
	 * Gets the region objects for all fiefs affecting a given region.
	 * 
	 * @param region the region to check
	 * @param world the world to look in
	 * @return the containing fiefs
	 */
	public static List<ProtectedRegion> getContainingFiefs(ProtectedRegion region, World world)
	{
		RegionManager wg = StealthRealtor.worldguard.get(world);
		LinkedList<ProtectedRegion> fiefregions = new LinkedList<ProtectedRegion>();
		for(String fief : Config.FIEFS)
		{
			ProtectedRegion fiefregion = wg.getRegion(fief);
			if(fiefregion != null) fiefregions.add(fiefregion);
		}
		List<ProtectedRegion> fiefs = new ArrayList<ProtectedRegion>();
		try
		{
			fiefs = region.getIntersectingRegions(fiefregions);
		}catch(UnsupportedIntersectionException e1){
			log.warning(String.format((Locale)null,"[%s] WorldGuard reported an invalid intersection when looking up applicable fiefs for region (%s). Command will continue assuming no fiefs apply.", StealthRealtor.pdfFile.getName(), region.getId()));
		}
		return fiefs;
	}
	
	/**
	 * Make a String serialization of a set of player:amount pairs
	 * 
	 * @param payees the object oriented payee list
	 * @param dbescape if true, call the database escaping function on each player name before putting it into the string
	 * @return the string serialization
	 */
	public static String serializePayeeList(Map<String, Double> payees, boolean dbescape)
	{
		StringBuilder out = new StringBuilder();
		for(String player : payees.keySet())
		{
			if(dbescape) player = db.escape(player);
			double amount = payees.get(player);
			out.append('[');
			out.append(player);
			out.append(',');
			out.append(amount);
			out.append(']');
		}
		return out.toString();
	}

	/**
	 * Serialize a payee list, not doing any escaping by default.
	 * 
	 * @param payees the payees
	 * @return the string
	 */
	public static String serializePayeeList(Map<String, Double> payees)
	{
		return serializePayeeList(payees, false);
	}
	
	/**
	 * Re-create the player:amount map from a String serialization
	 * 
	 * @param payees the string serialization
	 * @return a map containing player:amount pairs
	 */
	public static Map<String, Double> unserializePayeeList(String payees)
	{
		String[] pieces = payees.substring(payees.indexOf('['),payees.lastIndexOf(']')).split("\\]\\[");
		Map<String, Double> out = new HashMap<String, Double>();
		for(String pc : pieces)
		{
			String[] pair = pc.split(",");
			out.put(pair[0], Double.parseDouble(pair[1]));
		}
		return out;
	}
}
