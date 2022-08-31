package love.kill.methodcache;

import love.kill.methodcache.util.ThreadPoolBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 内存监控
 *
 * @author Lycop
 */
public class MemoryMonitor {

	private static Logger logger = LoggerFactory.getLogger(MemoryMonitor.class);

	/**
	 * 内存信息
	 */
	private final static MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

	/**
	 * 内存池-老年代
	 */
	private final static String memoryPoolMXBeanOldGen = "old gen";

	/**
	 * 内存告警阈值
	 */
	private final double memoryThreshold;

	/**
	 * GC阈值
	 */
	private final double gcThreshold;

	/**
	 * 内存溢出订阅者
	 */
	private final static List<Consumer<MemoryUsage>> subscribers = new CopyOnWriteArrayList<>();

	/**
	 * 执行线程
	 */
	private static final ExecutorService executorService = ThreadPoolBuilder.buildDefaultThreadPool();

	public MemoryMonitor(MethodcacheProperties methodcacheProperties) {

		logger.info("开启内存监控...");
		this.gcThreshold = new BigDecimal(methodcacheProperties.getGcThreshold()).divide(new BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
		this.memoryThreshold = new BigDecimal(methodcacheProperties.getMemoryThreshold()).divide(new BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
		logger.info("内存告警阈值=" + memoryThreshold + "，GC阈值=" + gcThreshold);
		setUsageThreshold();
		NotificationEmitter ne = (NotificationEmitter) memBean;
		ne.addNotificationListener((notification, handback) -> {
			Object userData = notification.getUserData();
			if (userData instanceof CompositeData) {
				CompositeData cd = (CompositeData) notification.getUserData();
				MemoryNotificationInfo mni = MemoryNotificationInfo.from(cd);
				MemoryUsage memUsage = mni.getUsage();
				String poolName = mni.getPoolName();
				long used = memUsage.getUsed();
				long max = memUsage.getMax();

				if (poolName.toLowerCase().endsWith(memoryPoolMXBeanOldGen) && isAlarmed(used, max, memoryThreshold)) {
					if (subscribers.size() > 0) {
						for (Consumer<MemoryUsage> sub : subscribers) {
							executorService.execute(() -> {
								sub.accept(memUsage);
							});
						}
					}
				}
			}
		}, null, null);
	}

	/**
	 * 设置内存溢出通知阈值
	 */
	private void setUsageThreshold() {
		List<MemoryPoolMXBean> memPools = ManagementFactory.getMemoryPoolMXBeans();
		if (memPools == null) {
			return;
		}

		for (MemoryPoolMXBean mp : memPools) {
			String mpName = mp.getName();
			if (mpName.toLowerCase().endsWith(memoryPoolMXBeanOldGen) && mp.isUsageThresholdSupported()) {
				MemoryUsage usage = mp.getUsage();
				long max = usage.getMax();
				mp.setUsageThreshold(new BigDecimal(max).multiply(new BigDecimal(memoryThreshold)).longValue());
			}
		}
	}

	/**
	 * 触发警告
	 *
	 * @param used   已用内存
	 * @param limit  上限
	 * @param cordon 警戒线
	 */
	private boolean isAlarmed(long used, long limit, double cordon) {
		BigDecimal bigUsed = new BigDecimal(used);
		BigDecimal bigLimit = new BigDecimal(limit);
		return bigUsed.compareTo(bigLimit.multiply(new BigDecimal(cordon))) >= 0;
	}

	/**
	 * 订阅内存告警
	 *
	 * @param subscriber 订阅者
	 */
	public void sub(Consumer<MemoryUsage> subscriber) {
		subscribers.add(subscriber);
	}
}
