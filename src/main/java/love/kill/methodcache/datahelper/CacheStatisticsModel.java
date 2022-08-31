package love.kill.methodcache.datahelper;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 缓存统计
 *
 * @author Lycop
 */
public class CacheStatisticsModel implements Serializable {

	private static final long serialVersionUID = 1L;

	private static SimpleDateFormat outPrintSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * 方法签名
	 */
	private String methodSignature;

	/**
	 * 方法签名哈希值
	 */
	private int methodSignatureHashCode;

	/**
	 * id
	 */
	private String id;

	/**
	 * 缓存备注
	 */
	private String remark;

	/**
	 * 命中次数
	 */
	private int hit;

	/**
	 * 命中平均耗时
	 */
	private long avgOfHitSpend;

	/**
	 * 命中时最小耗时
	 */
	private long minHitSpend;

	/**
	 * 命中时最小耗时发生时间(时间戳，毫秒)
	 */
	private long timeOfMinHitSpend;

	/**
	 * 命中时最大耗时
	 */
	private long maxHitSpend;

	/**
	 * 命中时最大耗时发生时间(时间戳，毫秒)
	 */
	private long timeOfMaxHitSpend;

	/**
	 * 未命中次数
	 */
	private int failure;

	/**
	 * 未命中平均耗时
	 */
	private long avgOfFailureSpend;

	/**
	 * 未命中时最小耗时
	 */
	private long minFailureSpend;

	/**
	 * 未命中时最小耗时发生时间(时间戳，毫秒)
	 */
	private long timeOfMinFailureSpend;

	/**
	 * 未命中时最大耗时
	 */
	private long maxFailureSpend;

	/**
	 * 未命中时最大耗时发生时间(时间戳，毫秒)
	 */
	private long timeOfMaxFailureSpend;


	public CacheStatisticsModel(String methodSignature, int methodSignatureHashCode, String id, String remark) {
		this.methodSignature = methodSignature;
		this.methodSignatureHashCode = methodSignatureHashCode;
		this.id = id;
		this.remark = remark;
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
		return hit;
	}

	public void incrementHit() {
		this.hit ++;
	}

	public void incrementHit(int hit) {
		this.hit += hit;
	}

	public long getAvgOfHitSpend() {
		return avgOfHitSpend;
	}

	public void calculateAvgOfHitSpend(long avgOfHitSpend) {
		this.avgOfHitSpend = new BigDecimal(this.avgOfHitSpend).add(new BigDecimal(avgOfHitSpend)).divide(new BigDecimal(2), 0, BigDecimal.ROUND_HALF_UP).longValue();
	}

	public long getMinHitSpend() {
		return minHitSpend;
	}

	public void setMinHitSpend(long minHitSpend, long timeOfMinHitSpend) {
		if(this.minHitSpend == 0L || this.minHitSpend > minHitSpend){
			this.minHitSpend = minHitSpend;
			this.timeOfMinHitSpend = timeOfMinHitSpend;
		}
	}

	public String printTimeOfMinHitSpend() {
		return outPrintSimpleDateFormat.format(new Date(timeOfMinHitSpend));
	}

	public long getMaxHitSpend() {
		return maxHitSpend;
	}

	public void setMaxHitSpend(long maxHitSpend, long timeOfMaxHitSpend) {
		if(this.maxHitSpend < maxHitSpend){
			this.maxHitSpend = maxHitSpend;
			this.timeOfMaxHitSpend = timeOfMaxHitSpend;
		}

	}

	public String printTimeOfMaxHitSpend() {
		return outPrintSimpleDateFormat.format(new Date(timeOfMaxHitSpend));
	}


	public int getFailure() {
		return failure;
	}

	public void incrementFailure() {
		this.failure ++;
	}

	public void incrementFailure(int failure) {
		this.failure += failure;
	}

	public long getAvgOfFailureSpend() {
		return avgOfFailureSpend;
	}

	public void calculateAvgOfFailureSpend(long avgOfFailureSpend) {
		this.avgOfFailureSpend = new BigDecimal(this.avgOfFailureSpend).add(new BigDecimal(avgOfFailureSpend)).divide(new BigDecimal(2), 0, BigDecimal.ROUND_HALF_UP).longValue();
	}

	public long getMinFailureSpend() {
		return minFailureSpend;
	}

	public void setMinFailureSpend(long minFailureSpend, long timeOfMinFailureSpend) {
		if(this.minFailureSpend == 0L || this.minFailureSpend > minFailureSpend){
			this.minFailureSpend = minFailureSpend;
			this.timeOfMinFailureSpend = timeOfMinFailureSpend;
		}

	}

	public String printTimeOfMinFailureSpend() {
		return outPrintSimpleDateFormat.format(new Date(timeOfMinFailureSpend));
	}

	public long getMaxFailureSpend() {
		return maxFailureSpend;
	}

	public void setMaxFailureSpend(long maxFailureSpend, long timeOfMaxFailureSpend) {
		if(this.maxFailureSpend < maxFailureSpend){
			this.maxFailureSpend = maxFailureSpend;
			this.timeOfMaxFailureSpend = timeOfMaxFailureSpend;
		}

	}

	public String printTimeOfMaxFailureSpend() {
		return outPrintSimpleDateFormat.format(new Date(timeOfMaxFailureSpend));
	}

	@Override
	public String toString() {
		return "CacheStatisticsModel{" +
				"methodSignature='" + methodSignature + '\'' +
				", methodSignatureHashCode=" + methodSignatureHashCode +
				", id='" + id + '\'' +
				", remark='" + remark + '\'' +
				", hit=" + hit +
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
