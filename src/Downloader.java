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
import java.util.logging.Logger;

public class Downloader implements Runnable {
	/**
	 * ������ ������ ��� ����������
	 */
	public final static int BUF_SIZE = 1024 * 4;
	
	private final URL url;
	private final Set<String> files;
	
	/**
	 * ��������� ������� ������ ������ ����
	 */
	private final DownloadProcessListener listener;
	
	/**
	 * ����� ��� ����������
	 */
	private final byte[] buf;
	
	/**
	 * ���������, ���������� ���������� ����������� ���� � Future
	 * 
	 * @param urlToRead
	 *            ������ ��� ����������
	 * @param files
	 *            ����� ������ ��� ���������� ����������� ������
	 * @param listener
	 *            ��������� ������� ������ ������ ����
	 * @throws MalformedURLException
	 */
	public Downloader(String urlToRead, Set<String> files, DownloadProcessListener listener)
	        throws MalformedURLException {
		url = new URL(urlToRead);
		this.files = files;
		this.listener = listener;
		buf = new byte[BUF_SIZE];
	}
	
	private void fireChunkReaded(int chunkSize) {
		DownloadEvent event = new DownloadEvent(this, chunkSize);
		listener.chunkReaded(event);
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
	 * ������ ������ � ������ ����������� �������� ����������
	 * 
	 * @param in
	 *            �������� �����
	 * @param out
	 *            ��������� �����
	 * @return {@code false} - ���� ��������� ��� ���������� ��������� ������
	 * @throws IOException
	 */
	private boolean limitedRead(InputStream in, OutputStream out) throws IOException {
		// �������� �����
		if (SpeedManager.takePauseFlag) return true;
		
		int result = -1;
		result = in.read(buf);
		if (result >= 0) {
			out.write(buf, 0, result);
			fireChunkReaded(result);
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
	public void run() {
		try {
			getFileFromHttp();
		} catch (Exception e) {
			throw new RuntimeException(e.getLocalizedMessage() + "\nURL: " + url.toString(), e);
		}
	}
}
