import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class Downloader implements Callable<Integer> {
	
	/**
	 * ������ ������ ��� ����������
	 */
	private final int BUF_SIZE = 1024 * 4;
	
	private final URL url;
	private final int speedLimit;
	private final Set<String> files;
	
	/**
	 * ����� ��� ����������
	 */
	private final byte[] buf;
	/**
	 * ������ ��������� �������� ����������
	 */
	private final int interval;// �����������
	/**
	 * ������� ������� ����� ������� ����������
	 */
	private final long initTimestamp;
	/**
	 * ���������� ����������� ����
	 */
	private int bytesRead;
	
	/**
	 * ���������, ���������� ���������� ����������� ���� � Future
	 * 
	 * @param urlToRead
	 *            ������ ��� ����������
	 * @param files
	 *            ����� ������ ��� ���������� ����������� ������
	 * @param speedLimit
	 *            ������������ �������� ����������, ����/�. 0 - �� ����������.
	 * @throws MalformedURLException
	 */
	public Downloader(String urlToRead, Set<String> files, int speedLimit)
	        throws MalformedURLException {
		url = new URL(urlToRead);
		this.files = files;
		this.speedLimit = speedLimit;
		buf = new byte[BUF_SIZE];
		interval = (int) (BUF_SIZE / ((float) this.speedLimit / 1000));
		initTimestamp = System.currentTimeMillis();
	}
	
	/**
	 * �������� ����� ��� ���������� ������ � ���������� �� ����������� � �����
	 * 
	 * @throws IOException
	 */
	private void getFileFromHttp() throws IOException {
		HttpURLConnection connection;
		
		connection = (HttpURLConnection) url.openConnection();
		connection.setReadTimeout(0);
		int responseCode = connection.getResponseCode();
		
		// ��������� ����� �������
		if (responseCode == HttpURLConnection.HTTP_OK) {
			try (InputStream in = connection.getInputStream()) {
				try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
					// ������ ����� � ByteArrayOutputStream
					for (;;) {
						if (!limitedRead(in, out)) {
							break;
						}
					}
					// ��������� ByteArrayOutputStream � �����
					for (String fileName : files) {
						try {
							saveToFile(out, fileName);
						} catch (IOException e) {
							System.err.println("������: " + e.getLocalizedMessage() + "\nURL: "
							        + url.toString() + "\n����: " + fileName);
						}
					}
				}
			}
		} else throw new RuntimeException("������ ����������: " + responseCode);
	}
	
	/**
	 * ������ ������ � ������ ����������� ������������ �������� ����������
	 * 
	 * @param in
	 *            �������� �����
	 * @param out
	 *            ��������� �����
	 * @return {@code false} - ���� ��������� ��� ���������� ��������� ������
	 * @throws IOException
	 */
	private boolean limitedRead(InputStream in, OutputStream out) throws IOException {
		long lastCheckTimestamp = 0;
		if (speedLimit > 0) {
			lastCheckTimestamp = System.currentTimeMillis();
		}
		
		int result = -1;
		result = in.read(buf);
		if (result >= 0) {
			out.write(buf, 0, result);
			bytesRead += result;
		}
		
		if (speedLimit > 0) {
			long now = System.currentTimeMillis();
			
			// ���� ������ ����������� ������� ��������� ���������
			if (now - lastCheckTimestamp < interval) {
				// ���� �� ����� ���������
				long timeToSleep = interval - (now - lastCheckTimestamp);
				try {
					Thread.sleep(timeToSleep);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			if (Main.isDebug) {
				// ��������� ������� ��������
				long timeElapsed = System.currentTimeMillis() - initTimestamp;
				int currentSpeed = Math.round(bytesRead / ((float) timeElapsed / 1000));
				Logger.getGlobal().info(
				        "URL: " + url.toString() + " | ��������: " + currentSpeed + " | �����: "
				                + speedLimit);
			}
		}
		
		return result >= 0;
	}
	
	/**
	 * ���������� ByteArray-������ � ����
	 * 
	 * @param bos
	 *            ByteArray-����� � �������
	 * @param fileName
	 *            ��� ����� ��� ����������
	 * @throws IOException
	 */
	private void saveToFile(ByteArrayOutputStream bos, String fileName) throws IOException {
		File file = new File(fileName);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			bos.writeTo(fos);
			if (Main.isDebug) {
				Logger.getGlobal().info("���� ��������: " + file.getCanonicalPath());
			}
		}
	}
	
	@Override
	public Integer call() throws Exception {
		try {
			getFileFromHttp();
		} catch (Exception e) {
			throw new RuntimeException(e.getLocalizedMessage() + "\nURL: " + url.toString(), e);
		}
		return bytesRead;
	}
}
