import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class SpeedManager implements Callable<Long[]>, DownloadProcessListener {
	/**
	 * ������� ������� ������ �������
	 */
	private final long initTimeStamp;
	/**
	 * ����� ��������
	 */
	private final int speedLimit;
	/**
	 * ����� �������, ����
	 */
	private volatile AtomicLong totalBytesRead;
	
	/**
	 * ���� ����� ������� ��� �������� ������� ���� ��������� ��������
	 * ����������
	 */
	public static volatile boolean takePauseFlag = false;
	/**
	 * ���� ���������� ���� �������� �������
	 */
	public static volatile boolean downloadCompleteFlag = false;
	
	/**
	 * �������� ������� � ������ ����������� ��������
	 * 
	 * @param speedLimit
	 *            ����������� ��������, 0 - ������������
	 */
	public SpeedManager(int speedLimit) {
		this.speedLimit = speedLimit;
		initTimeStamp = System.currentTimeMillis();
		totalBytesRead = new AtomicLong(0);
	}
	
	@Override
	/**
	 * @returns ������ Long[2]
	 * <p>
	 * 1� �������� - ������ ����������� ����
	 * <br>
	 * 2� �������� - ����� ������
	 */
	public Long[] call() throws Exception {
		int currentSpeed = 0;
		long now = initTimeStamp;
		
		while (!downloadCompleteFlag) {
			now = System.currentTimeMillis();
			currentSpeed = Math
			        .round(totalBytesRead.get() / ((float) (now - initTimeStamp) / 1000));
			
			// ���������� ���� ��� ���������� �������� �������
			takePauseFlag = speedLimit > 0 && currentSpeed > speedLimit;
			
			if (Main.isDebug) {
				Logger.getGlobal().info("��������: " + currentSpeed + " | �����: " + speedLimit);
			}
		}
		
		// ������������ �������� ����� ����������� ������� ��������� �������
		if (speedLimit > 0 && currentSpeed > speedLimit) {
			int timeToSleep = Math.round((float) (currentSpeed - speedLimit) / speedLimit
			        * (now - initTimeStamp));
			Thread.sleep(timeToSleep);
		}
		
		Long[] result = { totalBytesRead.get(), System.currentTimeMillis() - initTimeStamp };
		return result;
	}
	
	@Override
	public void chunkReaded(DownloadEvent event) {
		totalBytesRead.addAndGet(event.getChunkSize());
	}
}
