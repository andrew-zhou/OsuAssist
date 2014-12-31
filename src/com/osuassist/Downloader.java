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
	
	private static ArrayList<String> downloads; // list of filePaths being downloaded (without .tmp extension)
	
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
			if(!downloads.contains(filePath)) {
				validDownload = true;
				downloads.add(filePath);
			}
		}
		if(validDownload) {
			String tmpFile = filePath+".tmp";
			try {
				// download to .tmp file
				URL url = new URL(downloadURL);
				ReadableByteChannel rbc = Channels.newChannel(url.openStream());
				FileOutputStream fos = new FileOutputStream(tmpFile);
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE); // can only download up to 2^63 bytes - fix later
				fos.close();
				// replace existing file with .tmp file
				File oldFile = new File(filePath);
				File newFile = new File(tmpFile);
				Files.deleteIfExists(Paths.get(oldFile.getPath()));
				newFile.renameTo(oldFile);
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
	
}
