package com.osuassist;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

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
	
	private final static String DATA_PATH = "data.dat"; // Convert relative path to absolute
	private final static String API_URL = "https://osu.ppy.sh/api/get_beatmaps?"; // API Url - need to append API_KEY and mapset id
	private final static String API_KEY = "2229871017ba281f2e62416c5ef55fb9660551ce"; // API Key
	private final static String MAPLIST_INDEX = "https://osu.ppy.sh/p/beatmaplist"; // Initial maplist page
	private final static String MAPLIST_BASE_URL = "https://osu.ppy.sh/p/beatmaplist?l=1&r=0&q=&g=0&la=0&s=4&o=1&m=-1&page="; // Append number to get a page of maplists
	
	/**
	 * Scrapes the Osu website and uses its API for beatmap data and saves this content in a temporary file.
	 * Upon successful completion, the old data file is replaced with the new temporary one.
	 */
	public static void update() {
		try {
			int numPages = getNumPages();
			ArrayList<Integer> mapsetIds = getMapsetIds(numPages);
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(new File(DATA_PATH+".tmp"), true))); // make tmp file to save data to
			for(int id : mapsetIds) {
				JsonArray mapsetData = callAPI(id);
				for(JsonElement mapData : mapsetData) {
					out.println(mapData); // write beatmap data to file
				}
			}
			out.close();
		} catch(IOException e) { // need to improve error response
			System.err.println("Error: Could not connect to Osu website");
			return;
		}		
		
		// delete old data file and replace with new one
		try {
			File oldData = new File(DATA_PATH);
			File newData = new File(DATA_PATH+".tmp");
			Files.deleteIfExists(Paths.get(oldData.getPath()));
			newData.renameTo(oldData);
		} catch(IOException e) {
			System.err.println("Error: Could not delete old data file");
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
	
	/**
	 * 
	 * @param numPages The number of beatmap listing pages currently on the Osu website.
	 * @return An ArrayList of the ids for all beatmap sets.
	 * @throws IOException A connection to the Osu website could not be established.
	 */
	private static ArrayList<Integer> getMapsetIds(int numPages) throws IOException {
		ArrayList<Integer> mapsetIds = new ArrayList<Integer>();
		
		for(int i=1; i<=numPages; i++) {
			String url = MAPLIST_BASE_URL + i;
			System.out.println(url); // testing only
			Document pageDoc = Jsoup.connect(url).timeout(0).get(); // should change the timeout value to not be infinite
			Elements titles = pageDoc.getElementsByClass("title"); // mapset ids are in the href tags of the title classes
			for(Element title : titles) {
				Pattern pattern = Pattern.compile("\\d+$"); // strip the numeric ID from the full string
				Matcher matcher = pattern.matcher(title.attr("href"));
				if(matcher.find()) {
					mapsetIds.add(Integer.parseInt(matcher.group()));
				} else { // if code reaches this, need to re-check osu website source code for new format
					System.err.println("Error: No ID found for mapset.");
				}
			}
		}
		
		return mapsetIds;
	}
	
	/**
	 * 
	 * @param mapsetId The mapset id to call the api with.
	 * @return A JsonArray object with data of all the beatmaps with the given mapset id.
	 * @throws IOException A connection to the Osu website could not be established.
	 */
	private static JsonArray callAPI(int mapsetId) throws IOException {
		URL url = new URL(API_URL+"k="+API_KEY+"&s="+mapsetId);
		System.out.println(url.toString()); // testing only
		HttpURLConnection request = (HttpURLConnection)url.openConnection();
		request.setConnectTimeout(0); // should change the timeout value to not be infinite
		request.connect(); // send api request
		JsonParser jp = new JsonParser(); 
		JsonElement root = jp.parse(new InputStreamReader((InputStream)request.getContent())); // get api response and parse as json
		JsonArray arr = root.getAsJsonArray();
		return arr;
	}
}
