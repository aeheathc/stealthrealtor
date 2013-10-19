package com.aehdev.stealthrealtor.commands;

import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.List;
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
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * List everything StealthRealtor knows about this region including time left on current rental and its interpretation of the flags.
 */
public class CommandRealtorInfo extends Command
{
	/**
	 * Create a new info order.
	 * @param plugin
	 * reference to the main plugin object
	 * @param commandLabel
	 * command name/alias
	 * @param sender
	 * who sent the command
	 * @param args
	 * arguments to the subcommand
	 */
	public CommandRealtorInfo(StealthRealtor plugin, CommandSender sender, String[] args)
	{
		super(plugin, sender, args);
	}

	/**
	 * Execute info order -- show the region's information.
	 */
	public boolean process()
	{
		if(!(sender instanceof Player))
		{
			sender.sendMessage("You are not currently in a world");
		}
		Player player = (Player)sender;
		
		if(!player.hasPermission("stealthrealtor.user.info"))
		{
			sender.sendMessage("You do not have permission to query StealthRealtor.");
			return false;
		}
		
		if(args.length != 1 || args[0].trim().length()<1)
		{
			sender.sendMessage(CommandRealtorHelp.HELP_INFO);
			return false;
		}
		World world = player.getWorld();
		RegionManager wg = StealthRealtor.worldguard.get(world);
		
		ProtectedRegion region = wg.getRegion(args[0]);
		
		if(region == null)
		{
			sender.sendMessage("No region by that name in the current world.");
			return false;
		}

		Double priceObj = region.getFlag(DefaultFlag.PRICE);
		Boolean buyableObj = region.getFlag(DefaultFlag.BUYABLE);
		boolean buyable = buyableObj!=null && buyableObj;
		String name = region.getId();
		
		if(Config.FIEFLIST.contains(name))
		{
			sender.sendMessage("Region (" + name + ") is a zone for determining permissions and taxation for other regions being bought or sold.");
			return true;
		}
		
		
		Set<String> owners = region.getOwners().getPlayers();
		Set<String> members = region.getMembers().getPlayers();
		ProtectedRegion parent = region.getParent();
		Set<String> parentOwners = new TreeSet<String>();
		if(parent != null) parentOwners = parent.getOwners().getPlayers();
		
		if(priceObj == null)
		{
			sender.sendMessage("Region " + name + " does not have the price flag set, so it can't be bought or rented.");
			return true;
		}
		double price = priceObj;
		if(price <= 0)
		{
			sender.sendMessage("Region " + name + " has an invalid price, so it can't be bought or rented.");
			return true;
		}
		List<ProtectedRegion> fiefs = Search.getContainingFiefs(region, world);
		String fiefsStr = "";
		if(fiefs.size() < 1)
		{
			fiefsStr = ChatColor.DARK_AQUA + "(none)";
		}else{
			fiefsStr = Search.joinRegion(fiefs,ChatColor.DARK_AQUA + "," + ChatColor.WHITE);
		}
		
		LinkedList<String> msg = new LinkedList<String>();
		msg.add(String.format((Locale)null, "%sRegion: %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, name));
		msg.add(String.format((Locale)null, "%sTerritory: %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, fiefsStr));
		
		if(buyable)
		{
			msg.add(String.format((Locale)null, "%sBuy Price: %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, plugin.econ.format(price)));
			if(owners.size() < 1)
			{
				msg.add(String.format((Locale)null, "%sRegion set buyable, but has no owner, so can't be bought", ChatColor.DARK_PURPLE));
			}else{
				msg.add(String.format((Locale)null, "%sSeller: %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, Search.join(owners, ChatColor.DARK_AQUA + "," + ChatColor.WHITE)));
				msg.add(String.format((Locale)null, "%sAvailable to buy", ChatColor.DARK_PURPLE));
			}
		}else{
			msg.add(String.format((Locale)null, "%sRent Price (per day): %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, plugin.econ.format(price)));
			if(owners.size() + parentOwners.size() < 1)
			{
				msg.add(String.format((Locale)null, "%sRegion set rentable, but has no owner, so can't be rented", ChatColor.DARK_PURPLE));
			}else{
				msg.add(String.format((Locale)null, "%sLandlord: %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, Search.join(owners.size()>0 ? owners : parentOwners, ChatColor.DARK_AQUA + "," + ChatColor.WHITE)));
				if(members.size() < 1)
				{
					msg.add(String.format((Locale)null, "%sAvailable to rent", ChatColor.DARK_PURPLE));
				}else{
					String query = String.format((Locale)null, "SELECT `expiry`,`player` FROM rentals WHERE region='%s' AND world='%s'", db.escape(name), db.escape(world.getName()));
					try{
						ResultSet res = db.query(query);
						if(!res.next())
						{
							msg.add(String.format((Locale)null, "%sAvailable to rent", ChatColor.DARK_PURPLE));
							region.setMembers(new DefaultDomain());
							log.warning(String.format((Locale)null,"[%s] Members (%s) found on rentable region (%s) but no valid rental was active. Removed the members.", StealthRealtor.pdfFile.getName(), Search.join(members, ", "), name));
						}else{
							String expiry = res.getString("expiry");
							String renter = res.getString("player");
							if(!members.contains(renter))
							{
								DefaultDomain domrnt = new DefaultDomain();
								domrnt.addPlayer(renter);
								region.setMembers(domrnt);
								log.warning(String.format((Locale)null,"[%s] Member list (%s) found on rentable region (%s) did not contain active renter (%s). Replaced member list with active renter.", StealthRealtor.pdfFile.getName(), Search.join(members, ", "), name, renter));
							}
							String durStr = Search.timeRemaining(expiry);
							if(durStr == null)
							{
								durStr = "error";
								log.warning(String.format((Locale)null,"[%s] Could not parse stored expiry date for rental on region (%s) in world (%s) by player %s", StealthRealtor.pdfFile.getName(), name, world.getName(), renter));
							}
							msg.add(String.format((Locale)null, "%sCurrently rented by: %s%s%s - expires in %s%s", ChatColor.DARK_AQUA, ChatColor.WHITE, renter, ChatColor.DARK_AQUA, ChatColor.WHITE, durStr));
						}
						wg.save();
						res.close();
					}catch(ProtectionDatabaseException e){
						log.warning(String.format((Locale)null,"[%s] Error telling worldguard to save. Any rental member related warnings immediately above won't have their fixes saved automatically. %s", StealthRealtor.pdfFile.getName(), e));
					}catch(Exception e){
						sender.sendMessage("Info req cancelled due to DB error.");
						log.warning(String.format((Locale)null,"[%s] Couldn't get region info: %s", StealthRealtor.pdfFile.getName(), e));
						return false;
					}
				}
			}
		}

		sender.sendMessage(msg.toArray(new String[]{}));
		return true;
	}
}
