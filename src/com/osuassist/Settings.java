package com.osuassist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class Settings {
	private final static String SETTINGS_PATH = "settings.properties";
	
	private static Properties keyValues = new Properties();
	private static boolean isInit = false;
	
	private static void init() throws IOException {
		if(!isInit) {
			File f = new File(SETTINGS_PATH);
			if(!f.exists() && !f.createNewFile()) {
				throw new IOException("Error: Could not create settings file.");
			}
			FileInputStream in = new FileInputStream(f);
			keyValues.load(in);
			in.close();
			isInit = true;
		}
	}
	
	private static void save() throws IOException {
		File f = new File(SETTINGS_PATH);
		FileOutputStream out = new FileOutputStream(f);
		keyValues.store(out, "");
		out.close();
	}
	
	public static String getValue(String key) throws IOException {
		init();
		return keyValues.getProperty(key);
	}
	
	public static void setValue(String key, String value) throws IOException {
		init();
		keyValues.setProperty(key, value);
		save();
	}
}
