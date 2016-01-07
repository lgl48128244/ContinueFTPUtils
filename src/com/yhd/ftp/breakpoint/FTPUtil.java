package com.yhd.ftp.breakpoint;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;

public class FTPUtil {

	private static FTPClient ftpClient = new FTPClient();

	private static Logger logger = Logger.getLogger(FTPUtil.class);

	private static String encode_charset = "UTF-8";

	private final static int CACHE_SIZE = 8 * 1024 * 1024;

	private final static String DIRECTORY_CREATE_FAILED = "目录创建失败";

	private final static String DIRECTORY_CREATE_SUCCESS = "目录创建成功";

	private final static String UPLOAD_NEW_SUCCESS = "上传文件成功";

	private final static String UPLOAD_NEW_FAILED = "上传文件失败";

	private final static String UPLOAD_RESUME_SUCCESS = "断点续传成功";

	private final static String UPLOAD_RESUME_FAILED = "断点续传失败";

	private final static String FILE_EXISTS = "文件已存在 ";

	private final static String FILE_DOES_NOT_MATCH = "文件不匹配";

	/**
	 * 
	 * @param ftpClient FTPClient
	 * @param materialId 素材ID
	 * @param localFile 本地文件路径
	 * @return 上传文件路径 (ftp://ip/dir/.../file.ext)
	 */
	public static String upload(FTPClient ftpClient, String localFile) {
		try {
			File file = new File(localFile);
			ftpClient.enterLocalPassiveMode();// 设置PassiveMode传输
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);// 设置以二进制流的方式传输
			ftpClient.setControlEncoding(encode_charset);// 设置编码格式

			String fileName = new String(file.getName().getBytes(encode_charset), encode_charset);//防止文件名乱码
			String dateDir = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
			String remoteFileName = "/" + dateDir + "/" + fileName;

			// 检测目标路径是否存在
			if (createDirecroty(ftpClient, remoteFileName) == DIRECTORY_CREATE_FAILED) {
				logger.error(DIRECTORY_CREATE_FAILED);
				return null;
			}
			String result = null;
			FTPFile[] ftpFiles = ftpClient.listFiles("/" + dateDir);
			logger.debug("上传后返回的文件名" + remoteFileName);
			if (ftpFiles != null && ftpFiles.length > 0) {
				for (FTPFile ftpFile : ftpFiles) {
					// 若存在相同文件名文件，则判断是否进行断点续传
					if (ftpFile.getName().equals(fileName)) {
						long remoteSize = ftpFile.getSize();
						long localSize = file.length();
						if (remoteSize == localSize) {
							logger.error(FILE_EXISTS);
							return null;
						} else if (remoteSize > localSize) {
							logger.error(FILE_DOES_NOT_MATCH);
							return null;
						}
						// 尝试移动文件内读取指针,实现断点续传
						result = uploadFile(remoteFileName, file, ftpClient, remoteSize);
						logger.debug("上传结果：" + result);
						if (result.equals(UPLOAD_NEW_SUCCESS) || result.equals(UPLOAD_RESUME_SUCCESS)) {
							logger.debug(UPLOAD_RESUME_SUCCESS);
							return remoteFileName.substring(1);
						} else {
							logger.error("上传失败!!!");
							return null;
						}
					}
				}
				// 重新上传
				ftpClient.deleteFile(remoteFileName);
				result = uploadFile(remoteFileName, file, ftpClient, 0);
				logger.debug("上传结果：" + result);

				if (result.equals(UPLOAD_NEW_SUCCESS) || result.equals(UPLOAD_RESUME_SUCCESS)) {
					logger.debug(UPLOAD_NEW_SUCCESS);
					return remoteFileName.substring(1);
				} else {
					logger.error("上传失败!!!");
					return null;
				}
			} else {
				// 重新上传
				ftpClient.deleteFile(remoteFileName);
				result = uploadFile(remoteFileName, file, ftpClient, 0);
				logger.debug("上传结果：" + result);
				if (result.equals(UPLOAD_NEW_SUCCESS) || result.equals(UPLOAD_RESUME_SUCCESS)) {
					logger.debug(UPLOAD_NEW_SUCCESS);
					return remoteFileName.substring(1);
				} else {
					logger.error("上传失败!!!");
					return null;
				}
			}

		} catch (FileNotFoundException e) {
			logger.error("文件不存在:" + e);
			return null;
		} catch (UnsupportedEncodingException e) {
			logger.error("编码格式不支持:" + e);
			return null;
		} catch (IOException e) {
			logger.error("IO错误:" + e);
			return null;
		} finally {
			closeConnections();// 关闭连接
		}
	}

	/**
	 * 
	 * @param materialId
	 * @param remoteFile
	 * @param localFile
	 * @param ftpClient
	 * @param remoteSize
	 * @return
	 * @throws IOException
	 */
	public static String uploadFile(String remoteFile, File localFile, FTPClient ftpClient, long remoteSize) throws IOException {
		String status;
		// 显示进度的上传
		long localSize = localFile.length();
		long process = 0;//百分比
		long localreadbytes = 0L;
		RandomAccessFile raf = new RandomAccessFile(localFile, "r");
		OutputStream out = ftpClient.appendFileStream(new String(remoteFile.getBytes(encode_charset), encode_charset));
		// 断点续传
		if (remoteSize > 0) {
			ftpClient.setRestartOffset(remoteSize);
			 process = remoteSize / localSize;
			raf.seek(remoteSize);
			localreadbytes = remoteSize;
		}
		byte[] bytes = new byte[CACHE_SIZE];
		int c;
		while ((c = raf.read(bytes)) != -1) {
			out.write(bytes, 0, c);
			localreadbytes += c;
			if (localreadbytes / localSize != process) {
				process = localreadbytes / localreadbytes;
				System.out.println("上传进度:" + process);
				// TODO 汇报上传状态
			}
		}
		out.flush();
		raf.close();
		out.close();
		boolean result = ftpClient.completePendingCommand();
		if (remoteSize > 0) {
			status = result ? UPLOAD_RESUME_SUCCESS : UPLOAD_RESUME_FAILED;
		} else {
			status = result ? UPLOAD_NEW_SUCCESS : UPLOAD_NEW_FAILED;
		}
		return status;
	}

	/**
	 * 递归创建远程服务器目录
	 * 
	 * @param remote 
	 * @param ftpClient FTPClient
	 * @return status
	 * @throws IOException
	 */
	private static String createDirecroty(FTPClient ftpClient, String remote) throws IOException {
		String status = DIRECTORY_CREATE_SUCCESS;
		String directory = remote.substring(0, remote.lastIndexOf("/") + 1);
		if (!directory.equalsIgnoreCase("/") && !ftpClient.changeWorkingDirectory(directory)) {
			// 如果远程目录不存在，则递归创建远程服务器目录
			int start = 0;
			int end = 0;
			if (directory.startsWith("/")) {
				start = 1;
			} else {
				start = 0;
			}
			end = directory.indexOf("/", start);
			while (true) {
				String subDirectory = new String(remote.substring(start, end));
				if (!ftpClient.changeWorkingDirectory(subDirectory)) {
					if (!ftpClient.makeDirectory(subDirectory))
						return DIRECTORY_CREATE_FAILED;
					ftpClient.changeWorkingDirectory(subDirectory);
				}
				start = end + 1;
				end = directory.indexOf("/", start);

				// 检查所有目录是否创建完毕
				if (end <= start) {
					break;
				}
			}
		}
		return status;
	}

	/**
	 * 
	 * @param ftpHost 主机
	 * @param ftpUserName 用户名
	 * @param ftpPassword 密码
	 * @param ftpPort 端口
	 * @return
	 */
	public static FTPClient getFTPClient(String ftpHost, String ftpUserName, String ftpPassword, int ftpPort) {
		try {
			ftpClient.setControlEncoding(encode_charset);
			ftpClient.connect(ftpHost, ftpPort);// 连接FTP服务器
			ftpClient.login(ftpUserName, ftpPassword);// 登陆FTP服务器
			logger.debug("ftp参数：ftpHost=" + ftpHost + " ftpUserName=" + ftpUserName + " ftpPassword=" + ftpPassword + " ftpPort=" + ftpPort);
			if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
				logger.info("未连接到FTP，用户名或密码错误。");
				ftpClient.disconnect();
			} else {
				logger.info("FTP连接成功。");
			}
		} catch (SocketException e) {
			logger.info("FTP的IP地址可能错误，请正确配置" + e);
		} catch (IOException e) {
			logger.info("FTP的端口错误,请正确配置" + e);
		}
		return ftpClient;
	}

	/**
	 * 关闭连接
	 */
	private static void closeConnections() {
		try {
			if (ftpClient.isConnected()) {
				ftpClient.disconnect();
			}
		} catch (IOException e) {
			logger.info("关闭连接失败" + e);
		}
	}

	public static void main(String[] args) throws IOException {
		String ip = "10.3.1.190";
		String username = "root";
		String password = "root";

		// String ip = "121.43.168.180";
		// String username = "ctvit";
		// String password = "OitaeHGVIe";
		Integer port = 21;
		FTPClient ftpClient = FTPUtil.getFTPClient(ip, username, password, port);
		String status = FTPUtil.upload(ftpClient, "D:/temp/电影天堂www.dygod.net.三十极夜.中英双字.1024分辨率.rmvb");
		System.out.println(status);
	}
}