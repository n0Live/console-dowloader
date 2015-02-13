import java.util.EventListener;

public interface DownloadProcessListener extends EventListener {
	
	/**
	 * Прочитана порция байт
	 * 
	 * @param event
	 */
	public void chunkReaded(DownloadEvent event);
	
}
