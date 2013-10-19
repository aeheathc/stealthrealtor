package com.aehdev.stealthrealtor.lib.multiDB;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.StringCharacterIterator;
import java.util.logging.Logger;

/**
 * Represents a SQLite database. Meant to be used as a {@link Database}
 */
public class SQLite extends Database
{
	/** Directory containing the SQLite database file. */
	public String location;
	
	/** Filename of the SQLite database. */
	public String name;
	
	/** Absolute path to the SQLite database file constructed from arguments. */
	private File sqlFile;
	
	/**
	 * Define a SQLite connection.
	 * 
	 * @param log reference to the main logger
	 * @param prefix Prefix for log messages, preferably the plugin name
	 * @param name Filename of the SQLite database
	 * @param location Directory containing the SQLite database file
	 */
	public SQLite(Logger log, String prefix, String name, String location)
	{
		super(log,prefix,"[SQLite] ");
		this.name = name;
		this.location = location;
		File folder = new File(this.location);
		if(this.name.contains("/") || this.name.contains("\\") || this.name.endsWith(".db"))
			this.writeError("The database name cannot contain: /, \\, or .db", true);
		if(!folder.exists()) folder.mkdir();
		
		sqlFile = new File(folder.getAbsolutePath() + File.separator + name + ".db");
	}
	
	/**
	 * Check for the DB driver
	 * @return true if the DB driver was successfully loaded
	 */
	protected boolean initialize()
	{
		try{
		  Class.forName("org.sqlite.JDBC");
		  return true;
		}catch(ClassNotFoundException e){
		  this.writeError("Class not found in initialize(): " + e, true);
		  return false;
		}
	}
	
	/**
	 * Open the database connection
	 * @return the Connection object for this database connection
	 */
	@Override
	public Connection open()
	{
		if(initialize())
		{
			try{
			  this.connection = DriverManager.getConnection("jdbc:sqlite:" + sqlFile.getAbsolutePath());
			  return this.connection;
			}catch(SQLException e){
			  this.writeError("SQL exception in open(): " + e, true);
			}
		}
		return null;
	}
	
	/**
	 * Close the database connection.
	 */
	@Override
	public void close()
	{
		if(connection != null)
			try{
				connection.close();
			}catch(SQLException ex){
				this.writeError("SQL exception in close(): " + ex, true);
			}
	}

	/**
	 * Check the status of the database connection.
	 * @return true if the connection is open and valid
	 */
	@Override
	public boolean checkConnection()
	{
		if(connection != null) return true;
		return false;
	}
	
	/**
	 * Query the database.
	 * @param query the SQL query to use
	 * @param suppressErrors when true, this function will suppress errors. Useful for when queries that might fail are part of normal operation.
	 * @return a ResultSet containing the results of the query
	 */
	@Override
	public ResultSet query(String query, boolean suppressErrors)
	{
		Statement statement = null;
		ResultSet result = null;
		query = query.trim();
		
		try{
			connection = this.open();
			statement = connection.createStatement();			
	
		    /* We remove LIMITs from UPDATEs for compatibility with SQLite libraries not compiled with SQLITE_ENABLE_UPDATE_DELETE_LIMIT
		    Horrible, I know! Blame SQLite's terrible design that allows critical SQL features to be missing by default. */
			if(query.substring(0,6).equalsIgnoreCase("UPDATE") || query.substring(0,6).equalsIgnoreCase("DELETE"))
			{
				int end = query.indexOf("LIMIT");
		    	if(end > 0) query = query.substring(0,end);
			}

		    if(query.trim().toUpperCase().startsWith("SELECT"))
		    	result = statement.executeQuery(query);
		    else
		    	statement.executeUpdate(query);

			return result;	
		}catch(SQLException e){
			if(e.getMessage().toLowerCase().contains("locking") || e.getMessage().toLowerCase().contains("locked"))
			{
				return retry(query);
			}else{
				if(!suppressErrors)
					this.writeError("SQL exception in query(): " + e.getMessage() + " Query in full: " + query, false);
			}
		}
		return null;
	}

	
	/**
	 * Retries a statement when an initial attempt reported a lock related failure.
	 * 
	 * @param query the SQL query attempted
	 * @return a ResultSet containing the results of the query
	 */
	private ResultSet retry(String query)
	{
		try{this.wait(1000);}catch(Exception e){}
		Statement statement = null;
		ResultSet result = null;
		
		try{
			statement = connection.createStatement();
			result = statement.executeQuery(query);
			return result;
		}catch(SQLException ex){
			if(ex.getMessage().toLowerCase().contains("locking") || ex.getMessage().toLowerCase().contains("locked"))
				this.writeError("Please close your previous ResultSet to run the query: \n\t" + query, false);
			else
				this.writeError("SQL exception in retry(): " + ex.getMessage(), false);
		}
		
		return null;
	}
	
	/**
	 * Make string safe for SQL query using dumb Pascal-style thing which is the only method sqlite supports,
	 * despite not being standard SQL, because they don't support backslash escaping because "it's not standard SQL".
	 * 
	 * @param text the text to escape
	 * @return the string
	 * @return
	 */
	public String escape(String text)
	{
        final StringBuffer sb                   = new StringBuffer( text.length() * 2 );
        final StringCharacterIterator iterator  = new StringCharacterIterator( text );
  	  	char character = iterator.current();

        while(character != StringCharacterIterator.DONE)
        {
            if(character == '\'' ) sb.append( "\'\'" );
            else sb.append(character);

            character = iterator.next();
        }
        return sb.toString();
	}
}