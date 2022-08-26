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
	 * 方法签名
	 * */
	private String methodSignature;

	/**
	 * 方法签名哈希值
	 * */
	private int methodSignatureHashCode;

	/**
	 * 入参
	 * */
	private String args;

	/**
	 * 入参哈希值
	 * */
	private int argsHashCode;

	/**
	 * 缓存哈希值
	 * */
	private int cacheHashCode;

	/**
	 * 数据
	 * */
	private Object data;

	/**
	 * 缓存时间
	 * */
	private Long cacheTime = new Date().getTime();

	/**
	 * 过期时间（时间戳）
	 * -1 代表永久有效
	 * */
	private long expireTime;

	/**
	 * 备注
	 * */
	private String remark;

	/**
	 * id
	 * */
	private String id;

	/**
	 * 数据大小
	 * */
	private long instanceSize = 0L;



	public CacheDataModel(String methodSignature, int methodSignatureHashCode, String args, int argsHashCode, int cacheHashCode, Object data, long expireTime) {
		this.methodSignature = methodSignature;
		this.methodSignatureHashCode = methodSignatureHashCode;
		this.argsHashCode = argsHashCode;
		this.args = args;
		this.data = data;
		this.expireTime = expireTime;
		this.cacheHashCode = cacheHashCode;

//		this.instanceSize = RamUsageEstimator.sizeOf(methodSignature) + RamUsageEstimator.sizeOf(methodSignatureHashCode) + RamUsageEstimator.sizeOf(args) +
//				RamUsageEstimator.sizeOf(argsHashCode) + RamUsageEstimator.sizeOf(cacheHashCode) + RamUsageEstimator.sizeOf(data) + RamUsageEstimator.sizeOf(expireTime);

		refreshInstanceSize();
	}

//	private long getObjectSize(Object data) {
//		return ObjectSizeCalculator.getObjectSize(data);
//		return RamUsageEstimator.sizeOf(data);
//		try {
//			// TODO: 2022/8/22  计算误差过大
//			return doObjectSize(data);
//		} catch (IllegalAccessException e) {
//			e.printStackTrace();
//			return 0;
//		}
//	}

//	private static long doObjectSize(Object arg) throws IllegalAccessException {
//
//		if(arg == null){
//			return 0;
//		}
//
//		if(isPrimitive(arg.getClass())){
//			// 基本数据类型
//			return ClassLayout.parseInstance(arg).instanceSize();
//
//		}else {
//			// 复杂对象类型
//			Field[] declaredFields = arg.getClass().getDeclaredFields();
//
//			for(Field field : declaredFields){
//				field.setAccessible(true);
//			}
//
//			List<Field> fieldList = new LinkedList<>(Arrays.asList(declaredFields));
//
//			long totalSize = 0L;
//			for(Field field : fieldList){
//				totalSize += doObjectSize(field.get(arg));
//			}
//			return totalSize;
//		}
//	}


	public String getMethodSignature() {
		return methodSignature;
	}

	public String getArgs() {
		return args;
	}

	public int getArgsHashCode() {
		return argsHashCode;
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


	public synchronized boolean isExpired(){
		return expireTime >= 0L && new Date().getTime() >= expireTime;
	}

	public synchronized void expired(){
		expireTime  = new Date().getTime();
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
				"methodSignatureHashCode='" + methodSignatureHashCode + '\'' +
				", args='" + args + '\'' +
				", argsHashCode=" + argsHashCode +
				", data=" + data +
				", cacheTime=" + formatDate(cacheTime) +
				", expireTime=" + formatDate(expireTime) +
				", remark=" + remark +
				", id=" + id +
				", instanceSize=" + instanceSize +
				'}';
	}

	private String formatDate(long timeStamp){
		try {
			return outPrintSimpleDateFormat.format(new Date(timeStamp));
		}catch (Exception e){
			return String.valueOf(timeStamp);
		}
	}
//
//	private static boolean isPrimitive(Class clazz) {
//		return  clazz.isPrimitive() || isInternal(clazz);
//	}
//
//	private static boolean isInternal(Class clazz) {
//		return	(String.class == clazz) ||
//				(Short.class == clazz) ||
//				(Integer.class == clazz) ||
//				(Long.class == clazz) ||
//				(Float.class == clazz) ||
//				(Double.class == clazz) ||
//				(Character.class == clazz) ||
//				(Boolean.class == clazz);
//	}

	private void refreshInstanceSize(){
		this.instanceSize = RamUsageEstimator.sizeOf(this);
	}
}
