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
	 * ���������� �������
	 */
	private final int threadsCount;
	/**
	 * ����������� ������������ �������� �� ��� ������
	 */
	private final int speedLimit;
	/**
	 * ����� ��� ���������� ��������� ������
	 */
	private final String outputFolder;
	/**
	 * ������ ��� ���������� � �������������� �� ����� ������
	 */
	private final Map<String, Set<String>> urls;
	/**
	 * ��� ������� ����������
	 */
	private final ExecutorService downloadPool;
	
	/**
	 * �������� ��������
	 * 
	 * @param threadsCount
	 *            ���������� �������
	 * @param speedLimit
	 *            ����������� ������������ �������� ����������
	 * @param inputFileName
	 *            ���� � ����� �� ������� ������
	 * @param outputFolder
	 *            ��� ����� ��� ���������� ��������� ������
	 */
	public DownloadManager(int threadsCount, int speedLimit, String inputFileName,
	        String outputFolder) {
		this.speedLimit = speedLimit;
		
		// ���� ����� �� ������, ��������� � ������� ������� ����� ������������
		if (outputFolder.isEmpty()) {
			this.outputFolder = System.getProperty("user.dir");
		} else {
			this.outputFolder = outputFolder;
		}
		// ������� ����� ��� �������������
		new File(outputFolder).mkdirs();
		
		urls = parseLinks(new File(inputFileName));
		
		// ������� �� ��������� ���������� ������� � ���������� ������ � �����,
		// �� ���� �� ���� �����
		this.threadsCount = Math.max(Math.min(threadsCount, urls.size()), 1);
		
		downloadPool = Executors.newFixedThreadPool(this.threadsCount);
	}
	
	/**
	 * ��������� ������ � ���� ������ �� ���������� �����
	 * 
	 * @param file
	 *            ���� �� ������� ������
	 * @return ������ ��� ���������� � �������������� �� ����� ������
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
					// ��������� � ����� ����� ���� � ����� ��� ����������
					filesSet.add(outputFolder + File.separator + fileName);
					result.put(URL, filesSet);
				}
			}
		} catch (IOException e) {
			System.err.println("������: " + e.getLocalizedMessage());
		}
		return result;
	}
	
	/**
	 * ����� �� ������ ���������
	 * 
	 * @param result
	 *            ���������� ������: ���������� ��������� ���� � ����� �������
	 *            (Long[2])
	 * @param errorsCount
	 *            ���������� ������, ��������� �� ����� ������
	 */
	private void showResults(Long[] result, int errorsCount) {
		try {
			// ����������� ���������� ��������� ����
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
			
			// ����������� ����� ������ ���������
			long elapsedTime = result[1];
			String workedTime = String.format(
			        "%d ���, %d ���",
			        TimeUnit.MILLISECONDS.toMinutes(elapsedTime),
			        TimeUnit.MILLISECONDS.toSeconds(elapsedTime)
			                - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
			                        .toMinutes(elapsedTime)));
			
			// ����������� ������� �������� ����������
			String avgSpeed = String.format(Locale.ENGLISH, "%.2f ����/c", downloadedBytes
			        / ((float) elapsedTime / 1000));
			
			System.out.println("������� ����: " + sDownloadedBytes);
			System.out.println("����� ������: " + workedTime);
			System.out.print("������� ��������: " + avgSpeed);
			System.out.println(speedLimit > 0 ? " (�����������: " + speedLimit + " ����/c)" : "");
			
		} catch (Exception e) {
			System.err.println("������: " + e.getLocalizedMessage());
			errorsCount++;
		}
		
		if (errorsCount > 0) {
			System.err.println("�� ����� ������ ��������� ������: " + errorsCount);
		}
		
	}
	
	/**
	 * ������ �������
	 */
	public void startWork() {
		int errorsCount = 0; // ������� ������
		
		// �������� ����������� �������� �������
		SpeedManager speedManager = new SpeedManager(speedLimit);
		ExecutorService speedManagerThread = Executors.newSingleThreadExecutor();
		Future<Long[]> future = speedManagerThread.submit(speedManager);
		
		for (String url : urls.keySet()) {
			try {
				downloadPool.execute(new Downloader(url, urls.get(url), speedManager));
			} catch (MalformedURLException e) {
				System.err.println("������: " + e.getLocalizedMessage());
				errorsCount++;
			}
		}
		downloadPool.shutdown();
		
		try {
			downloadPool.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// ���������� ���� ���������� ������ ��������� �������
		SpeedManager.downloadCompleteFlag = true;
		speedManagerThread.shutdown();
		
		// ���������� ������: ���������� ��������� ���� � ����� �������
		Long[] result = null;
		try {
			result = future.get();
		} catch (ExecutionException e) {
			System.err.println("������: " + e.getLocalizedMessage());
			errorsCount++;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		showResults(result, errorsCount);
	}
}
