package com.aehdev.stealthrealtor.commands;

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
import com.aehdev.stealthrealtor.Config;
import com.aehdev.stealthrealtor.StealthRealtor;
import com.aehdev.stealthrealtor.Search;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * Command for buying a plot.
 */
public class CommandRealtorBuy extends Command
{

	/**
	 * Create a new Buy order.
	 * @param plugin
	 * reference to the main plugin object
	 * @param commandLabel
	 * command name/alias
	 * @param sender
	 * who sent the command
	 * @param args
	 * arguments to the subcommand
	 */
	public CommandRealtorBuy(StealthRealtor plugin, CommandSender sender, String[] args)
	{
		super(plugin, sender, args);
	}

	/**
	 * Execute buy order -- attempt to purchase the region
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
		if(args.length < 1 || args.length > 2 || args[0].trim().length()<1)
		{
			sender.sendMessage(CommandRealtorHelp.HELP_BUY);
			return false;
		}
		
		//command access permission check
		if(!player.hasPermission("stealthrealtor.user.buy"))
		{
			sender.sendMessage("You do not have permission to buy regions.");
			return false;
		}
		
		//Determine who will be the buyer. If someone else, make sure command sender has FORCE permission
		Player buyer = null;
		String buyerName = null;
		if(args.length == 2 && args[1].trim().length()>0)
		{
			if(!player.hasPermission("stealthrealtor.force"))
			{
				sender.sendMessage("You do not have permission to file transactions for other players.");
				return false;
			}else{
				buyerName = args[1].trim();
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
		
		//check region flags for validity
		Double priceObj = region.getFlag(DefaultFlag.PRICE);
		Boolean buyableObj = region.getFlag(DefaultFlag.BUYABLE);
		boolean buyable = buyableObj!=null && buyableObj;
		if(!buyable)
		{
			sender.sendMessage("Region " + regionName + " is not buyable.");
			return false;
		}
		if(priceObj == null)
		{
			sender.sendMessage("Region " + regionName + " does not have a price, so it can't be bought.");
			return false;
		}
		double price = priceObj;
		if(price <= 0)
		{
			sender.sendMessage("Region " + regionName + " has an invalid price, so it can't be bought or rented.");
			return false;
		}
		
		//determine sellers
		Set<String> sellers = region.getOwners().getPlayers();
		if(sellers.size()<1)
		{
			sender.sendMessage("Region has no owner so can't be sold.");
			return false;
		}
		
		//if passports are required, we need to make sure the BUYER has the right permissions for the highest priority fief this region is in (if any)
		List<ProtectedRegion> fiefs = Search.getContainingFiefs(region, world);
		if(Config.PASSPORT_BUY && !buyer.hasPermission("stealthrealtor.passport.buyall") && fiefs.size() > 0)
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
			if(!buyer.hasPermission(fiefperm+"buy") && !buyer.hasPermission(fiefperm+"*"))
			{
				sender.sendMessage("You don't have permission to buy land in " + highestRegion);
				return false;
			}
		}
		
		//check if the buyer can afford it
		double balance = plugin.econ.getBalance(buyerName);
		if(price > balance)
		{
			sender.sendMessage("Buyer can't afford this region");
			return false;
		}
		
		//calculate distribution
		double tax = (Config.TAX_PERCENT/100) * price;
		double sellersTotal = price - tax;
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
		if(plugin.econ.withdrawPlayer(buyerName, price).transactionSuccess())
		{
			payments.put(buyerName, -price);
		}else{
			player.sendMessage("Vault error taking payment: could not complete sale.");
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
					player.sendMessage("Vault error paying seller: could not complete sale.");
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
				player.sendMessage("Vault error paying tax: could not complete sale.");
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
					player.sendMessage("Vault error paying tax: could not complete sale.");
					rollback(payments, "paying tax to vassal " + vassal);
					return false;
				}
			}
		}
		DefaultDomain buyerdom = new DefaultDomain();
		buyerdom.addPlayer(buyerName);
		region.setOwners(buyerdom);
		region.setMembers(new DefaultDomain());
		region.setFlag(DefaultFlag.BUYABLE, null);
		region.setFlag(DefaultFlag.PRICE, null);
		try{
			wg.save();
		}catch(ProtectionDatabaseException e){
			player.sendMessage("WorldGuard error saving region data: could not complete sale.");
			rollback(payments, "telling worldguard to save", false);
			return false;
		}

		//Success. Log and notify.
		sender.sendMessage(regionName + " bought for " + plugin.econ.format(price));
		log.info(String.format((Locale)null,"[%s] %s bought region %s in world %s from %s for %f",
				StealthRealtor.pdfFile.getName(), buyerName, regionName, world.getName(), sellers.toString(), price));
		Date now = new Date();
		String datetime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now);
		String user = db.escape(buyerName);
		String regionNameDb = db.escape(regionName);
		String worldName = db.escape(world.getName());
		String currentvassal = vassalsEach.size()<1 ? "NULL" : ("'"+Search.serializePayeeList(vassalsEach, true)+"'");
		String currentking = db.escape(Config.KING);
		String proprietors = '[' + Search.join(sellers.toArray(new String[0]), "][", true) + ']';
		String query = String.format((Locale)null,
						"INSERT INTO log (	`datetime`,	`user`,	`region`,		`world`,	`action`,	`price`,	`days`,	`oldexpiry`,	`currentvassal`,	`currentking`,	`currenttax`,		`currenttribute`,		`proprietors`)"
						+ " VALUES (		'%s',		'%s',	'%s',			'%s',		'buy',		%f,			NULL,	NULL,			%s,					'%s',			%f,					%f,						'%s')",
											datetime,	user,	regionNameDb,	worldName,				price,								currentvassal,		currentking,	Config.TAX_PERCENT,	Config.TRIBUTE_PERCENT,	proprietors);
		try{
			db.query(query);
		}catch(Exception e){
			log.warning(String.format((Locale)null,"[%s] Couldn't log region purchase: %s", StealthRealtor.pdfFile.getName(), e));
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