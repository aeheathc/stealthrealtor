package com.aehdev.stealthrealtor.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import com.aehdev.stealthrealtor.StealthRealtor;

/**
 * Engine for parsing and running all commands accepted by the plugin.
 */
public class RealtorCommandExecutor implements CommandExecutor
{

	/** reference to the main plugin object */
	private final StealthRealtor plugin;

	/**
	 * Start accepting commands.
	 * @param plugin reference to the main plugin object
	 */
	public RealtorCommandExecutor(StealthRealtor plugin)
	{
		this.plugin = plugin;
	}

    /**
     * Process players' commands which are sent here by Bukkit.
     * 
     * @param sender who sent the command, hopefully a player
     * @param command the command name
     * @param commandLabel actual command alias that was used
     * @param args arguments to the command
     * @return true on success
     */
	@Override
	public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args)
	{
		if(args.length < 1) return (new CommandRealtorHelp(plugin, sender, args)).process();
		
		int argsNum = args.length-1;
		String cmdArgs[] = new String[argsNum];
		System.arraycopy(args, 1, cmdArgs, 0,argsNum);

		String commandName = command.getName().toLowerCase();
		String type = args[0];
		com.aehdev.stealthrealtor.commands.Command cmd = null;

		if(commandName.equalsIgnoreCase("realtor"))
		{
			if(type.equalsIgnoreCase("buy"))			cmd = new CommandRealtorBuy(plugin, sender, cmdArgs);
			else if(type.equalsIgnoreCase("rent"))		cmd = new CommandRealtorRent(plugin,  sender, cmdArgs);
			else if(type.equalsIgnoreCase("info"))		cmd = new CommandRealtorInfo(plugin, sender, cmdArgs);
			else if(type.equalsIgnoreCase("list"))		cmd = new CommandRealtorList(plugin, sender, cmdArgs);
			else if(type.equalsIgnoreCase("reload"))	cmd = new CommandRealtorReload(plugin, sender, cmdArgs);
			else if(type.equalsIgnoreCase("version"))	cmd = new CommandRealtorVersion(plugin, sender, cmdArgs);
			else										cmd = new CommandRealtorHelp(plugin, sender, cmdArgs);

			cmd.process();
			return true;
		}
		return false;
	}
}
