package com.aehdev.stealthrealtor.commands;

import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import com.aehdev.stealthrealtor.StealthRealtor;
import com.aehdev.stealthrealtor.lib.multiDB.Database;

/**
 * Represents a StealthRealtor command. That is, each object is a sub-command of
 * /realtor. For example, "/realtor buy"
 */
public abstract class Command
{
	/** Reference to the main plugin object. */
	protected StealthRealtor plugin = null;
	
	/** Reference to database handler */
	protected Database db = StealthRealtor.db;

	/** The sender/origin of the command. We will almost always need this to be
	 * a player, especially for commands that aren't read-only. */
	protected CommandSender sender = null;

	/** The command arguments. */
	protected String[] args = null;

	/** Matches valid numbers. */
	protected static String DECIMAL_REGEX = "(\\d+\\.\\d+)|(\\d+\\.)|(\\.\\d+)|(\\d+)";

	/** The logging object with which we write to the server log. */
	protected static final Logger log = Logger.getLogger("Minecraft");

	/**
	 * Define a new command.
	 * @param plugin
	 * Reference back to main plugin object.
	 * @param commandLabel
	 * Alias typed by user for this command.
	 * @param sender
	 * Who sent the command. Should be a player, but might be console.
	 * @param args
	 * Arguments passed to the command.
	 */
	public Command(StealthRealtor plugin, CommandSender sender, String[] args)
	{
		this.plugin = plugin;
		this.sender = sender;
		this.args = args;
	}

	/**
	 * Run the command.
	 * @return true, if successful
	 */
	public abstract boolean process();
}
