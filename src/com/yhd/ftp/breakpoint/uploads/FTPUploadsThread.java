package com.yhd.ftp.breakpoint.uploads;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import com.yhd.ftp.breakpoint.core.FTPConnections;

public class FTPUploadsThread implements Runnable {

	private static String encode_charset = "UTF-8";//指定的字符集
	private static String encode_byte = "UTF-8";//解码指定的 byte 数组，构造一个新的 String

	/**
	 * 上传文件到FTP服务器，支持断点续传
	 * 
	 * @param local
	 *            本地文件名称，绝对路径
	 * @param remote
	 *            远程文件路径，使用/home/directory1/subdirectory/file.ext或是
	 *            http://www.guihua.org /subdirectory/file.ext
	 *            按照Linux上的路径指定方式，支持多级目录嵌套，支持递归创建不存在的目录结构
	 * @return 上传结果
	 * @throws IOException
	 */
	public static FTPUploadsStatus upload(FTPClient ftpClient, String local) throws IOException {
		// 设置PassiveMode传输
		ftpClient.enterLocalPassiveMode();
		// 设置以二进制流的方式传输
		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
		ftpClient.setControlEncoding(encode_byte);
		FTPUploadsStatus result;
		// 对远程目录的处理
		File file = new File(local);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String remote = "/" + sdf.format(new Date()) + "/" + new String(file.getName().getBytes(encode_byte), encode_charset);
		String remoteFileName = remote;
		if (remote.contains("/")) {
			remoteFileName = remote.substring(remote.lastIndexOf("/") + 1);
			// 创建服务器远程目录结构，创建失败直接返回
			if (createDirecroty(remote, ftpClient) == FTPUploadsStatus.Create_Directory_Fail) {
				return FTPUploadsStatus.Create_Directory_Fail;
			}
		}

		// 检查远程是否存在文件
		FTPFile[] files = ftpClient.listFiles(new String(remoteFileName.getBytes(encode_byte), encode_charset));
		if (files.length == 1) {
			long remoteSize = files[0].getSize();
			File f = new File(local);
			long localSize = f.length();
			if (remoteSize == localSize) {
				return FTPUploadsStatus.File_Exits;
			} else if (remoteSize > localSize) {
				return FTPUploadsStatus.Remote_isBiggerThan_Local;
			}

			// 尝试移动文件内读取指针,实现断点续传
			result = uploadFile(remoteFileName, f, ftpClient, remoteSize);

			// 如果断点续传没有成功，则删除服务器上文件，重新上传
			if (result == FTPUploadsStatus.Upload_From_Break_Failed) {
				if (!ftpClient.deleteFile(remoteFileName)) {
					return FTPUploadsStatus.Delete_Remote_Faild;
				}
				result = uploadFile(remoteFileName, f, ftpClient, 0);
			}
		} else {
			result = uploadFile(remoteFileName, new File(local), ftpClient, 0);
		}
		return result;
	}

	public static FTPUploadsStatus uploadFile(String remoteFile, File localFile, FTPClient ftpClient, long remoteSize) throws IOException {
		FTPUploadsStatus status;
		// 显示进度的上传
		long step = localFile.length() / 100;
		long process = 0;
		long localreadbytes = 0L;
		RandomAccessFile raf = new RandomAccessFile(localFile, "r");
		OutputStream out = ftpClient.appendFileStream(new String(remoteFile.getBytes(encode_byte), encode_charset));
		// 断点续传
		if (remoteSize > 0) {
			ftpClient.setRestartOffset(remoteSize);
			process = remoteSize / step;
			raf.seek(remoteSize);
			localreadbytes = remoteSize;
		}
		byte[] bytes = new byte[1024];
		int c;
		while ((c = raf.read(bytes)) != -1) {
			out.write(bytes, 0, c);
			localreadbytes += c;
			if (localreadbytes / step != process) {
				process = localreadbytes / step;
				System.out.println("上传进度:" + process);
				// TODO 汇报上传状态
			}
		}
		out.flush();
		raf.close();
		out.close();
		boolean result = ftpClient.completePendingCommand();
		if (remoteSize > 0) {
			status = result ? FTPUploadsStatus.Upload_From_Break_Success : FTPUploadsStatus.Upload_From_Break_Failed;
		} else {
			status = result ? FTPUploadsStatus.Upload_New_File_Success : FTPUploadsStatus.Upload_New_File_Failed;
		}
		return status;
	}

	/** */
	/**
	 * 递归创建远程服务器目录
	 * 
	 * @param remote
	 *            远程服务器文件绝对路径
	 * @param ftpClient
	 *            FTPClient 对象
	 * @return 目录创建是否成功
	 * @throws IOException
	 */
	public static FTPUploadsStatus createDirecroty(String remote, FTPClient ftpClient) throws IOException {
		FTPUploadsStatus status = FTPUploadsStatus.Create_Directory_Success;
		String directory = remote.substring(0, remote.lastIndexOf("/") + 1);
		if (!directory.equalsIgnoreCase("/") && !ftpClient.changeWorkingDirectory(new String(directory.getBytes(encode_byte), encode_charset))) {
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
				String subDirectory = new String(remote.substring(start, end).getBytes(encode_byte), encode_charset);
				if (!ftpClient.changeWorkingDirectory(subDirectory)) {
					if (ftpClient.makeDirectory(subDirectory)) {
						ftpClient.changeWorkingDirectory(subDirectory);
					} else {
						System.out.println("创建目录失败");
						return FTPUploadsStatus.Create_Directory_Fail;
					}
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

	@Override
	public void run() {

	}

	public static void main(String[] args) throws IOException {
		String ip = "10.3.1.190";
		String username = "root";
		String password = "root";

		//				String ip = "121.43.168.180";
		//				String username = "ctvit";
		//				String password = "OitaeHGVIe";
		Integer port = 21;
		FTPClient ftpClient = FTPConnections.getFTPClient(ip, username, password, port);
		FTPUploadsStatus status = FTPUploadsThread.upload(ftpClient, "D:/temp/三十极夜2：黑暗的日子BD中英双字.rmvb");
		System.out.println(status);
	}
}
