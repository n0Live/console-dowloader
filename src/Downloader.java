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
	 * Размер буфера для скачивания
	 */
	public final static int BUF_SIZE = 1024 * 4;
	
	private final URL url;
	private final Set<String> files;
	
	/**
	 * Слушатель события чтения порции байт
	 */
	private final DownloadProcessListener listener;
	
	/**
	 * Буфер для скачивания
	 */
	private final byte[] buf;
	
	/**
	 * Загрузчик, возвращает количество прочитанных байт в Future
	 * 
	 * @param urlToRead
	 *            ссылка для скачивания
	 * @param files
	 *            имена файлов для сохранения содержимого ссылки
	 * @param listener
	 *            слушатель событий чтения порции байт
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
	 * Основной метод для скачивания ссылки и сохранения ее содержимого в файлы
	 * 
	 * @throws IOException
	 */
	private void getFileFromHttp() throws IOException {
		HttpURLConnection connection;
		
		connection = (HttpURLConnection) url.openConnection();
		connection.setReadTimeout(0);
		int responseCode = connection.getResponseCode();
		
		// проверяем ответ сервера
		if (responseCode == HttpURLConnection.HTTP_OK) {
			try (InputStream in = connection.getInputStream()) {
				try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
					// читаем поток в ByteArrayOutputStream
					for (;;) {
						if (!limitedRead(in, out)) {
							break;
						}
					}
					// сохраняем ByteArrayOutputStream в файлы
					for (String fileName : files) {
						try {
							saveToFile(out, fileName);
						} catch (IOException e) {
							System.err.println("ОШИБКА: " + e.getLocalizedMessage() + "\nURL: "
							        + url.toString() + "\nФайл: " + fileName);
						}
					}
				}
			}
		} else throw new RuntimeException("Ошибка соединения: " + responseCode);
	}
	
	/**
	 * Чтение потока с учетом ограничения скорости скачивания
	 * 
	 * @param in
	 *            входящий поток
	 * @param out
	 *            выходящий поток
	 * @return {@code false} - если прочитано все содержимое входящего потока
	 * @throws IOException
	 */
	private boolean limitedRead(InputStream in, OutputStream out) throws IOException {
		// проверка флага
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
	 * Сохранение ByteArray-потока в файл
	 * 
	 * @param bos
	 *            ByteArray-поток с данными
	 * @param fileName
	 *            имя файла для сохранения
	 * @throws IOException
	 */
	private void saveToFile(ByteArrayOutputStream bos, String fileName) throws IOException {
		File file = new File(fileName);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			bos.writeTo(fos);
			if (Main.isDebug) {
				Logger.getGlobal().info("Файл сохранен: " + file.getCanonicalPath());
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
