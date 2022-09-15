package love.kill.methodcache.datahelper;

import com.carrotsearch.sizeof.RamUsageEstimator;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 缓存数据模型
 *
 * @author Lycop
 */
public class CacheDataModel implements Serializable {

	private static final long serialVersionUID = 1L;

	private static SimpleDateFormat outPrintSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * 应用名
	 */
	private String applicationName;

	/**
	 * 方法签名
	 */
	private String methodSignature;

	/**
	 * 方法入参
	 */
	private String args;

	/**
	 * 缓存哈希值
	 */
	private int cacheHashCode;

	/**
	 * 缓存数据
	 */
	private Object data;

	/**
	 * 缓存时间
	 */
	private Long cacheTime = new Date().getTime();

	/**
	 * 过期时间（时间戳）
	 * -1 代表永久有效
	 */
	private long expireTime;

	/**
	 * 备注
	 */
	private String remark;

	/**
	 * id
	 */
	private String id;

	/**
	 * 数据大小
	 */
	private long instanceSize = 0L;


	public CacheDataModel(String applicationName, String methodSignature, String args, int cacheHashCode, Object data, long expireTime) {
		this.applicationName = applicationName;
		this.methodSignature = methodSignature;
		this.args = args;
		this.cacheHashCode = cacheHashCode;
		this.data = data;
		this.expireTime = expireTime;

		refreshInstanceSize();
	}

	public String getApplicationName() {
		return applicationName;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public String getArgs() {
		return args;
	}

	public int getCacheHashCode() {
		return cacheHashCode;
	}

	public Object getData() {
		return data;
	}


	public String getFormatCacheTime() {
		return formatDate(cacheTime);
	}

	public long getExpireTime() {
		return expireTime;
	}

	public String getFormatExpireTime() {
		return formatDate(expireTime);
	}


	public synchronized boolean isExpired() {
		return expireTime >= 0L && new Date().getTime() >= expireTime;
	}

	public synchronized void expired() {
		expireTime = new Date().getTime();
	}


	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public long getInstanceSize() {
		return instanceSize;
	}

	public void setInstanceSize(long instanceSize) {
		this.instanceSize = instanceSize;
	}

	@Override
	public String toString() {
		return "CacheDataModel{" +
				"methodSignature='" + methodSignature + '\'' +
				", args='" + args + '\'' +
				", data=" + data +
				", cacheTime=" + formatDate(cacheTime) +
				", expireTime=" + formatDate(expireTime) +
				", remark=" + remark +
				", id=" + id +
				", instanceSize=" + instanceSize +
				'}';
	}

	private String formatDate(long timeStamp) {
		try {
			return outPrintSimpleDateFormat.format(new Date(timeStamp));
		} catch (Exception e) {
			return String.valueOf(timeStamp);
		}
	}

	private void refreshInstanceSize() {
		this.instanceSize = RamUsageEstimator.sizeOf(this);
	}
}
