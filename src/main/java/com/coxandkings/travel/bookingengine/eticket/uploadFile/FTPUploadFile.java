package com.coxandkings.travel.bookingengine.eticket.uploadFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import org.apache.log4j.Logger;

import com.coxandkings.travel.bookingengine.config.ETicketConfig;

public class FTPUploadFile {

	private static final Logger logger = Logger.getLogger(FTPUploadFile.class);

	private String ftpServer = ETicketConfig.getmFTPServer();
	private static final int FTP_PORT = 21;
	private String ftpUser = ETicketConfig.getmFTPUser();
	private String ftpPassword = ETicketConfig.getmFTPPassword();
	private String localFileLocation = ETicketConfig.getmUserdir() + "/";

	public void uploadFile(String uniqueID) {
		FTPClient ftpClient = new FTPClient();
		try {

			ftpClient.connect(ftpServer, FTP_PORT);
			ftpClient.login(ftpUser, ftpPassword);
			ftpClient.enterLocalPassiveMode();
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

			// APPROACH #1: uploads first file using an InputStream
			File localFile = new File(localFileLocation + uniqueID + ".pdf");
			String remoteFileName = uniqueID + ".pdf";
			InputStream inputStream = new FileInputStream(localFile);
			System.out.println("Start uploading first file");
			boolean done = ftpClient.storeFile(remoteFileName, inputStream);
			inputStream.close();
			if (done) {
				System.out.println("The first, file is uploaded successfully.");
			}
		} catch (IOException ex) {
			System.out.println("Error: " + ex.getMessage());
			ex.printStackTrace();
		} finally {
			try {
				if (ftpClient.isConnected()) {
					ftpClient.logout();
					ftpClient.disconnect();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public void downloadFile(String uniqueID) {
		FTPClient ftpClient = new FTPClient();
		try {

			ftpClient.connect(ftpServer, FTP_PORT);
			ftpClient.login(ftpUser, ftpPassword);
			ftpClient.enterLocalPassiveMode();
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

			// APPROACH #1: uploads first file using an InputStream
			File localFile = new File(localFileLocation + uniqueID + ".pdf");
			String remoteFileName = uniqueID + ".pdf";
			OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(localFile));
			boolean success = ftpClient.retrieveFile(remoteFileName, outputStream);
			outputStream.close();

			if (success) {
				System.out.println("File #1 has been downloaded successfully.");
			}

		} catch (IOException ex) {
			System.out.println("Error: " + ex.getMessage());
			ex.printStackTrace();
		} finally {
			try {
				if (ftpClient.isConnected()) {
					ftpClient.logout();
					ftpClient.disconnect();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

}