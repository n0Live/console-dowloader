import java.util.EventListener;

public interface DownloadProcessListener extends EventListener {
	
	/**
	 * ��������� ������ ����
	 * 
	 * @param event
	 */
	public void chunkReaded(DownloadEvent event);
	
}
