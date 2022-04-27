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
	private Long methodSignatureHashCode;

	/**
	 * 入参
	 * */
	private String args;

	/**
	 * 入参哈希值
	 * */
	private Long argsHashCode;


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
	private long expireTimeStamp;

	public CacheDataModel(String methodSignature, long methodSignatureHashCode, String args, long argsHashCode, Object data, long expireTimeStamp) {
		this.methodSignature = methodSignature;
		this.methodSignatureHashCode = methodSignatureHashCode;
		this.argsHashCode = argsHashCode;
		this.args = args;
		this.data = data;
		this.expireTimeStamp = expireTimeStamp;
	}

	public static SimpleDateFormat getOutPrintSimpleDateFormat() {
		return outPrintSimpleDateFormat;
	}

	public static void setOutPrintSimpleDateFormat(SimpleDateFormat outPrintSimpleDateFormat) {
		CacheDataModel.outPrintSimpleDateFormat = outPrintSimpleDateFormat;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public void setMethodSignature(String methodSignature) {
		this.methodSignature = methodSignature;
	}

	public Long getMethodSignatureHashCode() {
		return methodSignatureHashCode;
	}

	public void setMethodSignatureHashCode(Long methodSignatureHashCode) {
		this.methodSignatureHashCode = methodSignatureHashCode;
	}

	public String getArgs() {
		return args;
	}

	public void setArgs(String args) {
		this.args = args;
	}

	public Long getArgsHashCode() {
		return argsHashCode;
	}

	public void setArgsHashCode(Long argsHashCode) {
		this.argsHashCode = argsHashCode;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public Long getCacheTime() {
		return cacheTime;
	}

	public void setCacheTime(Long cacheTime) {
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
				"methodSignatureHashCode='" + methodSignatureHashCode + '\'' +
				", args='" + args + '\'' +
				", argsHashCode=" + argsHashCode +
				", data=" + data +
				", cacheTime=" + getFormatDate(cacheTime) +
				", expireTime=" + getFormatDate(expireTimeStamp) +
				'}';
	}


	public String toJSONString(){
		Map<String,Object> objectMap = new HashMap<>();
		objectMap.put("methodSignature",methodSignature);
		objectMap.put("methodSignatureHashCode",methodSignatureHashCode);
		objectMap.put("args",args);
		objectMap.put("argsHashCode",argsHashCode);
		objectMap.put("data",data);
		objectMap.put("cacheTime",getFormatDate(cacheTime));
		objectMap.put("expireTime",getFormatDate(expireTimeStamp));
		return JSONObject.toJSONString(objectMap);
	}

	private String getFormatDate(long timeStamp){
		try {
			return outPrintSimpleDateFormat.format(new Date());
		}catch (Exception e){
			return String.valueOf(timeStamp);
		}
	}
}
