package love.kill.methodcache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

@ConfigurationProperties(prefix = "methodcache")
public class MethodcacheProperties {

	/**
	 * 名称
	 * */
	private String name;

	/**
	 * 开启缓存
	 * */
	private boolean enable = false;

	/**
	 * 开启日志
	 * */
	private boolean enableLog = false;

	/**
	 * 开启端点信息
	 * */
	private boolean enableEndpoint = false;

	/**
	 * 开启统计
	 * */
	private boolean enableRecord = false;

	/**
	 * 开启内存监控
	 * */
	private boolean enableMemoryMonitor = true;

	/**
	 * 内存告警阈值
	 * 百分比，取值范围：(0, 100)，默认：50(%)
	 * */
	private int memoryThreshold = 50;

	/**
	 * GC阈值
	 * 百分比，取值范围：(0, 100)，默认：50(%)
	 * */
	private int gcThreshold = 50;

	/**
	 * 切面排序值
	 *
	 * 控制 Advisor 的执行顺序
	 *
	 * */
	private int order = Ordered.LOWEST_PRECEDENCE;


	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isEnable() {
		return enable;
	}

	public void setEnable(boolean enable) {
		this.enable = enable;
	}

	public boolean isEnableLog() {
		return enableLog;
	}

	public void setEnableLog(boolean enableLog) {
		this.enableLog = enableLog;
	}

	public boolean isEnableEndpoint() {
		return enableEndpoint;
	}

	public void setEnableEndpoint(boolean enableEndpoint) {
		this.enableEndpoint = enableEndpoint;
	}

	public boolean isEnableRecord() {
		return enableRecord;
	}

	public void setEnableRecord(boolean enableRecord) {
		this.enableRecord = enableRecord;
	}

	public boolean isEnableMemoryMonitor() {
		return enableMemoryMonitor;
	}

	public void setEnableMemoryMonitor(boolean enableMemoryMonitor) {
		this.enableMemoryMonitor = enableMemoryMonitor;
	}

	public int getMemoryThreshold() {
		return memoryThreshold;
	}

	public void setMemoryThreshold(int memoryThreshold) {
		this.memoryThreshold = memoryThreshold;
	}

	public int getGcThreshold() {
		return gcThreshold;
	}

	public void setGcThreshold(int gcThreshold) {
		this.gcThreshold = gcThreshold;
	}

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public String toString() {
		return "MethodcacheProperties{" +
				"name='" + name + '\'' +
				", enable=" + enable +
				", enableLog=" + enableLog +
				", enableEndpoint=" + enableEndpoint +
				", enableRecord=" + enableRecord +
				", enableMemoryMonitor=" + enableMemoryMonitor +
				", gcThreshold=" + gcThreshold +
				", order=" + order +
				'}';
	}
}
