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
	 * Размер буфера для скачивания
	 */
	private final int BUF_SIZE = 1024 * 4;
	
	private final URL url;
	private final int speedLimit;
	private final Set<String> files;
	
	/**
	 * Буфер для скачивания
	 */
	private final byte[] buf;
	/**
	 * Период измерения скорости скачивания
	 */
	private final int interval;// миллисекунд
	/**
	 * Отметка времени перед началом скачивания
	 */
	private final long initTimestamp;
	/**
	 * Количество прочитанных байт
	 */
	private int bytesRead;
	
	/**
	 * Загрузчик, возвращает количество прочитанных байт в Future
	 * 
	 * @param urlToRead
	 *            ссылка для скачивания
	 * @param files
	 *            имена файлов для сохранения содержимого ссылки
	 * @param speedLimit
	 *            максимальная скорость скачивания, байт/с. 0 - не ограничено.
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
	 * Чтение потока с учетом ограничения максимальной скорости скачивания
	 * 
	 * @param in
	 *            входящий поток
	 * @param out
	 *            выходящий поток
	 * @return {@code false} - если прочитано все содержимое входящего потока
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
			
			// если чтение закончилось быстрее заданного интервала
			if (now - lastCheckTimestamp < interval) {
				// спим до конца интервала
				long timeToSleep = interval - (now - lastCheckTimestamp);
				try {
					Thread.sleep(timeToSleep);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			if (Main.isDebug) {
				// вычисляем текущую скорость
				long timeElapsed = System.currentTimeMillis() - initTimestamp;
				int currentSpeed = Math.round(bytesRead / ((float) timeElapsed / 1000));
				Logger.getGlobal().info(
				        "URL: " + url.toString() + " | Скорость: " + currentSpeed + " | Лимит: "
				                + speedLimit);
			}
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
	public Integer call() throws Exception {
		try {
			getFileFromHttp();
		} catch (Exception e) {
			throw new RuntimeException(e.getLocalizedMessage() + "\nURL: " + url.toString(), e);
		}
		return bytesRead;
	}
}
