package com.aehdev.stealthrealtor.commands;

import java.sql.ResultSet;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.aehdev.stealthrealtor.Config;
import com.aehdev.stealthrealtor.StealthRealtor;
import com.aehdev.stealthrealtor.Search;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * Command for renting a plot.
 */
public class CommandRealtorRent extends Command
{
	/**
	 * Create a new rental order.
	 * @param plugin
	 * reference to the main plugin object
	 * @param commandLabel
	 * command name/alias
	 * @param sender
	 * who sent the command
	 * @param args
	 * arguments to the subcommand
	 */
	public CommandRealtorRent(StealthRealtor plugin, CommandSender sender, String[] args)
	{
		super(plugin, sender, args);
	}

	/**
	 * Execute rent order -- attempt to rent the region
	 */
	@Override
	public boolean process()
	{
		//Ensure we're dealing with a player
		if(!(sender instanceof Player))
		{
			sender.sendMessage("You are not currently in a world");
		}
		Player player = (Player)sender;
		
		//command syntax check
		if(args.length < 2 || args.length > 3 || args[0].trim().length()<1 || args[1].trim().length()<1)
		{
			sender.sendMessage(CommandRealtorHelp.HELP_RENT);
			return false;
		}
		
		//command access permission check
		if(!player.hasPermission("stealthrealtor.user.rent"))
		{
			sender.sendMessage("You do not have permission to rent regions.");
			return false;
		}
		
		//Determine who will be the buyer. If someone else, make sure command sender has FORCE permission
		Player buyer = null;
		String buyerName = null;
		if(args.length == 3 && args[2].trim().length()>0)
		{
			if(!player.hasPermission("stealthrealtor.force"))
			{
				sender.sendMessage("You do not have permission to file transactions for other players.");
				return false;
			}else{
				buyerName = args[2].trim();
				buyer = Bukkit.getPlayerExact(buyerName);
				if(buyer == null)
				{
					sender.sendMessage("Player not found.");
					return false;
				}
			}
		}else{
			buyer = player;
			buyerName = player.getName();
		}
		
		//Check if requested region is real
		String regionName = args[0].trim();
		World world = player.getWorld();
		RegionManager wg = StealthRealtor.worldguard.get(world);
		ProtectedRegion region = wg.getRegion(regionName);
		if(region == null)
		{
			sender.sendMessage("No region by that name in the current world.");
			return false;
		}

		//make sure region isn't a fief
		if(Config.FIEFLIST.contains(regionName))
		{
			sender.sendMessage("Region (" + regionName + ") is a zone for determining permissions and taxation for other regions being bought or sold.");
			return false;
		}
		
		//make sure the region isn't already rented by someone else
		Set<String> oldRenters = region.getMembers().getPlayers();
		if(oldRenters.size() > 0 && !oldRenters.contains(buyerName))
		{
			sender.sendMessage("Region (" + regionName + ") is already being rented.");
			return false;
		}
		
		//check if they've already rented the region, making this an extension
		int oldrent_id = -1;
		String oldrent_expiry = null;
		Date oldrent_expiry_date = null;
		try{
			ResultSet res = db.query(String.format((Locale)null,"SELECT id,expiry FROM rentals WHERE region='%s' AND world='%s' AND player='%s'", regionName, world.getName(), buyerName));
			if(res.next())
			{
				oldrent_id = res.getInt("id");
				oldrent_expiry = res.getString("expiry");
			}
			res.close();
			if(oldrent_id >= 0)
				oldrent_expiry_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(oldrent_expiry);
		}catch(ParseException e){
			sender.sendMessage("Rental cancelled due to DB error.");
			log.warning(String.format((Locale)null,"[%s] Invalid date format stored in database for rental expiry: %s", StealthRealtor.pdfFile.getName(), e));
			return false;
		}catch(Exception e){
			sender.sendMessage("Rental cancelled due to DB error.");
			log.warning(String.format((Locale)null,"[%s] Couldn't get rental status: %s", StealthRealtor.pdfFile.getName(), e));
			return false;
		}

		//check rental length and limits
		int days = 0;
		try{
			days = Integer.parseInt(args[1]);
		}catch(NumberFormatException e){
			sender.sendMessage(CommandRealtorHelp.HELP_RENT);
			return false;
		}
		if(days<1)
		{
			sender.sendMessage("Rental must be for at least 1 day.");
			return false;
		}
		DateTime expiry = null;
		DateTime now = new DateTime();
		if(oldrent_id >= 0)
		{
			expiry = new DateTime(oldrent_expiry_date);
		}else{
			expiry = now;
		}
		expiry = expiry.plusDays(days);
		long totalDays = new Duration(now, expiry).getStandardDays();
		if(!buyer.hasPermission("stealthrealtor.limitless"))
		{
			if(totalDays > Config.MAX_RENT_DAYS)
			{
				sender.sendMessage("Can't exceed max rental length of " + Config.MAX_RENT_DAYS + " days.");
				return false;
			}
			if(Config.MAX_RENT_REGIONS > -1)
			{
				String query = String.format((Locale)null, "SELECT COUNT(*) AS amt FROM rentals WHERE player='%s'", db.escape(buyerName));
				try{
					ResultSet res = db.query(query);
					if(res.next())
					{
						int regCount = res.getInt("amt");
						if(regCount >= Config.MAX_RENT_REGIONS)
						{
							sender.sendMessage("Can't exceed maximum of " + Config.MAX_RENT_REGIONS + " rented regions ");
							return false;
						}
					}
					res.close();
				}catch(Exception e){
					sender.sendMessage("Rental cancelled due to DB error.");
					log.warning(String.format((Locale)null,"[%s] Couldn't get rented region count: %s", StealthRealtor.pdfFile.getName(), e));
					return false;
				}
			}
		}
		
		//check region flags for validity
		Double priceObj = region.getFlag(DefaultFlag.PRICE);
		Boolean buyableObj = region.getFlag(DefaultFlag.BUYABLE);
		boolean buyable = buyableObj!=null && buyableObj;
		if(buyable)
		{
			sender.sendMessage("Region " + regionName + " is not rentable.");
			return false;
		}
		if(priceObj == null)
		{
			sender.sendMessage("Region " + regionName + " does not have a price, so it can't be rented.");
			return false;
		}
		double price = priceObj;
		if(price <= 0)
		{
			sender.sendMessage("Region " + regionName + " has an invalid price, so it can't be rented.");
			return false;
		}
		double totalPrice = price * days;
		
		//determine sellers
		ProtectedRegion parent = region.getParent();
		Set<String> sellers = region.getOwners().getPlayers();
		if(sellers.size()<1 && parent != null)
			sellers = parent.getOwners().getPlayers();
		if(sellers.size()<1)
		{
			sender.sendMessage("Region has no owner or parent-owner so can't be rented.");
			return false;
		}
		
		//if passports are required, we need to make sure the BUYER has the right permissions for the highest priority fief this region is in (if any)
		List<ProtectedRegion> fiefs = Search.getContainingFiefs(region, world);
		if(Config.PASSPORT_RENT && !buyer.hasPermission("stealthrealtor.passport.rentall") && fiefs.size() > 0)
		{
			int maxPriority = fiefs.get(0).getPriority();
			String highestRegion = fiefs.get(0).getId();
			for(ProtectedRegion fief : fiefs)
			{
				if(fief.getPriority() > maxPriority)
				{
					maxPriority = fief.getPriority();
					highestRegion = fief.getId();
				}
			}
			String fiefperm = "stealthrealtor.passport." + highestRegion + ".";
			if(!buyer.hasPermission(fiefperm+"rent") && !buyer.hasPermission(fiefperm+"*"))
			{
				sender.sendMessage("You don't have permission to rent land in " + highestRegion);
				return false;
			}
		}
		
		//check if the buyer can afford it
		double balance = plugin.econ.getBalance(buyerName);
		if(totalPrice > balance)
		{
			sender.sendMessage("Renter can't afford this rental");
			return false;
		}
		
		//calculate distribution
		double tax = (Config.TAX_PERCENT/100) * totalPrice;
		double sellersTotal = totalPrice - tax;
		double sellersEach = sellersTotal / sellers.size();
		HashMap<String, Set<String>> vassals = new HashMap<String, Set<String>>(); //maps the names of all applicable fiefs having owners (vassals) to a list of vassal names 
		//go through all the applicable fiefs and store just the ones having vassals, and under each one store all its vassal names
		for(ProtectedRegion fief : fiefs)
		{
			Set<String> fiefVassals = fief.getOwners().getPlayers();
			if(fiefVassals.size() > 0)
			{
				vassals.put(fief.getId(), fiefVassals);
			}
		}
		double king = 0;
		double vassalsTotal = 0;
		double fiefsEach = 0;
		//if "fiefs having vassals" list is blank, either we're outside of any fiefs or all applicable fiefs have no vassals, so the king gets all the tax money regardless of tribute amount
		if(vassals.size() < 1)
		{
			king = tax;
			vassalsTotal = 0;
			fiefsEach = 0;
		}else{
			//if there are valid vassals then we calculate king's share based on tribute rate and split up the remainder per fief
			king = (Config.TRIBUTE_PERCENT/100) * tax;
			vassalsTotal = tax - king;
			fiefsEach = vassalsTotal / vassals.size();
		}
		//now take the per-fief tax money, and for each fief, split it among its vassals
		HashMap<String, Double> vassalsEach = new HashMap<String, Double>();
		for(String fief : vassals.keySet())
		{
			Set<String> fiefVassals = vassals.get(fief);
			int vassalCount = fiefVassals.size();
			double fiefVassalsEach = fiefsEach / vassalCount;
			for(String vassal : fiefVassals)
			{
				vassalsEach.put(vassal, fiefVassalsEach);
			}
		}
		
		//execute the transfers
		HashMap<String, Double> payments = new HashMap<String, Double>(); //this is just here to help us easily roll back if something goes wrong
		if(plugin.econ.withdrawPlayer(buyerName, totalPrice).transactionSuccess())
		{
			payments.put(buyerName, -totalPrice);
		}else{
			player.sendMessage("Vault error taking payment: could not complete transaction.");
			log.warning(String.format((Locale)null,"[%s] Vault error getting money from buyer %s. (Ending state OK)", StealthRealtor.pdfFile.getName(), buyer));
			return false;
		}

		if(sellersEach > 0)
		{
			for(String seller : sellers)
			{
				if(plugin.econ.depositPlayer(seller, sellersEach).transactionSuccess())
				{
					payments.put(seller, sellersEach);
				}else{
					player.sendMessage("Vault error paying seller: could not complete transaction.");
					rollback(payments, "paying seller " + seller);
					return false;
				}
			}
		}
		
		if(king > 0)
		{
			if(plugin.econ.depositPlayer(Config.KING, king).transactionSuccess())
			{
				payments.put(Config.KING, king);
			}else{
				player.sendMessage("Vault error paying tax: could not complete transaction.");
				rollback(payments, "paying tribute to king " + king);
				return false;
			}
		}
		
		if(vassalsTotal > 0)
		{
			for(String vassal : vassalsEach.keySet())
			{
				double vassalTax = vassalsEach.get(vassal);
				if(plugin.econ.depositPlayer(vassal, vassalTax).transactionSuccess())
				{
					payments.put(vassal, vassalTax);
				}else{
					player.sendMessage("Vault error paying tax: could not complete transaction.");
					rollback(payments, "paying tax to vassal " + vassal);
					return false;
				}
			}
		}
		
		//add rental to database
		String expiryString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(expiry.toDate());
		try{
			if(oldrent_id < 0)
			{
				db.query(String.format((Locale)null,
						"INSERT INTO rentals(	`expiry`,		`player`,				`region`,				`world`)"
								+ "VALUES(		'%s',			'%s',					'%s',					'%s')",
												expiryString,	db.escape(buyerName),	db.escape(regionName),	db.escape(world.getName())));
			}else{
				db.query(String.format((Locale)null, "UPDATE rentals SET `expiry`='%s' WHERE `id`=%d LIMIT 1", expiryString, oldrent_id));
			}
		}catch(Exception e){
			sender.sendMessage("Rental cancelled due to DB error.");
			log.warning(String.format((Locale)null,"[%s] Couldn't store rental in database: %s", StealthRealtor.pdfFile.getName(), e));
			rollback(payments, "storing rental info in database", false);
			return false;
		}
		
		DefaultDomain buyerdom = new DefaultDomain();
		buyerdom.addPlayer(buyerName);
		region.setMembers(buyerdom);
		try{
			wg.save();
		}catch(ProtectionDatabaseException e){
			player.sendMessage("WorldGuard error saving region data: could not complete transaction.");
			rollback(payments, "telling worldguard to save", false);
			return false;
		}

		//Success. Log and notify.
		sender.sendMessage(regionName + " rented for " + days + " days for a total of " + plugin.econ.format(totalPrice) + (oldrent_id >= 0 ? (" this extends total remaining rent time to " + totalDays + " days.") : ""));
		log.info(String.format((Locale)null,"[%s] %s rented region %s in world %s from %s for %d days for a total cost of %f %s",
				StealthRealtor.pdfFile.getName(), buyerName, regionName, world.getName(), sellers.toString(), days, totalPrice, (oldrent_id >= 0 ? (" this extends total remaining rent time to " + totalDays + " days.") : "")));
		String datetime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now.toDate());
		String user = db.escape(buyerName);
		String regionNameDb = db.escape(regionName);
		String worldName = db.escape(world.getName());
		String currentvassal = vassalsEach.size()<1 ? "NULL" : ("'"+Search.serializePayeeList(vassalsEach, true)+"'");
		String currentking = db.escape(Config.KING);
		String proprietors = '[' + Search.join(sellers.toArray(new String[0]), "][", true) + ']';
		String query = String.format((Locale)null,
						"INSERT INTO log (	`datetime`,	`user`,	`region`,		`world`,	`action`,						`price`,	`days`,	`oldexpiry`,									`currentvassal`,	`currentking`,	`currenttax`,		`currenttribute`,		`proprietors`)"
						+ " VALUES (		'%s',		'%s',	'%s',			'%s',		'%s',							%f,			%d,		%s,												%s,					'%s',			%f,					%f,						'%s')",
											datetime,	user,	regionNameDb,	worldName,	(oldrent_id>=0?"extend":"rent"),price,		days,	(oldrent_id>=0?("'"+oldrent_expiry+"'"):"NULL"),currentvassal,		currentking,	Config.TAX_PERCENT,	Config.TRIBUTE_PERCENT,	proprietors);
		try{
			db.query(query);
		}catch(Exception e){
			log.warning(String.format((Locale)null,"[%s] Couldn't log region rental: %s", StealthRealtor.pdfFile.getName(), e));
		}
		
		return true;
	}
	
	private void rollback(HashMap<String, Double> payments, String action, boolean vault)
	{
		HashMap<String, Double> failures = new HashMap<String, Double>();
		for(String player : payments.keySet())
		{
			double amount = payments.get(player);
			EconomyResponse res = null;
			if(amount > 0)
			{
				res = plugin.econ.withdrawPlayer(player, amount);
			}else if(amount < 0){
				res = plugin.econ.depositPlayer(player, -amount);
			}
			if(res != null && !res.transactionSuccess()) failures.put(player, amount);
		}
		String fault = vault ? "vault" : "transaction";
		if(failures.size() > 0)
		{
			String fail = Search.serializePayeeList(failures);
			log.warning(String.format((Locale)null,"[%s] %s error %s. BAD ENDING STATE! Failed to rollback the following payments (positive = player has extra money, negative = player is missing money): %s", StealthRealtor.pdfFile.getName(), fault, action, fail));
		}else{
			log.warning(String.format((Locale)null,"[%s] %s error %s. Anything done was rolled back successfully. (Ending state OK)", StealthRealtor.pdfFile.getName(), fault, action));
		}
	}
	
	private void rollback(HashMap<String, Double> payments, String action)
	{
		rollback(payments, action, true);
	}
}