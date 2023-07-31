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
	 * 累计命中耗时
	 */
	private volatile long totalOfHitSpend = -1L;

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
	 * 累计未命中耗时
	 */
	private long totalOfFailureSpend = -1L;

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

	/**
	 * 异常次数
	 */
	private volatile int exception = -1;

	/**
	 * 最后一次异常发生时间(时间戳，毫秒)
	 */
	private long timeOfLastException = -1L;

	/**
	 * 最后一次发生异常的入参
	 */
	private String argsOfLastException = "";

	/**
	 * 最后一次发生异常的信息
	 */
	private String stackTraceOfLastException = "";


	public CacheStatisticsModel(String cacheKey, String methodSignature, int methodSignatureHashCode, String id,
								String remark) {
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

	public long getTotalOfHitSpend() {
		return this.totalOfHitSpend == -1L ? 0 : this.totalOfHitSpend;
	}

	public String printTotalOfHitSpend() {
		if (this.totalOfHitSpend == -1L) {
			return "";
		}
		return String.valueOf(this.totalOfHitSpend);
	}

	public void incrementHit(long spend) {
		this.hit = getHit() + 1;
		this.totalOfHitSpend = getTotalOfHitSpend() + spend;

	}

	public long getAvgOfHitSpend() {
		if (hit == -1 || totalOfHitSpend == -1L) {
			return -1L;
		}
		if (hit == 0) {
			return 0;
		}
		return new BigDecimal(totalOfHitSpend).divide(new BigDecimal(hit), 0, BigDecimal.ROUND_HALF_UP).longValue();
	}

	public String printAvgOfHitSpend() {
		if (getAvgOfHitSpend() == -1L) {
			return "";
		}
		return String.valueOf(getAvgOfHitSpend());
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

	public long getTotalOfFailureSpend() {
		return this.totalOfFailureSpend == -1L ? 0 : this.totalOfFailureSpend;
	}

	public String printTotalOfFailureSpend() {
		if (this.totalOfFailureSpend == -1L) {
			return "";
		}
		return String.valueOf(this.totalOfFailureSpend);
	}

	public void incrementFailure(long spend) {
		this.failure = getFailure() + 1;
		this.totalOfFailureSpend = getTotalOfFailureSpend() + spend;
	}

	public long getAvgOfFailureSpend() {
		if (this.failure == -1 || this.totalOfFailureSpend == -1L) {
			return -1L;
		}
		if (this.failure == 0) {
			return 0;
		}
		return new BigDecimal(this.totalOfFailureSpend).divide(new BigDecimal(this.failure), 0, BigDecimal.ROUND_HALF_UP).longValue();
	}

	public String printAvgOfFailureSpend() {
		if (getAvgOfFailureSpend() == -1L) {
			return "";
		}
		return String.valueOf(getAvgOfFailureSpend());
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

	public int getException() {
		return this.exception == -1 ? 0 : this.exception;
	}

	public String printException() {
		return String.valueOf(getException());
	}


	public void incrementTimesOfException(String args, String stackTrace, long time) {
		this.exception = getException() + 1;
		this.argsOfLastException = args;
		this.stackTraceOfLastException = stackTrace;
		this.timeOfLastException = time;
	}

	public String printArgsOfLastException() {
		return Objects.toString(this.argsOfLastException);
	}

	public String printStackTraceOfLastException() {
		return this.stackTraceOfLastException;
	}

	public String printTimeOfLastException() {
		if (this.timeOfLastException == -1L) {
			return "";
		}
		return outPrintSimpleDateFormat.format(new Date(this.timeOfLastException));
	}

	@Override
	public String toString() {
		return "CacheStatisticsModel{" +
				"methodSignature='" + methodSignature + '\'' +
				", methodSignatureHashCode=" + methodSignatureHashCode +
				", id='" + id + '\'' +
				", remark='" + remark + '\'' +
				", hit=" + printHit() +
				", avgOfHitSpend=" + getAvgOfHitSpend() +
				", totalOfHitSpend=" + getTotalOfHitSpend() +
				", minHitSpend=" + minHitSpend +
				", timeOfMinHitSpend=" + timeOfMinHitSpend +
				", maxHitSpend=" + maxHitSpend +
				", timeOfMaxHitSpend=" + timeOfMaxHitSpend +
				", failure=" + failure +
				", avgOfFailureSpend=" + getAvgOfFailureSpend() +
				", totalOfFailureSpend=" + getTotalOfFailureSpend() +
				", minFailureSpend=" + minFailureSpend +
				", timeOfMinFailureSpend=" + timeOfMinFailureSpend +
				", maxFailureSpend=" + maxFailureSpend +
				", timeOfMaxFailureSpend=" + timeOfMaxFailureSpend +
				", exception=" + exception +
				", argsOfLastException=" + argsOfLastException +
				", stackTraceOfLastException=" + stackTraceOfLastException +
				", timeOfLastException=" + timeOfLastException +
				'}';
	}
}
