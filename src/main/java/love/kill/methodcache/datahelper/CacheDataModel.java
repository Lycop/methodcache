package love.kill.methodcache.datahelper;

import com.alibaba.fastjson.JSONObject;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
	 * id
	 * */
	private String id = "";

	/**
	 * 备注
	 * */
	private String remark;

	public CacheDataModel(String methodSignature, String args, int argsHashCode, Object data, long expireTime) {
		this.methodSignature = methodSignature;
		this.methodSignatureHashCode = hash(methodSignatureHashCode);
		this.argsHashCode = argsHashCode;
		this.args = args;
		this.data = data;
		this.expireTime = expireTime;
		this.cacheHashCode = hash(String.valueOf(methodSignatureHashCode) + String.valueOf(argsHashCode));
	}


	public String getMethodSignature() {
		return methodSignature;
	}

	public int getMethodSignatureHashCode() {
		return methodSignatureHashCode;
	}

	public String getArgs() {
		return args;
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
				", id=" + id +
				", remark=" + remark +
				'}';
	}


	public String toJSONString(){
		Map<String,Object> objectMap = new HashMap<>();
		objectMap.put("methodSignature",methodSignature);
		objectMap.put("methodSignatureHashCode",methodSignatureHashCode);
		objectMap.put("args",args);
		objectMap.put("argsHashCode",argsHashCode);
		objectMap.put("data",data);
		objectMap.put("cacheTime",formatDate(cacheTime));
		objectMap.put("expireTime",formatDate(expireTime));
		objectMap.put("id",id);
		objectMap.put("remark",remark);
		return JSONObject.toJSONString(objectMap);
	}

	private String formatDate(long timeStamp){
		try {
			return outPrintSimpleDateFormat.format(new Date(timeStamp));
		}catch (Exception e){
			return String.valueOf(timeStamp);
		}
	}

	private static int hash(Object key) {
		int h;
		return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
	}
}
