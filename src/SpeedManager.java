import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class SpeedManager implements Callable<Long[]>, DownloadProcessListener {
	/**
	 * Отметка времени начала закачек
	 */
	private final long initTimeStamp;
	/**
	 * Лимит скорости
	 */
	private final int speedLimit;
	/**
	 * Всего скачано, байт
	 */
	private volatile AtomicLong totalBytesRead;
	
	/**
	 * Флаг паузы закачки для качающих потоков если превышена скорость
	 * скачивания
	 */
	public static volatile boolean takePauseFlag = false;
	/**
	 * Флаг завершения всех качающих потоков
	 */
	public static volatile boolean downloadCompleteFlag = false;
	
	/**
	 * Менеджер закачек с учетом ограничения скорости
	 * 
	 * @param speedLimit
	 *            ограничение скорости, 0 - неограничено
	 */
	public SpeedManager(int speedLimit) {
		this.speedLimit = speedLimit;
		initTimeStamp = System.currentTimeMillis();
		totalBytesRead = new AtomicLong(0);
	}
	
	@Override
	/**
	 * @returns массив Long[2]
	 * <p>
	 * 1й параметр - размер прочитанных байт
	 * <br>
	 * 2й параметр - время работы
	 */
	public Long[] call() throws Exception {
		int currentSpeed = 0;
		long now = initTimeStamp;
		
		while (!downloadCompleteFlag) {
			now = System.currentTimeMillis();
			currentSpeed = Math
			        .round(totalBytesRead.get() / ((float) (now - initTimeStamp) / 1000));
			
			// выставляем флаг при превышении скорости закачки
			takePauseFlag = speedLimit > 0 && currentSpeed > speedLimit;
			
			if (Main.isDebug) {
				Logger.getGlobal().info("Скорость: " + currentSpeed + " | Лимит: " + speedLimit);
			}
		}
		
		// выравнивание скорости после сбрасывания буферов последних потоков
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
