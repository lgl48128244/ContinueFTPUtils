package com.yhd.ftp.breakpoint.downloads;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.log4j.Logger;

import com.yhd.ftp.breakpoint.core.FTPConnections;

public class FTPDownloadsThread implements Runnable {

	private FTPClient ftpClient = new FTPClient();
	private static Logger logger = Logger.getLogger(FTPDownloadsThread.class);
	private static String encode_charset = "UTF-8";//指定的字符集
	private static String encode_byte = "UTF-8";//解码指定的 byte 数组，构造一个新的 String
	private String host;
	private String username;
	private String passwd;
	private String port;
	private String sourceFile;
	private String targetFile;

	public FTPDownloadsThread(String host, String username, String passwd, String port, String sourceFile, String targetFile) {
		// 设置将过程中使用到的命令输出到控制台
		this.host = host;
		this.username = username;
		this.passwd = passwd;
		this.port = port;
		this.sourceFile = sourceFile;
		this.targetFile = targetFile;
		this.ftpClient.addProtocolCommandListener(new ProtocolCommandListener() {

			@Override
			public void protocolReplyReceived(ProtocolCommandEvent arg0) {
			}

			@Override
			public void protocolCommandSent(ProtocolCommandEvent arg0) {
			}
		});
	}

	/** */
	/**
	 * 从FTP服务器上下载文件,支持断点续传，上传百分比汇报
	 * 
	 * @param remote
	 *            远程文件路径
	 * @param local
	 *            本地文件路径
	 * @return 上传的状态
	 * @throws IOException
	 */
	public FTPDownloadsStatus download(String remote, String local) throws IOException {
		// 设置被动模式
		ftpClient.enterLocalPassiveMode();
		// 设置以二进制方式传输
		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
		FTPDownloadsStatus result;

		// 检查远程文件是否存在
		FTPFile[] files = ftpClient.listFiles(new String(remote.getBytes(encode_byte), encode_charset));
		if (files.length != 1) {
			logger.debug("Remote file doesn't exists");
			return FTPDownloadsStatus.Remote_File_Not_Exists;
		}

		long lRemoteSize = files[0].getSize();
		File f = new File(local);
		// 本地存在文件，进行断点下载
		if (f.exists()) {
			long localSize = f.length();
			// 判断本地文件大小是否大于远程文件大小
			if (localSize >= lRemoteSize) {
				logger.debug("Local file is bigger than remote file, download stop");
				return FTPDownloadsStatus.Local_isBiggerThan_Remote;
			}

			// 进行断点续传，并记录状态
			FileOutputStream out = new FileOutputStream(f, true);
			ftpClient.setRestartOffset(localSize);
			InputStream in = ftpClient.retrieveFileStream(new String(remote.getBytes(encode_byte), encode_charset));
			byte[] bytes = new byte[1024];
			long step = lRemoteSize / 100;
			long process = localSize / step;
			int c;
			while ((c = in.read(bytes)) != -1) {
				out.write(bytes, 0, c);
				localSize += c;
				long nowProcess = localSize / step;
				if (nowProcess > process) {
					process = nowProcess;
					if (process % 10 == 0)
						logger.debug("Download process :" + process);
				}
			}
			in.close();
			out.close();
			boolean isDo = ftpClient.completePendingCommand();
			if (isDo) {
				result = FTPDownloadsStatus.Download_From_Break_Success;
			} else {
				result = FTPDownloadsStatus.Download_From_Break_Failed;
			}
		} else {
			OutputStream out = new FileOutputStream(f);
			InputStream in = ftpClient.retrieveFileStream(new String(remote.getBytes(encode_byte), encode_charset));
			byte[] bytes = new byte[1024];
			long step = lRemoteSize / 100;
			long process = 0;
			long localSize = 0L;
			int c;
			while ((c = in.read(bytes)) != -1) {
				out.write(bytes, 0, c);
				localSize += c;
				long nowProcess = localSize / step;
				if (nowProcess > process) {
					process = nowProcess;
					if (process % 10 == 0) {
						logger.debug("Download process :" + process);
					}
				}
			}
			in.close();
			out.close();
			boolean upNewStatus = ftpClient.completePendingCommand();
			if (upNewStatus) {
				result = FTPDownloadsStatus.Download_New_Success;
			} else {
				result = FTPDownloadsStatus.Download_New_Failed;
			}
		}
		return result;
	}

	@Override
	public void run() {
		try {
			FTPConnections.getFTPClient(host, username, passwd, new Integer(port));
			this.download(sourceFile, targetFile);
			FTPConnections.closeConnections();
		} catch (IOException e) {
			logger.debug("Connect to FTP [" + host + ":" + port + "] error: " + e.getMessage());
			System.exit(-1);
		}
	}
}
