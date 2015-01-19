package com.osuassist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class Downloader {
	
	private static ArrayList<String> downloads = new ArrayList<String>(); // list of filePaths being downloaded (without .tmp extension)
	
	/**
	 * Downloads the file from the specified url to the given path. Replaces any
	 * existing file at the given path. If a file is already being downloaded to 
	 * the given path, the new download will not occur.
	 * 
	 * @param downloadURL The url to download the file from.
	 * @param filePath The [absolute] path of the file once downloaded.
	 */
	public static void downloadFile(String downloadURL, String filePath) {		
		boolean validDownload = false;
		// check whether file is already being downloaded and add to list if it isn't
		synchronized(Downloader.class) {
			validDownload = addToQueue(filePath);
		}
		if(validDownload) {
			String tmpFile = filePath+".tmp";
			try {
				// download to .tmp file
				saveURLToFile(downloadURL, tmpFile);
				// replace existing file with .tmp file
				replaceFile(tmpFile, filePath);
			} catch(IOException e) {
				System.err.println("Error: Could not download file "+filePath+"from "+downloadURL);
				System.err.println(e.getMessage());
			} finally {
				// remove filePath from files being downloaded
				synchronized(Downloader.class) {
					downloads.remove(filePath);
				}
			}
		}
	}
	
	private static boolean addToQueue(String filePath) {
		if(!downloads.contains(filePath)) {
			downloads.add(filePath);
			return true;
		}
		return false;
	}
	
	private static void saveURLToFile(String downloadURL, String filePath) throws IOException {
		URL url = new URL(downloadURL);
		ReadableByteChannel rbc = Channels.newChannel(url.openStream());
		FileOutputStream fos = new FileOutputStream(filePath);
		fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE); // can only download up to 2^63 bytes - fix later
		fos.close();
	}
	
	private static void replaceFile(String from, String to) throws IOException {
		File old = new File(from);
		File _new = new File(to);
		Files.deleteIfExists(Paths.get(_new.getPath()));
		old.renameTo(_new);
	}
}
