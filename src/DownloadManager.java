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
	 * ���������� �������
	 */
	private final int threadsCount;
	/**
	 * ����������� ������������ �������� �� ��� ������
	 */
	private final int globalSpeedLimit;
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
	 * ���������� ��������� ����
	 */
	private long downloadedBytes;
	
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
		globalSpeedLimit = speedLimit;
		
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
	 * @param start
	 *            ������� ������� ������ ����������
	 * @param done
	 *            ������� ������� ���������� ����������
	 * @param errorsCount
	 *            ���������� ������, ��������� �� ����� ������
	 */
	private void showResults(long start, long done, int errorsCount) {
		// ����������� ���������� ��������� ����
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
		
		// ����������� ����� ������ ���������
		long elapsedTime = done - start;
		String workedTime = String.format(
		        "%d ���, %d ���",
		        TimeUnit.MILLISECONDS.toMinutes(elapsedTime),
		        TimeUnit.MILLISECONDS.toSeconds(elapsedTime)
		                - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsedTime)));
		
		// ����������� ������� �������� ����������
		String avgSpeed = String.format(Locale.ENGLISH, "%.2f ����/c", downloadedBytes
		        / ((float) elapsedTime / 1000));
		
		if (errorsCount > 0) {
			System.err.println("�� ����� ������ ��������� ������: " + errorsCount);
		}
		
		System.out.println("������� ����: " + sDownloadedBytes);
		System.out.println("����� ������: " + workedTime);
		System.out.println("������� ��������: " + avgSpeed);
	}
	
	/**
	 * ������ �������
	 */
	public void startWork() {
		ArrayList<Future<Integer>> futures = new ArrayList<>();
		int errorsCount = 0; // ������� ������
		// ����������� ����������� �������� ��� ������ ������
		int oneThreadSpeedLimit = Math.round((float) globalSpeedLimit / threadsCount);
		
		// ������� ������� ������ ����������
		long workStartedTimestamp = System.currentTimeMillis();
		
		for (String url : urls.keySet()) {
			try {
				futures.add(downloadPool.submit(new Downloader(url, urls.get(url),
				        oneThreadSpeedLimit)));
			} catch (MalformedURLException e) {
				System.err.println("������: " + e.getLocalizedMessage());
				errorsCount++;
			}
		}
		
		// ������� ����������� �����
		for (Future<Integer> future : futures) {
			try {
				int bytesRead = future.get();
				downloadedBytes += bytesRead;
			} catch (ExecutionException e) {
				System.err.println("������: " + e.getLocalizedMessage());
				errorsCount++;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		// ������� ������� ���������� ����������
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
