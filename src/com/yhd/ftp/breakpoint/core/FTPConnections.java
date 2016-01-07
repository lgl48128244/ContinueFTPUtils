package com.yhd.ftp.breakpoint.core;

import java.io.IOException;
import java.net.SocketException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;

public class FTPConnections {
	private static FTPClient ftpClient = new FTPClient();
	private static Logger logger = Logger.getLogger(FTPConnections.class);
	private static String encode_byte = "UTF-8";//解码指定的 byte 数组，构造一个新的 String

	/**
	 * 连接到FTP服务器
	 * 
	 * @param hostname
	 *            主机名
	 * @param port
	 *            端口
	 * @param username
	 *            用户名
	 * @param password
	 *            密码
	 * @return 是否连接成功
	 * @throws IOException
	 */
	public static FTPClient getFTPClient(String ftpHost, String ftpUserName, String ftpPassword, int ftpPort) {
		try {
			ftpClient.setControlEncoding(encode_byte);
			ftpClient.connect(ftpHost, ftpPort);// 连接FTP服务器  
			ftpClient.login(ftpUserName, ftpPassword);// 登陆FTP服务器  
			if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
				logger.info("未连接到FTP，用户名或密码错误。");
				ftpClient.disconnect();
			} else {
				logger.info("FTP连接成功。");
			}
		} catch (SocketException e) {
			logger.info("FTP的IP地址可能错误，请正确配置。" + e.getMessage());
		} catch (IOException e) {
			logger.info("FTP的端口错误,请正确配置。" + e.getMessage());
		}
		return ftpClient;
	}

	/**
	 * 断开与远程服务器的连接
	 * 
	 * @throws IOException
	 */
	public static void closeConnections() {
		try {
			if (ftpClient.isConnected()) {
				ftpClient.disconnect();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.info("关闭连接失败" + e.getMessage());
		}
	}
}
