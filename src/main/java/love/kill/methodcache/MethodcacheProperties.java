package love.kill.methodcache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

@ConfigurationProperties(prefix = "methodcache")
public class MethodcacheProperties {

	/**
	 * 开启缓存
	 * */
	private boolean enable = false;

	/**
	 * 开启日志
	 * */
	private boolean enableLog = false;

	/**
	 * 切面排序值
	 *
	 * 控制 Advisor 的执行顺序
	 *
	 * */
	private int order = Ordered.LOWEST_PRECEDENCE;


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

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}
}
