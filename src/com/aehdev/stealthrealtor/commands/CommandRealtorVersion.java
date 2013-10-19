package com.aehdev.stealthrealtor.commands;

import java.util.Locale;

import org.bukkit.command.CommandSender;

import com.aehdev.stealthrealtor.StealthRealtor;

/**
 * Command that queries the plugin version info.
 */
public class CommandRealtorVersion extends Command
{
	/**
	 * Create a new version order.
	 * @param plugin
	 * reference to the main plugin object
	 * @param commandLabel
	 * command name/alias
	 * @param sender
	 * who sent the command
	 * @param args
	 * arguments to the subcommand
	 */
	public CommandRealtorVersion(StealthRealtor plugin, CommandSender sender, String[] args)
	{
		super(plugin, sender, args);
	}

	/**
	 * Run version command; display the version info to the sender.
	 */
	public boolean process()
	{
		sender.sendMessage(String.format((Locale)null,"StealthRealtor Version %s", plugin.getDescription().getVersion()));
		return true;
	}
}
