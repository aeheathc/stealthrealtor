package com.aehdev.stealthrealtor.lib.multiDB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.logging.Logger;

/**
 * Central class representing a database of any type.
 */
public abstract class Database
{
	/** Reference to the standard logger. */
	protected Logger log;
	
	/** String specified by the main plugin that goes at the beginning of log messages -- normally the plugin name. */
	protected final String PREFIX;
	
	/** The name of the database type, set by the subclass. Used at the beginning of log messages after the PREFIX. */
	protected final String DATABASE_PREFIX;
	
	/** True when a connection has been opened. */
	protected boolean connected;
	
	/** The database connection. */
	protected Connection connection;
	
	/**
	 * Define a database connection.
	 * Most of the actual database specific stuff is done in the subclass constructor,
	 * only the universal parts are done here
	 * @param log Reference to the main logger
	 * @param prefix Text to be prepended to log messages, preferably the name of the plugin
	 * @param dp database system specific log prefix, generally the name of the database system
	 */
	public Database(Logger log, String prefix, String dp)
	{
		this.log = log;
		this.PREFIX = prefix;
		this.DATABASE_PREFIX = dp;
		this.connected = false;
		this.connection = null;
	}
	
	/**
	 * Writes either errors or warnings to the console.
	 * @param toWrite - the String written to the console.
	 * @param severe - whether console output should appear as an error or warning.
	 */
	protected void writeError(String toWrite, boolean severe)
	{
		if(toWrite != null)
		{
			if(severe)
				this.log.severe(this.PREFIX + this.DATABASE_PREFIX + toWrite);
			else
				this.log.warning(this.PREFIX + this.DATABASE_PREFIX + toWrite);
		}
	}
	
	/**
	 * Used to check whether the class for the SQL engine is installed.
	 * @return true, if successful
	 */
	abstract boolean initialize();
	
	/**
	 * Opens a connection with the database.
	 * @return the success of the method.
	 */
	public abstract Connection open();
	
	/**
	 * Closes a connection with the database.
	 */
	public abstract void close();

	/**
	 * Checks the connection between Java and the database engine.
	 * @return the status of the connection, true for up, false for down.
	 */
	public abstract boolean checkConnection();
	
	/**
	 * Sends a query to the SQL database.
	 * @param query the SQL query to send to the database.
	 * @param supressErrors whether to suppress error logging
	 * @return the table of results from the query.
	 */
	public abstract ResultSet query(String query, boolean supressErrors);
	
	/**
	 * Sends a query to the SQL database.
	 * @param query the SQL query to send to the database.
	 * @return the table of results from the query.
	 */
	public final ResultSet query(String query)
	{
		return this.query(query,false);
	}
	
	/**
	 * Make string safe for inclusion in SQL query in a database-specific way.
	 * 
	 * @param text the text to escape
	 * @return the string
	 */
	public abstract String escape(String text);
}