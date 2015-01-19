package com.osuassist;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * This class provides methods for updating the application's internal database of Osu beatmaps.
 * This should be done by calling Updater.update(). Note that it may currently take several minutes to
 * fully update the database.
 * 
 * TODO:
 *  - Improve update speeds
 *  - Smart updating (only update the new content)
 *  - Improve error handling
 * 
 * @author Andrew Zhou
 * @date December 29, 2014
 * @version 1.0
 */
public class Updater {

	private final static String INI_LASTUPDATE_KEY = "lastUpdate";
	private final static String DATA_PATH = "jdbc:sqlite:data.db";
	private final static String MAPLIST_INDEX = "https://osu.ppy.sh/p/beatmaplist"; // Initial maplist page
	private final static String MAPLIST_BASE_URL = "https://osu.ppy.sh/p/beatmaplist?l=1&r=0&q=&g=0&la=0&s=4&o=1&m=-1&page="; // Append number to get a page of maplists

	/**
	 * Scrapes the Osu website for beatmap data and saves this content to a local database.
	 */
	public static void update() {
		try {
			int numPages = getNumPages();
			Date lastUpdateTime = getLastUpdateTime();		
			ArrayList<MapSet> mapsets = new ArrayList<MapSet>();
	        for(int i=1; i<=numPages; i++) {
	        	String url = MAPLIST_BASE_URL+i;
	        	Document doc = Jsoup.connect(url).get();
	        	Date pageUpdateTime = getPageUpdateTime(doc);
	        	
	        	if(!pageUpdateTime.before(lastUpdateTime)) {
	        		parsePage(doc, mapsets);
	        	} else {
	        		break;
	        	}
	        }
	        
			updateDB(mapsets);
			setLastUpdateTime();
	        
		} catch(Exception e) {
			System.err.println(e.getMessage());
		}
	}
	
	private static void setLastUpdateTime() throws IOException {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); // year-month-day
		Date date = new Date(); // get current date
		Settings.setValue(INI_LASTUPDATE_KEY, dateFormat.format(date));
	}
	
	private static Date getLastUpdateTime() throws IOException, ParseException {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); // year-month-day
		String d = Settings.getValue(INI_LASTUPDATE_KEY);
		if(d != null) {
			return dateFormat.parse(d);
		} else {
			return new Date(0); // return standard base time
		}
	}
	
	private static Date getPageUpdateTime(Document doc) throws ParseException {
		Element details = doc.getElementsByClass("small-details").first();		
		Document d = Jsoup.parse(details.html());
		Element dateElement = d.getElementsByClass("initiallyHidden").first();
		String date = dateElement.text();
		
		// Format date string if it is part of a beatmap pack
		if(date.contains("|")) {
			Pattern pattern = Pattern.compile("\\w{3,} \\d+, \\d{4}");
			Matcher matcher = pattern.matcher(date);
			if(matcher.find()) {
				date = matcher.group();
			} else {
				System.err.println("Error: No valid date found for page");
			}
		}
		
		DateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy");
		return dateFormat.parse(date);
	}
	
	private static void updateDB(ArrayList<MapSet> mapsets) throws SQLException {
		String sCreate = "CREATE TABLE IF NOT EXISTS beatmaps (id INTEGER PRIMARY KEY, name TEXT)";
		Connection conn = DriverManager.getConnection(DATA_PATH);
		try {
			// create table if it doesn't exist
			Statement stmt = conn.createStatement();
			stmt.executeUpdate(sCreate); 

			try {
				PreparedStatement sInsert = conn.prepareStatement("INSERT OR REPLACE INTO beatmaps VALUES(?,?)");
				try {
					// insert beatmap data into table
					for(int i = 0; i< mapsets.size(); i++) {
						sInsert.setInt(1, mapsets.get(i).getId());
						sInsert.setString(2, mapsets.get(i).getName());
						sInsert.addBatch();
						// Execute every 500 items
						if((i+1)%500 == 0) {
							sInsert.executeBatch();
						}
					}
				} finally {
					sInsert.close();
				}
			} finally {
				stmt.close();
			}
		} finally {
			conn.close();
		}
	}
	
	private static void parsePage(Document doc, ArrayList<MapSet> mapsets) {
		Elements maps = doc.getElementsByClass("maintext");
		for(Element map : maps) {
			Document mapDoc = Jsoup.parse(map.html());
			Element title = mapDoc.getElementsByClass("title").first(); // get title element
			Pattern pattern = Pattern.compile("\\d+$"); // strip the numeric ID from the full string
			Matcher matcher = pattern.matcher(title.attr("href"));
			if(matcher.find()) {
				int id = Integer.parseInt(matcher.group()); // get id
				String artist = mapDoc.getElementsByClass("artist").first().text(); // get artist name
				String name = title.text(); // get song name
				MapSet ms = new MapSet(id, artist+" - "+name); // map name format is "artist - songname"
				mapsets.add(ms);
			} else {
				System.err.println("Error: No ID found for mapset.");
			}
		}
	}
	
	/**
	 * 
	 * @return The current number of beatmap listing pages on the Osu website.
	 * @throws IOException A connection to the Osu website could not be established.
	 */
	private static int getNumPages() throws IOException  {
		Document doc = Jsoup.connect(MAPLIST_INDEX).get();
		Element pages = doc.getElementsByClass("pagination").first(); // num of pages is in the pagination class
		String s = pages.text();
		Pattern stripLeadingPattern = Pattern.compile("\\d+\\s*Next$"); // strip all the misc. numbers
		Matcher stripLeadingMatcher = stripLeadingPattern.matcher(s);
		if(stripLeadingMatcher.find()) {
			Pattern stripTrailingPattern = Pattern.compile("^\\d+"); // strip away the "Next" and trailing whitespace
			Matcher stripTrailingMatcher = stripTrailingPattern.matcher(stripLeadingMatcher.group());
			if(stripTrailingMatcher.find()) {
				return Integer.parseInt(stripTrailingMatcher.group());
			} else { // if code reaches here or below, need to re-check osu website source code for new format
				System.err.println("Error: Could not retrieve the number of beatmap listing pages");
				return -1;
			}
		} else { 
			System.err.println("Error: Could not retrieve the number of beatmap listing pages");
			return -1;
		}
	}
}
