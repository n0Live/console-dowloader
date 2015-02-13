import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
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
	private final int speedLimit;
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
		this.speedLimit = speedLimit;
		
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
	 * @param result
	 *            результаты работы: количество скачанных байт и время закачки
	 *            (Long[2])
	 * @param errorsCount
	 *            количество ошибок, возникших во время работы
	 */
	private void showResults(Long[] result, int errorsCount) {
		try {
			// форматируем количество скачанных байт
			long downloadedBytes = result[0];
			String sDownloadedBytes;
			if (downloadedBytes > 1024) {
				if (downloadedBytes > 1024 * 1024) {
					sDownloadedBytes = String.format(Locale.ENGLISH, "%.2fm",
					        (float) downloadedBytes / (1024 * 1024));
				} else {
					sDownloadedBytes = String.format(Locale.ENGLISH, "%.2fk",
					        (float) downloadedBytes / 1024);
				}
			} else {
				sDownloadedBytes = String.valueOf(downloadedBytes);
			}
			
			// форматируем время работы программы
			long elapsedTime = result[1];
			String workedTime = String.format(
			        "%d мин, %d сек",
			        TimeUnit.MILLISECONDS.toMinutes(elapsedTime),
			        TimeUnit.MILLISECONDS.toSeconds(elapsedTime)
			                - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
			                        .toMinutes(elapsedTime)));
			
			// форматируем среднюю скорость скачивания
			String avgSpeed = String.format(Locale.ENGLISH, "%.2f байт/c", downloadedBytes
			        / ((float) elapsedTime / 1000));
			
			System.out.println("Скачано байт: " + sDownloadedBytes);
			System.out.println("Время работы: " + workedTime);
			System.out.print("Средняя скорость: " + avgSpeed);
			System.out.println(speedLimit > 0 ? " (Ограничение: " + speedLimit + " байт/c)" : "");
			
		} catch (Exception e) {
			System.err.println("ОШИБКА: " + e.getLocalizedMessage());
			errorsCount++;
		}
		
		if (errorsCount > 0) {
			System.err.println("Во время работы произошли ошибки: " + errorsCount);
		}
		
	}
	
	/**
	 * Запуск закачек
	 */
	public void startWork() {
		int errorsCount = 0; // счетчик ошибок
		
		// менеджер ограничения скорости закачек
		SpeedManager speedManager = new SpeedManager(speedLimit);
		ExecutorService speedManagerThread = Executors.newSingleThreadExecutor();
		Future<Long[]> future = speedManagerThread.submit(speedManager);
		
		for (String url : urls.keySet()) {
			try {
				downloadPool.execute(new Downloader(url, urls.get(url), speedManager));
			} catch (MalformedURLException e) {
				System.err.println("ОШИБКА: " + e.getLocalizedMessage());
				errorsCount++;
			}
		}
		downloadPool.shutdown();
		
		try {
			downloadPool.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// выставляем флаг завершения работы менеджеру закачек
		SpeedManager.downloadCompleteFlag = true;
		speedManagerThread.shutdown();
		
		// результаты работы: количество скачанных байт и время закачки
		Long[] result = null;
		try {
			result = future.get();
		} catch (ExecutionException e) {
			System.err.println("ОШИБКА: " + e.getLocalizedMessage());
			errorsCount++;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		showResults(result, errorsCount);
	}
}
