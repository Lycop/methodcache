package love.kill.methodcache.util;

/**
 * 线程池构建
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */

import java.util.concurrent.*;

public class ThreadPoolBuilder {

	/**
	 * CPU数量
	 */
	private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
	/**
	 * 核心线程数（CPU核心数 + 1）
	 */
	private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
	/**
	 * 线程池最大线程数（CPU核心数 * 2 + 1）
	 */
	private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;

	/**
	 * 构建默认配置的线程池
	 *
	 * @return 线程池
	 */
	public static ExecutorService buildDefaultThreadPool() {
		return new ThreadPoolExecutor(
				CORE_POOL_SIZE,
				MAXIMUM_POOL_SIZE,
				1000L,
				TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<>());
	}

	/**
	 * 构建单一线程池
	 *
	 * @return 线程池
	 */
	public static ExecutorService buildSingleThreadPool() {
		return Executors.newSingleThreadExecutor();
	}

	/**
	 * 构建固定线程池
	 *
	 * @return 线程池
	 */
	public static ExecutorService buildFixedThreadPool() {
		return Executors.newFixedThreadPool(CORE_POOL_SIZE);
	}
}
