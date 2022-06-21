package love.kill.methodcache.datahelper.impl;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.datahelper.CacheDataModel;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.DataUtil;
import love.kill.methodcache.util.RedisUtil;
import love.kill.methodcache.util.SerializeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public class RedisDataHelper implements DataHelper {

	private static Logger logger = LoggerFactory.getLogger(RedisDataHelper.class);

	/**
	 * 锁前缀
	 * */
	private static final String REDIS_LOCK_PREFIX = "REDIS_LOCK_";

	/**
	 * 缓存key
	 * */
	private static final  String METHOD_CACHE_DATA = "METHOD_CACHE_DATA";

	/**
	 * 签名和入参的分隔符
	 * */
	private static final  String CACHE_KEY_SEPARATION_CHARACTER = "@";

	/**
	 * cpu个数
	 */
	private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();


	/**
	 * 执行线程
	 * */
	private static final ExecutorService executorService = new ThreadPoolExecutor(
			CPU_COUNT + 1, // 核心线程数（CPU核心数 + 1）
			CPU_COUNT * 2 + 1, // 线程池最大线程数（CPU核心数 * 2 + 1）
			1,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(),
			Executors.defaultThreadFactory(),
			new ThreadPoolExecutor.AbortPolicy());

	/**
	 * 配置属性
	 * */
	private MethodcacheProperties methodcacheProperties;

	/**
	 * redis工具类
	 * */
	private RedisUtil redisUtil;

	/**
	 * 输出缓存日志
	 * */
	private boolean enableLog;


	public RedisDataHelper(MethodcacheProperties methodcacheProperties, RedisUtil redisUtil) {
		this.methodcacheProperties = methodcacheProperties;
		this.redisUtil = redisUtil;
		this.enableLog = methodcacheProperties.isEnableLog();
	}

	@Override
	public Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark){

		String methodSignature = method.toGenericString(); // 方法签名
		int methodSignatureHashCode = methodSignature.hashCode(); // 方法入参哈希
		int argsHashCode = DataUtil.getArgsHashCode(args); // 入参哈希
		String argsInfo = Arrays.toString(args); // 入参信息
		int cacheHashCode = DataUtil.hash(String.valueOf(methodSignatureHashCode) + String.valueOf(argsHashCode)); // 缓存哈希

		if(StringUtils.isEmpty(id)){
			id = String.valueOf( methodSignature.hashCode());
		}

		String cacheKey = methodSignature + CACHE_KEY_SEPARATION_CHARACTER + cacheHashCode + CACHE_KEY_SEPARATION_CHARACTER + id;
		String redisLockKey = getLockKey(cacheKey);

		CacheDataModel cacheDataModel;

			cacheDataModel = getDataFromRedis(cacheKey,false);
			log(String.format(  "\n ************* CacheData *************" +
								"\n ** ------- 从Redis获取缓存 -------- **" +
								"\n ** 方法签名：%s" +
								"\n ** 方法入参：%s" +
								"\n ** 缓存命中：%s" +
								"\n ** 过期时间：%s" +
								"\n *************************************",
					methodSignature,
					argsInfo,
					cacheDataModel != null ? "是":"否",
					(cacheDataModel == null ? "无" : formatDate(cacheDataModel.getExpireTime()))));

		if (cacheDataModel == null || cacheDataModel.isExpired()) {
			try {
				// 没有获取到数据或者数据已过期，加锁再次尝试获取
				while (!redisUtil.lock(redisLockKey)) {}
				cacheDataModel = getDataFromRedis(cacheKey,false);
				log(String.format(	"\n ************* CacheData *************" +
									"\n ** ------ 从Redis获取缓存(加锁) ---- **" +
									"\n ** 方法签名：%s" +
									"\n ** 方法入参：%s" +
									"\n ** 缓存命中：%s" +
									"\n ** 过期时间：%s" +
									"\n *************************************",
						methodSignature,
						argsInfo,
						cacheDataModel != null ? "是":"否",
						(cacheDataModel == null ? "无" : formatDate(cacheDataModel.getExpireTime()))));

				if (cacheDataModel == null || cacheDataModel.isExpired()) {
					// 没获取到数据或者数据已过期，发起实际请求

					Object data = actualDataFunctional.getActualData();
					log(String.format(	"\n ************* CacheData *************" +
										"\n ** ----------- 发起请求 ----------- **" +
										"\n ** 方法签名：%s" +
										"\n ** 方法入参：%s" +
										"\n ** 返回数据：%s" +
										"\n *************************************",
							methodSignature,
							argsInfo,
							data));

					if (data != null) {
						long expirationTime = actualDataFunctional.getExpirationTime();
						log(String.format(	"\n ************* CacheData *************" +
											"\n ** -------- 设置缓存至Redis ------- **" +
											"\n ** 方法签名：%s" +
											"\n ** 方法入参：%s" +
											"\n ** 缓存数据：%s" +
											"\n ** 过期时间：%s" +
											"\n *************************************",
								methodSignature,
								argsInfo,
								data,
								formatDate(expirationTime)));

						setDataToRedis(cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, data, expirationTime, id, remark);
					}

					return data;
				}
			} catch (Throwable throwable) {
				throwable.printStackTrace();
				logger.info("\n ************* CacheData *************" +
							"\n ** ------- 获取数据发生异常 -------- **" +
							"\n ** 异常信息：" + throwable.getMessage() +
							"\n *************************************");
				return null;
			} finally {
				redisUtil.unlock(redisLockKey);
			}
		}

		if (refreshData) {

			final String finalId = id;

			// 刷新数据
			executorService.execute(() -> {
				try {
					while (!redisUtil.lock(redisLockKey)) {
					}
					Object data = actualDataFunctional.getActualData();
					if (data != null) {
						long expirationTime = actualDataFunctional.getExpirationTime();
						log(String.format("\n ************* CacheData *************" +
										"\n ** -------- 刷新缓存至Redis ------- **" +
										"\n 方法签名：%s" +
										"\n 方法入参：%s" +
										"\n 缓存数据：%s" +
										"\n 过期时间：%s" +
										"\n *************************************",
								methodSignature,
								Arrays.toString(args),
								data,
								formatDate(expirationTime)));
						setDataToRedis(cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, data, expirationTime, finalId, remark);
					}
				} catch (Throwable throwable) {
					throwable.printStackTrace();
					logger.info("\n ************* CacheData *************" +
							"\n ** -- 异步更新数据至Redis发生异常 --- **" +
							"\n ** 异常信息：" + throwable.getMessage() +
							"\n *************************************");
				} finally {
					redisUtil.unlock(redisLockKey);
				}
			});
		}

		return cacheDataModel.getData();


	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Map<String,Object>> getCaches(String match,String select){

		Map<String, Map<String,Object>> cacheMap = new HashMap<>();

		Set<String> cacheKeys = new HashSet<>();
		if(StringUtils.isEmpty(match)){
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null,null,null)));
		}else {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(match,null,null)));
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null,match,null)));
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null,null,match)));
		}

		Set<CacheDataModel> dataModelSet = getCacheDataModel(cacheKeys);

		for (CacheDataModel dataModel : dataModelSet) {
			if(dataModel == null || dataModel.isExpired()){
				continue;
			}

			filterDataModel(cacheMap, dataModel, select);
		}

		return cacheMap;
	}

	/**
	 * 构建缓存key规则
	 *
	 * @param methodSignature 方法签名
	 * @param cacheHashCode 入参哈希
	 * @param id id
	 * */
	private String buildCacheKeyPattern(String methodSignature, String cacheHashCode, String id){
		String cacheKeyPattern = "%{methodSignature}%" + CACHE_KEY_SEPARATION_CHARACTER + "%{cacheHashCode}%" + CACHE_KEY_SEPARATION_CHARACTER + "%{id}%";

		if(!StringUtils.isEmpty(methodSignature)){
			cacheKeyPattern = cacheKeyPattern.replace("%{methodSignature}%", "*" + methodSignature + "*");
		}else {
			cacheKeyPattern = cacheKeyPattern.replace("%{methodSignature}%", "*");
		}

		if(!StringUtils.isEmpty(cacheHashCode)){
			cacheKeyPattern = cacheKeyPattern.replace("%{cacheHashCode}%", "*" + cacheHashCode + "*");
		}else {
			cacheKeyPattern = cacheKeyPattern.replace("%{cacheHashCode}%", "*");
		}

		if(!StringUtils.isEmpty(id)){
			cacheKeyPattern = cacheKeyPattern.replace("%{id}%", "*" + id + "*");
		}else {
			cacheKeyPattern = cacheKeyPattern.replace("%{id}%", "*");
		}
		return cacheKeyPattern;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Map<String, Object>> wipeCache(String id, String cacheHashCode) {

		Map<String, Map<String, Object>> delCacheMap = new HashMap<>();

		Set<String> cacheKeys = new HashSet<>();

		if(StringUtils.isEmpty(id) && StringUtils.isEmpty(cacheHashCode)){
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null, null, null)));
		}else {
			if (!StringUtils.isEmpty(id)) {
				cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null, null, id)));
			}

			if (!StringUtils.isEmpty(cacheHashCode)) {
				cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null,cacheHashCode, null)));
			}
		}

		Set<CacheDataModel> dataModelSet = getCacheDataModel(cacheKeys);
		for (CacheDataModel dataModel : dataModelSet) {
			if (dataModel == null || dataModel.isExpired()) {
				continue;
			}

			String methodSignature = dataModel.getMethodSignature();
			int methodSignatureHashCode = methodSignature.hashCode(); // 方法入参哈希
			String argsHashCode = String.valueOf(dataModel.getArgsHashCode());
			String cacheDataId = dataModel.getId();
			String cacheKey = methodSignature + CACHE_KEY_SEPARATION_CHARACTER + DataUtil.hash(String.valueOf(methodSignatureHashCode) + String.valueOf(argsHashCode)) + CACHE_KEY_SEPARATION_CHARACTER + cacheDataId;
			String redisLockKey = getLockKey(cacheKey);
			try {
				redisUtil.lock(redisLockKey);
				if(!dataModel.isExpired()){
					filterDataModel(delCacheMap, dataModel, "");
					dataModel.expired();
					deleteDataFromRedis(cacheKey);
				}
			} catch (Throwable throwable) {
				throwable.printStackTrace();
			} finally {
				redisUtil.unlock(redisLockKey);
			}
		}
		return delCacheMap;
	}

	@SuppressWarnings("unchecked")
	private Set<CacheDataModel> getCacheDataModel(Set<String> cacheKeys){

		Set<CacheDataModel> dataModelSet = new HashSet<>();

		if(cacheKeys.size() <= 0){
			return dataModelSet;
		}

		CountDownLatch countDownLatch = new CountDownLatch(cacheKeys.size());

		for(String cacheKey : cacheKeys){
			executorService.execute(() -> {
				try {
					dataModelSet.add(getDataFromRedis(cacheKey,true));
				} catch (Exception e) {
					logger.error("从Redis批量查询缓存出现异常：" + e.getMessage());
				} finally {
					countDownLatch.countDown();
				}
			});

		}

		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
			logger.error("查询缓存被中断：" + e.getMessage());
		}

		return dataModelSet;
	}

	/**
	 * 从 Redis 获取数据
	 *
	 * @param cacheKey 缓存key
	 * @param origKeyFlag 原始key标识
	 * @result 缓存数据
	 * */
	private CacheDataModel getDataFromRedis(String cacheKey,boolean origKeyFlag) {
		String key;
		if(origKeyFlag){
			key= cacheKey;
		}else {
			key = getCacheKey(cacheKey);
		}
		Object objectByteString = redisUtil.get(key);
		if(objectByteString instanceof String){
			Object dataModel = SerializeUtil.deserialize(string2ByteArray((String) objectByteString));
			if(dataModel instanceof CacheDataModel){
				return (CacheDataModel)dataModel;
			}
		}
		return null;
	}


	/**
	 * 缓存数据至Redis
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * @param cacheHashCode 缓存哈希
	 * @param args 入参
	 * @param data 返回数据
	 * @param expireTimeStamp 过期时间
	 * */
	private void setDataToRedis(String cacheKey, String methodSignature, int methodSignatureHashCode, String args, int argsHashCode, int cacheHashCode, Object data, long expireTimeStamp, String id, String remark) {
		CacheDataModel cacheDataModel = new CacheDataModel(methodSignature, methodSignatureHashCode, args, argsHashCode, cacheHashCode, data, expireTimeStamp);

		if(!StringUtils.isEmpty(id)){
			cacheDataModel.setId(id);
		}

		if(!StringUtils.isEmpty(remark)){
			cacheDataModel.setRemark(remark);
		}

		setDataToRedis(cacheKey, cacheDataModel,expireTimeStamp - new Date().getTime());
	}

	/**
	 * 缓存数据至 Redis
	 * 这里会对返回值进行反序列化
	 * */
	private void setDataToRedis(String cacheKey, CacheDataModel cacheDataModel, long timeout) {
		redisUtil.set(getCacheKey(cacheKey), byteArray2String(SerializeUtil.serizlize(cacheDataModel)), timeout);
	}

	/**
	 * 缓存数据至 Redis
	 * 这里会对返回值进行反序列化
	 * */
	private void deleteDataFromRedis(String cacheKey) {
		redisUtil.del(getCacheKey(cacheKey));
	}


	private String getLockKey(String key){
		return REDIS_LOCK_PREFIX + METHOD_CACHE_DATA + key;
	}

	private String getCacheKey(String key){
		return METHOD_CACHE_DATA + CACHE_KEY_SEPARATION_CHARACTER + key;
	}

	private String byteArray2String(byte[] bytes){
		return Base64.getEncoder().encodeToString(bytes);
	}

	private byte[] string2ByteArray(String str){
		return Base64.getDecoder().decode(str);
	}

	private void log(String info){
		if(enableLog){
			logger.info(info);
		}
	}
}
