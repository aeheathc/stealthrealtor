package com.aehdev.stealthrealtor.commands;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.aehdev.stealthrealtor.Config;
import com.aehdev.stealthrealtor.Search;
import com.aehdev.stealthrealtor.StealthRealtor;
import com.sk89q.worldguard.protection.UnsupportedIntersectionException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * Command that can display lists of regions meeting various criteria.
 */
public class CommandRealtorList extends Command
{
	/**
	 * Create a new list order.
	 * @param plugin
	 * reference to the main plugin object
	 * @param commandLabel
	 * command name/alias
	 * @param sender
	 * who sent the command
	 * @param args
	 * arguments to the subcommand
	 */
	public CommandRealtorList(StealthRealtor plugin, CommandSender sender, String[] args)
	{
		super(plugin, sender, args);
	}

	/**
	 * Run the command, show the list of regions they asked for.
	 */
	public boolean process()
	{
		if(!(sender instanceof Player))
		{
			sender.sendMessage("You are not currently in a world");
		}
		Player player = (Player)sender;
		World world = player.getWorld();
		RegionManager wg = StealthRealtor.worldguard.get(world);
		if(!player.hasPermission("stealthrealtor.user.info"))
		{
			sender.sendMessage("You do not have permission to query StealthRealtor.");
			return false;
		}
		LinkedList<String> msg = new LinkedList<String>();
		Collection<ProtectedRegion> regions = null;
		if(args.length == 0) args = new String[]{""};
		switch(args[0])
		{
			case "available":
			if(args.length == 1) args = new String[]{"",""};
			regions = wg.getRegions().values();
			switch(args[1])
			{
				case "buy":	listBuyable(regions, msg);	break;
				case "rent":listRentable(regions, msg);	break;
				default:	listAvail(regions, msg);
			}
			break;
			
			case "within":
			if(args.length == 1)
			{
				sender.sendMessage(CommandRealtorHelp.HELP_LIST_WITHIN);
				return false;
			}
			ProtectedRegion container = wg.getRegion(args[1]);
			if(container == null)
			{
				sender.sendMessage("Invalid region name.");
				return false;
			}
			try{
				regions = container.getIntersectingRegions(new LinkedList<ProtectedRegion>(wg.getRegions().values()));
			}catch(UnsupportedIntersectionException e){
				log.warning(String.format((Locale)null,"[%s] WorldGuard reported an invalid intersection when looking up regions within (%s). This list-within command will fail.", StealthRealtor.pdfFile.getName(), container.getId()));
				sender.sendMessage("WorldGuard error looking up regions within that one.");
				return false;
			}
			if(args.length == 2) args = new String[]{"","",""};
			switch(args[2])
			{
				case "buy":	listBuyable(regions, msg);	break;
				case "rent":listRentable(regions, msg);	break;
				default:	listAvail(regions, msg);
			}
			break;
			
			case "rented":
			String renter = player.getName();
			if(args.length > 1)
			{
				if(!player.hasPermission("stealthrealtor.spy"))
				{
					sender.sendMessage("You do not have permission to look up other peoples rentals");
					return false;
				}
				renter = args[1];
			}
			String query = String.format((Locale)null, "SELECT `expiry`,`region` FROM rentals WHERE player='%s' AND world='%s'", db.escape(renter), db.escape(world.getName()));
			try{
				ResultSet res = db.query(query);
				msg.add(ChatColor.DARK_AQUA+"Regions you are renting in the current world, and time remaining:");
				while(res.next())
				{
					String expiry = res.getString("expiry");
					String region = res.getString("region");
					expiry = Search.timeRemaining(expiry);
					if(expiry == null)
					{
						expiry = "error";
						log.warning(String.format((Locale)null,"[%s] Could not parse stored expiry date for rental on region (%s) in world (%s) by player %s", StealthRealtor.pdfFile.getName(), region, world.getName(), renter));
					}
					msg.add(String.format((Locale)null, "%s %s-%s %s", region, ChatColor.DARK_AQUA, ChatColor.WHITE, expiry));
				}
				res.close();
			}catch(Exception e){
				sender.sendMessage("Rental list cancelled due to DB error.");
				log.warning(String.format((Locale)null,"[%s] Couldn't get rental info: %s", StealthRealtor.pdfFile.getName(), e));
				return false;
			}
			
			break;
			
			default:
			sender.sendMessage("LIST must be followed by another keyword:");
			sender.sendMessage(CommandRealtorHelp.HELP_LIST_AVAIL);
			sender.sendMessage(CommandRealtorHelp.HELP_LIST_WITHIN);
			sender.sendMessage(CommandRealtorHelp.HELP_LIST_RENTED);
			return false;
		}
		
		sender.sendMessage(msg.toArray(new String[]{}));
		return true;
	}
	
	public void listAvail(Collection<ProtectedRegion> regions, LinkedList<String> msg)
	{
		for(ProtectedRegion region : regions)
		{
			String regionName = region.getId();
			if(Config.FIEFLIST.contains(regionName)) continue;
			
			Double priceObj = region.getFlag(DefaultFlag.PRICE);
			if(priceObj == null) continue;
			
			Boolean buyableObj = region.getFlag(DefaultFlag.BUYABLE);
			boolean buyable = buyableObj!=null && buyableObj;
			Set<String> owners = region.getOwners().getPlayers();
			if(buyable && owners.isEmpty()) continue;
			
			Set<String> members = region.getMembers().getPlayers();
			ProtectedRegion parent = region.getParent();
			Set<String> parentOwners = new TreeSet<String>();
			if(parent != null) parentOwners = parent.getOwners().getPlayers();
			if(!buyable && owners.isEmpty() && parentOwners.isEmpty()) continue;
			if(!buyable && !members.isEmpty()) continue;
			
			double price = priceObj;
			msg.add(ChatColor.DARK_AQUA+"Available regions:");
			msg.add(String.format((Locale)null, "%s %s-%s %s%s%s", regionName, ChatColor.DARK_AQUA, ChatColor.WHITE, plugin.econ.format(price), ChatColor.DARK_AQUA, (buyable?"":"/day")));
		}
		if(msg.size() < 1) msg.add("No available regions found.");
	}
	
	public void listBuyable(Collection<ProtectedRegion> regions, LinkedList<String> msg)
	{
		for(ProtectedRegion region : regions)
		{
			String regionName = region.getId();
			if(Config.FIEFLIST.contains(regionName)) continue;
			
			Double priceObj = region.getFlag(DefaultFlag.PRICE);
			if(priceObj == null) continue;
			
			Boolean buyableObj = region.getFlag(DefaultFlag.BUYABLE);
			boolean buyable = buyableObj!=null && buyableObj;
			if(!buyable) continue;
			
			Set<String> owners = region.getOwners().getPlayers();
			if(owners.isEmpty()) continue;
				
			double price = priceObj;
			msg.add(ChatColor.DARK_AQUA+"Buyable regions:");
			msg.add(String.format((Locale)null, "%s %s-%s %s", regionName, ChatColor.DARK_AQUA, ChatColor.WHITE, plugin.econ.format(price)));
		}
		if(msg.size() < 1) msg.add("No buyable regions found.");
	}
	
	public void listRentable(Collection<ProtectedRegion> regions, LinkedList<String> msg)
	{
		for(ProtectedRegion region : regions)
		{
			String regionName = region.getId();
			if(Config.FIEFLIST.contains(regionName)) continue;
			
			Double priceObj = region.getFlag(DefaultFlag.PRICE);
			if(priceObj == null) continue;
			
			Boolean buyableObj = region.getFlag(DefaultFlag.BUYABLE);
			boolean buyable = buyableObj!=null && buyableObj;
			if(buyable) continue;
			
			Set<String> owners = region.getOwners().getPlayers();
			Set<String> members = region.getMembers().getPlayers();
			ProtectedRegion parent = region.getParent();
			Set<String> parentOwners = new TreeSet<String>();
			if(parent != null) parentOwners = parent.getOwners().getPlayers();
			if(owners.isEmpty() && parentOwners.isEmpty()) continue;
			if(!members.isEmpty()) continue;
			
			double price = priceObj;
			msg.add(ChatColor.DARK_AQUA+"Rentable regions:");
			msg.add(String.format((Locale)null, "%s %s-%s %s%s%s", regionName, ChatColor.DARK_AQUA, ChatColor.WHITE, plugin.econ.format(price), ChatColor.DARK_AQUA, "/day"));
		}
		if(msg.size() < 1) msg.add("No rentable regions found.");
	}
}
