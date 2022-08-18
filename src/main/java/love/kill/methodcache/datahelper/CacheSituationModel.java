package love.kill.methodcache.datahelper;

import java.io.Serializable;
import java.text.SimpleDateFormat;

/**
 * 缓存情况模型
 *
 * @author Lycop
 */
public class CacheSituationModel implements Serializable {

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
	 * 入参
	 */
	private String args;

	/**
	 * 入参哈希值
	 */
	private int argsHashCode;

	/**
	 * 缓存哈希值
	 */
	private int cacheHashCode;

	/**
	 * 备注
	 */
	private String remark;

	/**
	 * id
	 */
	private String id;

	/**
	 * 命中次数
	 */
	private int hit;

	/**
	 * 未命中次数
	 */
	private int failure;

	/**
	 * 命中时耗时
	 */
	private long hitSpend;

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
	 * 未命中时耗时
	 */
	private long failureSpend;

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


	public CacheSituationModel(String methodSignature, int methodSignatureHashCode, String args, int argsHashCode, int cacheHashCode, String id, String remark) {
		this.methodSignature = methodSignature;
		this.methodSignatureHashCode = methodSignatureHashCode;
		this.args = args;
		this.argsHashCode = argsHashCode;
		this.cacheHashCode = cacheHashCode;
		this.id = id;
		this.remark = remark;
	}

	public static SimpleDateFormat getOutPrintSimpleDateFormat() {
		return outPrintSimpleDateFormat;
	}

	public static void setOutPrintSimpleDateFormat(SimpleDateFormat outPrintSimpleDateFormat) {
		CacheSituationModel.outPrintSimpleDateFormat = outPrintSimpleDateFormat;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public void setMethodSignature(String methodSignature) {
		this.methodSignature = methodSignature;
	}

	public int getMethodSignatureHashCode() {
		return methodSignatureHashCode;
	}

	public void setMethodSignatureHashCode(int methodSignatureHashCode) {
		this.methodSignatureHashCode = methodSignatureHashCode;
	}

	public String getArgs() {
		return args;
	}

	public void setArgs(String args) {
		this.args = args;
	}

	public int getArgsHashCode() {
		return argsHashCode;
	}

	public void setArgsHashCode(int argsHashCode) {
		this.argsHashCode = argsHashCode;
	}

	public int getCacheHashCode() {
		return cacheHashCode;
	}

	public void setCacheHashCode(int cacheHashCode) {
		this.cacheHashCode = cacheHashCode;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getHit() {
		return hit;
	}

	public void setHit(int hit) {
		this.hit = hit;
	}

	public int getFailure() {
		return failure;
	}

	public void setFailure(int failure) {
		this.failure = failure;
	}

	public long getHitSpend() {
		return hitSpend;
	}

	public void setHitSpend(long hitSpend) {
		this.hitSpend = hitSpend;
	}

	public long getMinHitSpend() {
		return minHitSpend;
	}

	public void setMinHitSpend(long minHitSpend) {
		this.minHitSpend = minHitSpend;
	}

	public long getTimeOfMinHitSpend() {
		return timeOfMinHitSpend;
	}

	public void setTimeOfMinHitSpend(long timeOfMinHitSpend) {
		this.timeOfMinHitSpend = timeOfMinHitSpend;
	}

	public long getMaxHitSpend() {
		return maxHitSpend;
	}

	public void setMaxHitSpend(long maxHitSpend) {
		this.maxHitSpend = maxHitSpend;
	}

	public long getTimeOfMaxHitSpend() {
		return timeOfMaxHitSpend;
	}

	public void setTimeOfMaxHitSpend(long timeOfMaxHitSpend) {
		this.timeOfMaxHitSpend = timeOfMaxHitSpend;
	}

	public long getFailureSpend() {
		return failureSpend;
	}

	public void setFailureSpend(long failureSpend) {
		this.failureSpend = failureSpend;
	}

	public long getMinFailureSpend() {
		return minFailureSpend;
	}

	public void setMinFailureSpend(long minFailureSpend) {
		this.minFailureSpend = minFailureSpend;
	}

	public long getTimeOfMinFailureSpend() {
		return timeOfMinFailureSpend;
	}

	public void setTimeOfMinFailureSpend(long timeOfMinFailureSpend) {
		this.timeOfMinFailureSpend = timeOfMinFailureSpend;
	}

	public long getMaxFailureSpend() {
		return maxFailureSpend;
	}

	public void setMaxFailureSpend(long maxFailureSpend) {
		this.maxFailureSpend = maxFailureSpend;
	}

	public long getTimeOfMaxFailureSpend() {
		return timeOfMaxFailureSpend;
	}

	public void setTimeOfMaxFailureSpend(long timeOfMaxFailureSpend) {
		this.timeOfMaxFailureSpend = timeOfMaxFailureSpend;
	}

	@Override
	public String toString() {
		return "CacheSituationModel{" +
				"methodSignature='" + methodSignature + '\'' +
				", methodSignatureHashCode=" + methodSignatureHashCode +
				", args='" + args + '\'' +
				", argsHashCode=" + argsHashCode +
				", cacheHashCode=" + cacheHashCode +
				", remark='" + remark + '\'' +
				", id='" + id + '\'' +
				", hit=" + hit +
				", failure=" + failure +
				", hitSpend=" + hitSpend +
				", minHitSpend=" + minHitSpend +
				", timeOfMinHitSpend=" + timeOfMinHitSpend +
				", maxHitSpend=" + maxHitSpend +
				", timeOfMaxHitSpend=" + timeOfMaxHitSpend +
				", failureSpend=" + failureSpend +
				", minFailureSpend=" + minFailureSpend +
				", timeOfMinFailureSpend=" + timeOfMinFailureSpend +
				", maxFailureSpend=" + maxFailureSpend +
				", timeOfMaxFailureSpend=" + timeOfMaxFailureSpend +
				'}';
	}
}
