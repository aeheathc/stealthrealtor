package com.aehdev.stealthrealtor.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.aehdev.stealthrealtor.StealthRealtor;

/**
 * Command that shows syntax for all commands.
 */
public class CommandRealtorHelp extends Command
{
	/**
	 * Create a new help request.
	 * @param plugin
	 * reference to the main plugin object
	 * @param commandLabel
	 * command name/alias
	 * @param sender
	 * who sent the command
	 * @param args
	 * arguments to the subcommand
	 */
	public CommandRealtorHelp(StealthRealtor plugin, CommandSender sender, String[] args)
	{
		super(plugin, sender, args);
	}

	/**
	 * Run the request and display the help.
	 */
	public boolean process()
	{
		sender.sendMessage(StealthRealtor.CHAT_PREFIX + ChatColor.DARK_AQUA + "Here are the available commands [required] <optional>");
		
		Player player = null;
		if(sender instanceof Player) player = (Player)sender;

		sender.sendMessage(HELP_HELP);
		if(player != null && player.hasPermission("stealthrealtor.user.buy"))	sender.sendMessage(HELP_BUY);
		if(player != null && player.hasPermission("stealthrealtor.user.rent"))	sender.sendMessage(HELP_RENT);
		if(player == null || player.hasPermission("stealthrealtor.user.info"))
		{
			sender.sendMessage(HELP_INFO);
			sender.sendMessage(HELP_LIST_AVAIL);
			sender.sendMessage(HELP_LIST_WITHIN);
			if(player != null)	sender.sendMessage(HELP_LIST_RENTED);
		}
		if(player == null || player.hasPermission("stealthrealtor.reload"))		sender.sendMessage(HELP_RELOAD);

		return true;
	}
	
	public static String HELP_HELP			= ChatColor.WHITE + "   /realtor " + ChatColor.DARK_AQUA + "- Display this help.";
	public static String HELP_BUY			= ChatColor.WHITE + "   /realtor buy [region] " + ChatColor.DARK_AQUA + "- Buy a region.";
	public static String HELP_RENT			= ChatColor.WHITE + "   /realtor rent [region] [days] " + ChatColor.DARK_AQUA + "- Rent a region for x real-life days.";
	public static String HELP_INFO			= ChatColor.WHITE + "   /realtor info [region] " + ChatColor.DARK_AQUA + "- See info on a region.";
	public static String HELP_LIST_AVAIL	= ChatColor.WHITE + "   /realtor list available <buy|rent> " + ChatColor.DARK_AQUA + "- List all regions available to buy or rent.";
	public static String HELP_LIST_WITHIN	= ChatColor.WHITE + "   /realtor list within [region] <buy|rent> " + ChatColor.DARK_AQUA + "- List all regions available to buy or rent in a given larger region.";
	public static String HELP_LIST_RENTED	= ChatColor.WHITE + "   /realtor list rented " + ChatColor.DARK_AQUA + "- List regions you are currently renting.";
	public static String HELP_RELOAD		= ChatColor.WHITE + "   /realtor reload " + ChatColor.DARK_AQUA + "- Reload the plugin.";
}
