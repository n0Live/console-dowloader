import java.util.EventObject;

public class DownloadEvent extends EventObject {
	/**
	 * ������ ����������� ������ ����
	 */
	private final int chunkSize;
	
	/**
	 * ������� ������ ������ ����
	 * 
	 * @param source
	 *            �������� �������
	 * @param chunkSize
	 *            ������ ����������� ������ ����
	 */
	public DownloadEvent(Object source, int chunkSize) {
		super(source);
		this.chunkSize = chunkSize;
	}
	
	public int getChunkSize() {
		return chunkSize;
	}
	
}
