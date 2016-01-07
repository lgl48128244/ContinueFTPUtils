package com.yhd.ftp.breakpoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import com.yhd.ftp.breakpoint.downloads.FTPDownloadsThread;

public class FTPDownloads {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/**
		 * @param host
		 * @param username
		 * @param passwd
		 * @param port
		 * @param sourceFile
		 * @param targetFile
		 */
//		String host = "ftp.gnu.org";
//		String port = "80";
//		String username = "";
//		String passwd = "";
//		String sourceFile = "/";
//		String targetFile = "D:/ftpdownloads/";
		
		Properties properties = new Properties();
		FileInputStream fis = null;
		File propFile;
		try {
			propFile = new File("url.properties");
			if(!propFile.exists()) {
				System.out.println("File url.properties doesn't exists!");
				return;
			}
			fis = new FileInputStream(propFile);
			properties.load(fis);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		} finally {
			try {
				if(fis != null){
					fis.close();
					fis = null;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		String host = properties.getProperty("host").trim();
		String port = properties.getProperty("port").trim();
		String username = properties.getProperty("username").trim();
		String password = properties.getProperty("password").trim();
		String sourceFile = properties.getProperty("source_folder").trim();
		String targetFile = properties.getProperty("target_folder").trim();
		
		System.out.println("Connect to Remote FTP Server [ " + host + " ],please wait!");
		System.out.println("Remote Server user: " + username);
		System.out.println("Remote Server file/folder path : " + sourceFile);
		FTPDownloadsThread fdt = new FTPDownloadsThread(host, username, password,
				port, sourceFile, targetFile);
		Thread downloadThread = new Thread(fdt);
		downloadThread.start();
	}

}
