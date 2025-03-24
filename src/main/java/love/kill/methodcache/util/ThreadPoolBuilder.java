/*
 * Copyright 2025 Lycop
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
