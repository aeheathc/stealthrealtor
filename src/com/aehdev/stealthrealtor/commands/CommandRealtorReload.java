package com.aehdev.stealthrealtor.commands;

import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.aehdev.stealthrealtor.StealthRealtor;

/**
 * Command that queries the plugin version info.
 */
public class CommandRealtorReload extends Command
{
	/**
	 * Create a new reloading order.
	 * @param plugin
	 * reference to the main plugin object
	 * @param commandLabel
	 * command name/alias
	 * @param sender
	 * who sent the command
	 * @param args
	 * arguments to the subcommand
	 */
	public CommandRealtorReload(StealthRealtor plugin, CommandSender sender, String[] args)
	{
		super(plugin, sender, args);
	}

	/**
	 * Run version command; display the version info to the sender.
	 */
	public boolean process()
	{
		if((sender instanceof Player) && !((Player)sender).hasPermission("stealthrealtor.reload"))
		{
			sender.sendMessage("You do not have permission to reload StealthRealtor");
			return false;
		}
		log.info(String.format((Locale)null,"[%s] Starting reload", plugin.getDescription().getName()));

		//tell Bukkit config engine to reload from disk
		plugin.reloadConfig();
		plugin.onDisable();
		plugin.onEnable();
		sender.sendMessage("StealthRealtor reloaded");
		log.info(String.format((Locale)null,"[%s] Reload finished.", plugin.getDescription().getName()));
		return true;
	}
}
