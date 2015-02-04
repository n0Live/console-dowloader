import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DownloadManager {
	/**
	 * Количество потоков
	 */
	private final int threadsCount;
	/**
	 * Ограничение максимальной скорости на все потоки
	 */
	private final int globalSpeedLimit;
	/**
	 * Папка для сохранения скачанных файлов
	 */
	private final String outputFolder;
	/**
	 * Ссылки для скачивания и сопоставленные им имена файлов
	 */
	private final Map<String, Set<String>> urls;
	/**
	 * Пул потоков скачивания
	 */
	private final ExecutorService downloadPool;
	/**
	 * Количество скачанных байт
	 */
	private long downloadedBytes;
	
	/**
	 * Менеджер загрузок
	 * 
	 * @param threadsCount
	 *            количество потоков
	 * @param speedLimit
	 *            ограничение максимальной скорости скачивания
	 * @param inputFileName
	 *            путь к файлу со списком ссылок
	 * @param outputFolder
	 *            имя папки для сохранения скачанных файлов
	 */
	public DownloadManager(int threadsCount, int speedLimit, String inputFileName,
	        String outputFolder) {
		globalSpeedLimit = speedLimit;
		
		// если папка не задана, сохраняем в текущую рабочую папку пользователя
		if (outputFolder.isEmpty()) {
			this.outputFolder = System.getProperty("user.dir");
		} else {
			this.outputFolder = outputFolder;
		}
		// создаем папку при необходимости
		new File(outputFolder).mkdirs();
		
		urls = parseLinks(new File(inputFileName));
		
		// минимум из заданного количества потоков и количества ссылок в файле,
		// но хотя бы один поток
		this.threadsCount = Math.max(Math.min(threadsCount, urls.size()), 1);
		
		downloadPool = Executors.newFixedThreadPool(this.threadsCount);
	}
	
	/**
	 * Получение ссылок и имен файлов из указанного файла
	 * 
	 * @param file
	 *            файл со списком ссылок
	 * @return ссылки для скачивания и сопоставленные им имена файлов
	 */
	private Map<String, Set<String>> parseLinks(File file) {
		Map<String, Set<String>> result = new HashMap<>();
		try (FileInputStream in = new FileInputStream(file)) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "Cp1251"))) {
				String line;
				while ((line = reader.readLine()) != null) {
					String[] values = line.split(" ");
					String URL = values[0];
					String fileName = values[1];
					
					Set<String> filesSet = result.get(URL);
					if (filesSet == null) {
						filesSet = new HashSet<>();
					}
					// добавляем к имени файла путь к папке для сохранения
					filesSet.add(outputFolder + File.separator + fileName);
					result.put(URL, filesSet);
				}
			}
		} catch (IOException e) {
			System.err.println("ОШИБКА: " + e.getLocalizedMessage());
		}
		return result;
	}
	
	/**
	 * Отчет по работе программы
	 * 
	 * @param start
	 *            отметка времени начала скачивания
	 * @param done
	 *            отметка времени завершения скачивания
	 * @param errorsCount
	 *            количество ошибок, возникших во время работы
	 */
	private void showResults(long start, long done, int errorsCount) {
		// форматируем количество скачанных байт
		String sDownloadedBytes;
		if (downloadedBytes > 1024) {
			if (downloadedBytes > 1024 * 1024) {
				sDownloadedBytes = String.format(Locale.ENGLISH, "%.2fm", (float) downloadedBytes
				        / (1024 * 1024));
			} else {
				sDownloadedBytes = String.format(Locale.ENGLISH, "%.2fk",
				        (float) downloadedBytes / 1024);
			}
		} else {
			sDownloadedBytes = String.valueOf(downloadedBytes);
		}
		
		// форматируем время работы программы
		long elapsedTime = done - start;
		String workedTime = String.format(
		        "%d мин, %d сек",
		        TimeUnit.MILLISECONDS.toMinutes(elapsedTime),
		        TimeUnit.MILLISECONDS.toSeconds(elapsedTime)
		                - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsedTime)));
		
		// форматируем среднюю скорость скачивания
		String avgSpeed = String.format(Locale.ENGLISH, "%.2f байт/c", downloadedBytes
		        / ((float) elapsedTime / 1000));
		
		if (errorsCount > 0) {
			System.err.println("Во время работы произошли ошибки: " + errorsCount);
		}
		
		System.out.println("Скачано байт: " + sDownloadedBytes);
		System.out.println("Время работы: " + workedTime);
		System.out.println("Средняя скорость: " + avgSpeed);
	}
	
	/**
	 * Запуск закачек
	 */
	public void startWork() {
		ArrayList<Future<Integer>> futures = new ArrayList<>();
		int errorsCount = 0; // счетчик ошибок
		// расчитываем ограничения скорости для одного потока
		int oneThreadSpeedLimit = Math.round((float) globalSpeedLimit / threadsCount);
		
		// отметка времени начала скачивания
		long workStartedTimestamp = System.currentTimeMillis();
		
		for (String url : urls.keySet()) {
			try {
				futures.add(downloadPool.submit(new Downloader(url, urls.get(url),
				        oneThreadSpeedLimit)));
			} catch (MalformedURLException e) {
				System.err.println("ОШИБКА: " + e.getLocalizedMessage());
				errorsCount++;
			}
		}
		
		// считаем прочитанные байты
		for (Future<Integer> future : futures) {
			try {
				int bytesRead = future.get();
				downloadedBytes += bytesRead;
			} catch (ExecutionException e) {
				System.err.println("ОШИБКА: " + e.getLocalizedMessage());
				errorsCount++;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		// отметка времени завершения скачивания
		long workDoneTimestamp = System.currentTimeMillis();
		
		downloadPool.shutdown();
		try {
			downloadPool.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		showResults(workStartedTimestamp, workDoneTimestamp, errorsCount);
	}
}
