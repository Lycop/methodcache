package love.kill.methodcache.datahelper;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * 缓存统计
 *
 * @author Lycop
 */
public class CacheStatisticsModel implements Serializable {

	private static final long serialVersionUID = 1L;

	private static SimpleDateFormat outPrintSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * 缓存key
	 */
	private String cacheKey;

	/**
	 * 方法签名
	 */
	private String methodSignature;

	/**
	 * 方法签名哈希值
	 */
	private int methodSignatureHashCode;

	/**
	 * 缓存id
	 */
	private String id;

	/**
	 * 缓存备注
	 */
	private String remark;

	/**
	 * 命中次数
	 */
	private volatile int hit = -1;

	/**
	 * 命中平均耗时
	 */
	private long avgOfHitSpend = -1L;

	/**
	 * 命中时最小耗时
	 */
	private long minHitSpend = -1L;

	/**
	 * 命中时最小耗时发生时间(时间戳，毫秒)
	 */
	private long timeOfMinHitSpend = -1L;

	/**
	 * 命中时最小耗时入参
	 */
	private String argsOfMinHitSpend = "";

	/**
	 * 命中时最大耗时
	 */
	private long maxHitSpend = -1L;

	/**
	 * 命中时最大耗时发生时间(时间戳，毫秒)
	 */
	private long timeOfMaxHitSpend = -1L;

	/**
	 * 命中时最大耗时入参
	 */
	private String argsOfMaxHitSpend = "";

	/**
	 * 未命中次数
	 */
	private volatile int failure = -1;

	/**
	 * 未命中平均耗时
	 */
	private long avgOfFailureSpend = -1L;

	/**
	 * 未命中时最小耗时
	 */
	private long minFailureSpend = -1L;

	/**
	 * 未命中时最小耗时发生时间(时间戳，毫秒)
	 */
	private long timeOfMinFailureSpend = -1;

	/**
	 * 未命中时最小耗时入参
	 */
	private String argsOfMinFailureSpend = "";

	/**
	 * 未命中时最大耗时
	 */
	private long maxFailureSpend = -1L;

	/**
	 * 未命中时最大耗时发生时间(时间戳，毫秒)
	 */
	private long timeOfMaxFailureSpend = -1L;

	/**
	 * 未命中时最小耗时入参
	 */
	private String argsOfMaxFailureSpend = "";


	public CacheStatisticsModel(String cacheKey, String methodSignature, int methodSignatureHashCode, String id, String remark) {
		this.cacheKey = cacheKey;
		this.methodSignature = methodSignature;
		this.methodSignatureHashCode = methodSignatureHashCode;
		this.id = id;
		this.remark = remark;
	}

	public String getCacheKey() {
		return cacheKey;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public int getMethodSignatureHashCode() {
		return methodSignatureHashCode;
	}

	public String getId() {
		return id;
	}

	public String getRemark() {
		return remark;
	}

	public int getHit() {
		return this.hit == -1 ? 0 : this.hit;
	}

	public String printHit() {
		return String.valueOf(getHit());
	}

	public void incrementHit() {
		this.hit = getHit() + 1;
	}

	public void incrementHit(int hit) {
		this.hit = getHit() + hit;
	}

	public long getAvgOfHitSpend() {
		return this.avgOfHitSpend == -1L ? 0L : this.avgOfHitSpend;
	}

	public String printAvgOfHitSpend() {
		if (this.avgOfHitSpend == -1L) {
			return "";
		}
		return String.valueOf(this.avgOfHitSpend);
	}

	public void setAvgOfHitSpend(long avgOfHitSpend) {
		this.avgOfHitSpend = avgOfHitSpend;
	}

	public void calculateAvgOfHitSpend(long avgOfHitSpend) {
		if (this.avgOfHitSpend == -1L) {
			setAvgOfHitSpend(avgOfHitSpend);
		} else {
			BigDecimal hit = new BigDecimal(getHit());
			setAvgOfHitSpend(new BigDecimal(this.avgOfHitSpend)
					.multiply(hit)
					.add(new BigDecimal(avgOfHitSpend))
					.divide(hit, 0, BigDecimal.ROUND_HALF_UP)
					.longValue());
		}
	}

	public String printMinHitSpend() {
		if (this.minHitSpend == -1L) {
			return "";
		}
		return String.valueOf(this.minHitSpend);
	}

	public void setMinHitSpend(long minHitSpend, long timeOfMinHitSpend, String args) {
		if (this.minHitSpend == -1L || this.minHitSpend > minHitSpend) {
			this.minHitSpend = minHitSpend;
			this.timeOfMinHitSpend = timeOfMinHitSpend;
			this.argsOfMinHitSpend = args;
		}
	}

	public String printTimeOfMinHitSpend() {
		if (this.timeOfMinHitSpend == -1L) {
			return "";
		}
		return outPrintSimpleDateFormat.format(new Date(this.timeOfMinHitSpend));
	}

	public String printArgsOfMinHitSpend() {
		return Objects.toString(this.argsOfMinHitSpend);
	}

	public String printMaxHitSpend() {
		if (this.maxHitSpend == -1L) {
			return "";
		}
		return String.valueOf(this.maxHitSpend);
	}

	public void setMaxHitSpend(long maxHitSpend, long timeOfMaxHitSpend, String args) {
		if (this.maxHitSpend == -1L || this.maxHitSpend < maxHitSpend) {
			this.maxHitSpend = maxHitSpend;
			this.timeOfMaxHitSpend = timeOfMaxHitSpend;
			this.argsOfMaxHitSpend = args;
		}
	}

	public String printTimeOfMaxHitSpend() {
		if (this.timeOfMaxHitSpend == -1L) {
			return "";
		}
		return outPrintSimpleDateFormat.format(new Date(this.timeOfMaxHitSpend));
	}

	public String printArgsOfMaxHitSpend() {
		return Objects.toString(argsOfMaxHitSpend);
	}

	public int getFailure() {
		return this.failure == -1 ? 0 : this.failure;
	}

	public String printFailure() {
		return String.valueOf(getFailure());
	}

	public void incrementFailure() {
		this.failure = getFailure() + 1;
	}

	public void incrementFailure(int failure) {
		this.failure = getFailure() + failure;
	}

	public long getAvgOfFailureSpend() {
		return this.avgOfFailureSpend == -1L ? 0L : this.avgOfFailureSpend;
	}

	public String printAvgOfFailureSpend() {
		if (this.avgOfFailureSpend == -1L) {
			return "";
		}
		return String.valueOf(this.avgOfFailureSpend);
	}

	public void setAvgOfFailureSpend(long avgOfFailureSpend) {
		this.avgOfFailureSpend = avgOfFailureSpend;
	}

	public void calculateAvgOfFailureSpend(long avgOfFailureSpend) {
		if (this.avgOfFailureSpend == -1L) {
			setAvgOfFailureSpend(avgOfFailureSpend);
		} else {
			BigDecimal failure = new BigDecimal(getFailure());
			setAvgOfFailureSpend(new BigDecimal(this.avgOfFailureSpend)
					.multiply(failure)
					.add(new BigDecimal(avgOfFailureSpend))
					.divide(failure, 0, BigDecimal.ROUND_HALF_UP)
					.longValue());
		}
	}

	public String printMinFailureSpend() {
		if (this.minFailureSpend == -1L) {
			return "";
		}
		return String.valueOf(this.minFailureSpend);
	}

	public void setMinFailureSpend(long minFailureSpend, long timeOfMinFailureSpend, String args) {
		if (this.minFailureSpend == -1L || this.minFailureSpend > minFailureSpend) {
			this.minFailureSpend = minFailureSpend;
			this.timeOfMinFailureSpend = timeOfMinFailureSpend;
			this.argsOfMinFailureSpend = args;
		}
	}

	public String printTimeOfMinFailureSpend() {
		if (this.timeOfMinFailureSpend == -1L) {
			return "";
		}
		return outPrintSimpleDateFormat.format(new Date(this.timeOfMinFailureSpend));
	}

	public String printArgsOfMinFailureSpend() {
		return Objects.toString(argsOfMinFailureSpend);
	}

	public String printMaxFailureSpend() {
		if (this.maxFailureSpend == -1) {
			return "";
		}
		return String.valueOf(this.maxFailureSpend);
	}

	public void setMaxFailureSpend(long maxFailureSpend, long timeOfMaxFailureSpend, String args) {
		if (this.maxFailureSpend < maxFailureSpend) {
			this.maxFailureSpend = maxFailureSpend;
			this.timeOfMaxFailureSpend = timeOfMaxFailureSpend;
			this.argsOfMaxFailureSpend = args;
		}
	}

	public String printTimeOfMaxFailureSpend() {
		if (this.timeOfMaxFailureSpend == -1L) {
			return "";
		}
		return outPrintSimpleDateFormat.format(new Date(this.timeOfMaxFailureSpend));
	}

	public String printArgsOfMaxFailureSpend() {
		return Objects.toString(argsOfMaxFailureSpend);
	}

	public int getTimes() {
		return getHit() + getFailure();
	}

	public String printTimes() {
		return String.valueOf(getTimes());
	}

	@Override
	public String toString() {
		return "CacheStatisticsModel{" +
				"methodSignature='" + methodSignature + '\'' +
				", methodSignatureHashCode=" + methodSignatureHashCode +
				", id='" + id + '\'' +
				", remark='" + remark + '\'' +
				", hit=" + printHit() +
				", avgOfHitSpend=" + avgOfHitSpend +
				", minHitSpend=" + minHitSpend +
				", timeOfMinHitSpend=" + timeOfMinHitSpend +
				", maxHitSpend=" + maxHitSpend +
				", timeOfMaxHitSpend=" + timeOfMaxHitSpend +
				", failure=" + failure +
				", avgOfFailureSpend=" + avgOfFailureSpend +
				", minFailureSpend=" + minFailureSpend +
				", timeOfMinFailureSpend=" + timeOfMinFailureSpend +
				", maxFailureSpend=" + maxFailureSpend +
				", timeOfMaxFailureSpend=" + timeOfMaxFailureSpend +
				'}';
	}
}
