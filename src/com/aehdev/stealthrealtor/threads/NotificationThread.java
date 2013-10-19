package com.aehdev.stealthrealtor.threads;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.aehdev.stealthrealtor.StealthRealtor;
import com.aehdev.stealthrealtor.Config;
import com.aehdev.stealthrealtor.Search;
import com.aehdev.stealthrealtor.lib.multiDB.Database;

/**
 * This thread periodically shows a transaction digest to players who need to know.
 */
public class NotificationThread extends Thread
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
	public NotificationThread(StealthRealtor plugin)
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
				"[%s] Starting NotificationThread with Timer of %d seconds",
				plugin.getDescription().getName(),
				Config.NOTIFY_INTERVAL));
		final String[] example = new String[1];
		while(true)
		{
			long start = System.currentTimeMillis();
			Player[] online = Bukkit.getOnlinePlayers();
			for(Player player : online)
			{
				String playerName = player.getName();
				String name = db.escape(playerName);
				String now = sdf.format(new Date());
				
				//get last notification time
				String lastNotify = null;
				try{
					ResultSet hasPlayer = db.query("SELECT name,lastNotify FROM players WHERE name='" + name + "'");
					if(!hasPlayer.next())
					{
						db.query("INSERT INTO players(name,lastNotify) VALUES('" + name + "',NULL)");
					}else{
						lastNotify = hasPlayer.getString("lastNotify");
					}
					hasPlayer.close();
				}catch(Exception e){
					log.warning(String.format((Locale)null,"[%s] Couldn't verify lastNotify time: %s", StealthRealtor.pdfFile.getName(), e));
					break;
				}
				String timeLimit = "";
				if(lastNotify != null) timeLimit = " AND datetime>'" + lastNotify + "'";
				
				LinkedList<String> msg = new LinkedList<String>();
				
				//Add a message for each region they sold
				try{
					String buyQuery = "SELECT region,world,price,proprietors,currenttax FROM log WHERE proprietors LIKE '%[" + name + "]%' AND action='buy'" + timeLimit;
					ResultSet res = db.query(buyQuery);
					while(res.next())
					{
						StringBuilder output = new StringBuilder(60);
						String proprietors = res.getString("proprietors");
						int sellers = 0;
						for(int i=0; i<proprietors.length(); ++i) if(proprietors.charAt(i)=='[') ++sellers;
						double taxrate = res.getDouble("currenttax") / 100;
						double price = res.getDouble("price");
						double tax = price * taxrate;
						double sellersTotal = price - tax;
						double sellersEach = sellersTotal / sellers;
						output.append(ChatColor.DARK_AQUA);
						output.append("Got ");
						output.append(ChatColor.WHITE);
						output.append(plugin.econ.format(sellersEach));
						output.append(ChatColor.DARK_AQUA);
						output.append(" selling region ");
						output.append(ChatColor.WHITE);
						output.append(res.getString("region"));
						output.append(ChatColor.DARK_AQUA);
						output.append(" in world ");
						output.append(ChatColor.WHITE);
						output.append(res.getString("world"));
						output.append(ChatColor.DARK_AQUA);
						output.append(" for ");
						output.append(ChatColor.WHITE);
						output.append(plugin.econ.format(price));
						msg.add(output.toString());
					}
					res.close();
				}catch(Exception e){
					log.warning(String.format((Locale)null,"[%s] Couldn't get log for region sales: %s", StealthRealtor.pdfFile.getName(), e));
					break;
				}
				
				//Add a message totaling rental profits
				try{
					String buyQuery = "SELECT price,days,proprietors,currenttax FROM log WHERE proprietors LIKE '%[" + name + "]%' AND (action='rent' OR action='extend')" + timeLimit;
					ResultSet res = db.query(buyQuery);
					double total = 0;
					while(res.next())
					{
						double price = res.getDouble("price");
						double taxrate = res.getDouble("currenttax") / 100;
						double days = res.getDouble("days");
						double totalPrice = price * days;
						double tax = totalPrice * taxrate;
						double sellersTotal = totalPrice - tax;
						String proprietors = res.getString("proprietors");
						int sellers = 0;
						for(int i=0; i<proprietors.length(); ++i) if(proprietors.charAt(i)=='[') ++sellers;
						double sellersEach = sellersTotal / sellers;
						
						total += sellersEach;
					}
					res.close();
					if(total > 0)
					{
						msg.add(String.format((Locale)null, "%sRental revenue: %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, plugin.econ.format(total)));
					}
				}catch(Exception e){
					log.warning(String.format((Locale)null,"[%s] Couldn't get log for rental revenue: %s", StealthRealtor.pdfFile.getName(), e));
					break;
				}
				
				//Add a message totaling tax revenue (including tributes)
				try{
					String buyQuery = "SELECT action,price,days,currenttax,currentvassal,currentking,currenttribute FROM log WHERE (currentvassal LIKE '%[" + name + ",%' OR currentking='" + name + "')" + timeLimit;
					ResultSet res = db.query(buyQuery);
					double total = 0;
					while(res.next())
					{
						boolean buy = res.getString("action") == "buy";
						double price = res.getDouble("price");
						int days = res.getInt("days");
						double taxrate = res.getDouble("currenttax") / 100;
						if(!buy) price = price * days;
						double tax = price * taxrate;
						double tributerate = res.getDouble("currenttribute") / 100;
						double tribute = tax * tributerate;
						String king = res.getString("currentking");
						Map<String, Double> vassals = Search.unserializePayeeList(res.getString("currentvassal"));
						if(vassals.size() < 1 && king == playerName)
						{
							total += tax;
						}else{
							if(king == playerName) total += tribute;
							Double amt = vassals.get(playerName);
							if(amt != null) total += amt.doubleValue();
						}
					}
					res.close();
					if(total > 0)
					{
						msg.add(String.format((Locale)null, "%sTax revenue: %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, plugin.econ.format(total)));
					}
				}catch(Exception e){
					log.warning(String.format((Locale)null,"[%s] Couldn't get log for tax income: %s", StealthRealtor.pdfFile.getName(), e));
					break;
				}

				//update last notification time
				try{
					String recordSendingQuery = "UPDATE players SET lastNotify='" + now + "' WHERE name='" + name + "'";
					db.query(recordSendingQuery);
				}catch(Exception e){
					log.warning(String.format((Locale)null,"[%s] Couldn't record time of sending notification (notifications not sent, to prevent buildup): %s",
							StealthRealtor.pdfFile.getName(), e));
					break;
				}
				
				//send the updates
				if(msg.size() > 0)
				{
					if(lastNotify != null) msg.add(String.format((Locale)null, "%sSince %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, lastNotify));
					player.sendMessage(msg.toArray(example));
				}
			}
			
			// wait the configured amount of time before updates, but stop waiting if the thread is told to stop
			if(!run) break;
			while((System.currentTimeMillis()-start)/1000 < Config.NOTIFY_INTERVAL)
			{
				long millisToWait = (Config.NOTIFY_INTERVAL * 1000) - (System.currentTimeMillis()-start); 
				try{synchronized(this){wait(millisToWait);}}catch(InterruptedException e){}
				if(!run) break;
			}
		}
	}
}
