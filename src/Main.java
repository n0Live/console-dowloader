import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
	private enum AllowedArgs {
		n, l, f, o
	}
	
	/**
	 * Если нужны логи, поставь true
	 */
	public static final boolean isDebug = false;
	
	/**
	 * Перевод строкового значения ограничения скорости с префиксами в числовое
	 * 
	 * @param speedLimitString
	 *            строка ограничение скорости с префиксами
	 * @return числовое значение ограничения скорости, б/с
	 * @throws NumberFormatException
	 */
	private static int parseSpeedLimit(String speedLimitString) throws NumberFormatException {
		int result = 0;
		if (speedLimitString != null) {
			int length = speedLimitString.length();
			switch (speedLimitString.charAt(length - 1)) {
			case 'k':
				result = Integer.parseInt(speedLimitString.substring(0, length - 1)) * 1024;
				break;
			case 'm':
				result = Integer.parseInt(speedLimitString.substring(0, length - 1)) * 1024 * 1024;
				break;
			default:
				result = Integer.parseInt(speedLimitString);
				break;
			}
		}
		return result;
	}
	
	/**
	 * Печать справки по параметрам командной строки
	 */
	private static void printHelp() {
		System.out.println("\n");
		System.out.println("Использование:");
		System.out.println("[-n <число>] [-l <скорость>] -f <имя файла> [-o <путь к папке>]");
		System.out.println("\n");
		System.out.println("Параметры:");
		System.out.println("-n <число>		количество одновременно качающих потоков (1,2,3,4....)");
		System.out.println("-l <скорость>		общее ограничение на скорость скачивания");
		System.out.println("			для всех потоков, размерность - байт/секунда,");
		System.out.println("			можно использовать суффиксы k, m (k=1024, m=1024*1024)");
		System.out.println("-f <имя файла>		путь к файлу со списком ссылок");
		System.out.println("-o <путь к папке>	имя папки, куда складывать скачанные файлы");
	}
	
	/**
	 * @param args
	 *            параметры командной строки
	 */
	public static void main(String[] args) {
		int threadsCount = 0;
		int speedLimit = 0;
		String outputFolder = "";
		String inputFileName = "";
		
		Logger.getGlobal().setLevel(isDebug ? Level.ALL : Level.OFF);
		
		if (args.length > 1) {
			int i = 0;
			while (i < args.length - 1) {
				String arg = args[i];
				if (arg.charAt(0) == '-') {
					try {
						AllowedArgs value = AllowedArgs.valueOf(String.valueOf(arg.charAt(1)));
						switch (value) {
						case n:
							threadsCount = Integer.parseInt(args[++i]);
							break;
						case l:
							speedLimit = parseSpeedLimit(args[++i]);
							break;
						case f:
							inputFileName = args[++i];
							break;
						case o:
							outputFolder = args[++i];
							break;
						default:
							break;
						}
					} catch (IllegalArgumentException e) {
						System.err.println("ОШИБКА: Недопустимое значение \"" + arg + "\": "
						        + e.getLocalizedMessage());
						printHelp();
						return;
					}
				}
				i++;
			}
			if (inputFileName.isEmpty()) {
				System.err.println("ОШИБКА: Отсутствует обязательный параметр \"-f <имя файла>\"");
				printHelp();
				return;
			}
			new DownloadManager(threadsCount, speedLimit, inputFileName, outputFolder).startWork();
			return;
		}
		printHelp();
	}
	
}
