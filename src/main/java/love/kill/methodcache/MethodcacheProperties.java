package love.kill.methodcache;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
