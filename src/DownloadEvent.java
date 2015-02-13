import java.util.EventObject;

public class DownloadEvent extends EventObject {
	/**
	 * Размер прочитанной порции байт
	 */
	private final int chunkSize;
	
	/**
	 * Событие чтения порции байт
	 * 
	 * @param source
	 *            источник события
	 * @param chunkSize
	 *            размер прочитанной порции байт
	 */
	public DownloadEvent(Object source, int chunkSize) {
		super(source);
		this.chunkSize = chunkSize;
	}
	
	public int getChunkSize() {
		return chunkSize;
	}
	
}
