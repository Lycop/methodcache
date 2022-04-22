package love.kill.methodcache.datahelper;

import java.io.Serializable;
import java.util.Date;

/**
 * 缓存数据模型
 *
 * @author Lycop
 */
public class CacheDataModel implements Serializable {
	/**
	 * 方法签名
	 * */
	private String methodSignature;

	/**
	 * 入参哈希值
	 * */
	private long argsHashCode;

	/**
	 * 入参
	 * */
	private String args;

	/**
	 * 数据
	 * */
	private Object data;

	/**
	 * 缓存时间
	 * */
	private long cacheTime = new Date().getTime();

	/**
	 * 过期时间（时间戳）
	 * -1 代表永久有效
	 * */
	private long expireTimeStamp = -1L;

	public CacheDataModel(String methodSignature, long argsHashCode, String args, Object data, long expireTimeStamp) {
		this.methodSignature = methodSignature;
		this.argsHashCode = argsHashCode;
		this.args = args;
		this.data = data;
		this.expireTimeStamp = expireTimeStamp;
	}

	public CacheDataModel(String methodSignature, Integer argsHashCode, String args) {
		this.methodSignature = methodSignature;
		this.argsHashCode = argsHashCode;
		this.args = args;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public void setMethodSignature(String methodSignature) {
		this.methodSignature = methodSignature;
	}

	public String getArgs() {
		return args;
	}

	public void setArgs(String args) {
		this.args = args;
	}

	public long getArgsHashCode() {
		return argsHashCode;
	}

	public void setArgsHashCode(long argsHashCode) {
		this.argsHashCode = argsHashCode;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public long getCacheTime() {
		return cacheTime;
	}

	public void setCacheTime(long cacheTime) {
		this.cacheTime = cacheTime;
	}

	public long getExpireTimeStamp() {
		return expireTimeStamp;
	}

	public void setExpireTimeStamp(long expireTimeStamp) {
		this.expireTimeStamp = expireTimeStamp;
	}

	public boolean isExpired(){
		return expireTimeStamp >= 0L && new Date().getTime() > expireTimeStamp;
	}

	@Override
	public String toString() {
		return "CacheDataModel{" +
				"methodSignature='" + methodSignature + '\'' +
				", argsHashCode=" + argsHashCode +
				", args='" + args + '\'' +
				", data=" + data +
				", cacheTime=" + cacheTime +
				", expireTimeStamp=" + expireTimeStamp +
				'}';
	}
}
