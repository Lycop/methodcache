package love.kill.methodcache.datahelper.impl;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.SpringApplicationProperties;
import love.kill.methodcache.datahelper.CacheDataModel;
import love.kill.methodcache.datahelper.CacheStatisticsModel;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.DataUtil;
import love.kill.methodcache.util.RedisUtil;
import love.kill.methodcache.util.SerializeUtil;
import love.kill.methodcache.util.ThreadPoolBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public class RedisDataHelper implements DataHelper {

	private static Logger logger = LoggerFactory.getLogger(RedisDataHelper.class);

	/**
	 * 配置属性
	 */
	private final MethodcacheProperties methodcacheProperties;

	/**
	 * 应用名
	 * */
	private String applicationName;

	/**
	 * redis工具类
	 */
	private RedisUtil redisUtil;

	/**
	 * 锁前缀
	 */
	private static final String REDIS_LOCK_PREFIX = "REDIS_LOCK_";

	/**
	 * 执行线程
	 */
	private static final ExecutorService executorService = ThreadPoolBuilder.buildDefaultThreadPool();


	public RedisDataHelper(MethodcacheProperties methodcacheProperties,
						   SpringApplicationProperties springApplicationProperties, RedisUtil redisUtil) {
		this.redisUtil = redisUtil;
		this.methodcacheProperties = methodcacheProperties;

		if (StringUtils.isEmpty(this.applicationName = methodcacheProperties.getName())) {
			this.applicationName = springApplicationProperties.getName();
		}

		if (methodcacheProperties.isEnableStatistics()) {
			Executors.newSingleThreadExecutor().execute(() -> {
				while (true) {
					try {
						CacheStatisticsNode statisticsNode = cacheStatisticsInfoQueue.take();
						String cacheKey = statisticsNode.getCacheKey();

						String statisticsLockKey = getIntactCacheStatisticsLockKey(cacheKey);
						try {
							redisUtil.lock(statisticsLockKey, methodcacheProperties.getRedisLockTimeout(), true);
							String methodSignature = statisticsNode.getMethodSignature();
							CacheStatisticsModel statisticsModel =
									increaseStatistics(getCacheStatistics(methodSignature), statisticsNode);
							setCacheStatistics(methodSignature, statisticsModel);
						} finally {
							redisUtil.unlock(statisticsLockKey);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
	}

	@Override
	public Object getData(Object proxy, Method method, Object[] args, String isolationSignal, boolean refreshData,
						  ActualDataFunctional actualDataFunctional, String id, String remark,
						  boolean nullable, boolean shared) throws Throwable {

		long startTime = new Date().getTime();
		String methodSignature = method.toGenericString(); // 方法签名
		int methodSignatureHashCode = methodSignature.hashCode(); // 方法签名哈希
		int argsHashCode = DataUtil.getArgsHashCode(args); // 方法入参哈希
		String argsInfo = Arrays.toString(args); // 方法入参信息
		int cacheHashCode = getCacheHashCode(applicationName, methodSignatureHashCode, argsHashCode, isolationSignal); // 缓存哈希值
		if (StringUtils.isEmpty(id)) {
			id = String.valueOf(methodSignature.hashCode());
		}
		String cacheKey = getCacheKey(applicationName, methodSignature, cacheHashCode, id); // 构建缓存key
		String dataLockKey = getIntactDataLockKey(cacheKey); // 数据锁
		CacheDataModel cacheDataModel = getDataFromRedis(cacheKey, false, shared);
		boolean hit = (cacheDataModel != null && !cacheDataModel.isExpired());
		log(String.format(	"\n ************* CacheData *************" +
							"\n ** ------- 从Redis获取缓存 -------- **" +
							"\n ** 执行对象：%s" +
							"\n ** 方法签名：%s" +
							"\n ** 方法入参：%s" +
							"\n ** 缓存命中：%s" +
							"\n ** 过期时间：%s" +
							"\n *************************************",
				proxy,
				methodSignature,
				argsInfo,
				hit ? "是" : "否",
				hit ? formatDate(cacheDataModel.getExpireTime()) : "无"));

		if (!hit) {
			try {
				// 缓存未命中或数据已过期，加锁再次尝试获取
				redisUtil.lock(dataLockKey, methodcacheProperties.getRedisLockTimeout(), true);
				cacheDataModel = getDataFromRedis(cacheKey, false, shared);
			}finally {
				redisUtil.unlock(dataLockKey);
			}

			hit = (cacheDataModel != null && !cacheDataModel.isExpired());
			log(String.format(	"\n ************* CacheData *************" +
								"\n ** ------ 从Redis获取缓存(加锁) ---- **" +
								"\n ** 执行对象：%s" +
								"\n ** 方法签名：%s" +
								"\n ** 方法入参：%s" +
								"\n ** 缓存命中：%s" +
								"\n ** 过期时间：%s" +
								"\n *************************************",
					proxy,
					methodSignature,
					argsInfo,
					hit ? "是" : "否",
					hit ? formatDate(cacheDataModel.getExpireTime()) : "无"));


			if (!hit) {
				// 发起实际请求
				Object actualData;
				try {
					actualData = actualDataFunctional.getActualData();
					log(String.format(	"\n ************* CacheData *************" +
										"\n ** ----------- 发起请求 ----------- **" +
									    "\n ** 执行对象：%s" +
										"\n ** 方法签名：%s" +
										"\n ** 方法入参：%s" +
										"\n ** 返回数据：%s" +
										"\n *************************************",
							proxy,
							methodSignature,
							argsInfo,
							actualData));
				} catch (Throwable throwable) {
					throwable.printStackTrace();
					String uuid = UUID.randomUUID().toString().trim().replaceAll("-", "");
					logger.info("\n ************* CacheData *************" +
								"\n ** ------- 获取数据发生异常 -------- **" +
								"\n ** 异常信息(UUID=" + uuid + ")：" + throwable.getMessage() + "\n" + printStackTrace(throwable.getStackTrace()) +
								"\n *************************************");

					if (methodcacheProperties.isEnableStatistics()) {
						recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode,
								cacheHashCode, id, remark, false, true, printStackTrace(throwable, uuid), startTime,
								new Date().getTime());
					}

					throw throwable;
				}


				if (methodcacheProperties.isEnableStatistics()) {
					recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode,
							cacheHashCode, id, remark, hit, false, "", startTime, new Date().getTime());
				}

				if (isNotNull(actualData, nullable)) {
					long expirationTime = actualDataFunctional.getExpirationTime();
					refreshData(proxy, actualData, expirationTime, applicationName, dataLockKey, actualDataFunctional,
							nullable, cacheKey, methodSignature, argsInfo, cacheHashCode, id, remark);
				}
				return actualData;
			}

		}

		if (methodcacheProperties.isEnableStatistics()) {
			recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode,
					id, remark, hit, false, "", startTime, new Date().getTime());
		}

		if (refreshData) {
			refreshData(proxy, null, -1, applicationName, dataLockKey, actualDataFunctional, nullable, cacheKey,
					methodSignature, argsInfo, cacheHashCode, id, remark);
		}

		return cacheDataModel.getData();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Map<String, Object>> getCaches(String match) {

		Map<String, Map<String, Object>> cacheMap = new HashMap<>();

		Set<String> cacheKeys = new HashSet<>();
		if (StringUtils.isEmpty(match)) {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(applicationName, null, null, null)));
		} else {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(applicationName, match, null, null)));
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(applicationName, null, match, null)));
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(applicationName, null, null, match)));
		}

		Set<CacheDataModel> dataModelSet = getCacheDataModel(cacheKeys);

		for (CacheDataModel dataModel : dataModelSet) {
			if (dataModel != null && !dataModel.isExpired()) {
				filterDataModel(cacheMap, dataModel, null);
			}
		}

		return cacheMap;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Map<String, Object>> wipeCache(String id, String cacheHashCode) {

		Map<String, Map<String, Object>> delCacheMap = new HashMap<>();

		Set<String> cacheKeys = new HashSet<>();

		if (StringUtils.isEmpty(id) && StringUtils.isEmpty(cacheHashCode)) {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(applicationName, null, null, null)));
		} else {
			if (!StringUtils.isEmpty(id)) {
				cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(applicationName, null, null, id)));
			}
			if (!StringUtils.isEmpty(cacheHashCode)) {
				cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(applicationName, null, cacheHashCode, null)));
			}
		}

		Set<CacheDataModel> dataModelSet = getCacheDataModel(cacheKeys);
		for (CacheDataModel dataModel : dataModelSet) {
			if (dataModel == null || dataModel.isExpired()) {
				continue;
			}

			String cacheKey = getCacheKey(dataModel.getApplicationName(), dataModel.getMethodSignature(),
					dataModel.getCacheHashCode(), dataModel.getId()); // 缓存key
			String redisDataLockKey = getIntactDataLockKey(cacheKey);
			try {
				redisUtil.lock(redisDataLockKey, methodcacheProperties.getRedisLockTimeout(), true);
				if (!dataModel.isExpired()) {
					dataModel.expired();
				}
				filterDataModel(delCacheMap, dataModel, "");
				deleteDataFromRedis(cacheKey);
			} catch (Throwable throwable) {
				throwable.printStackTrace();
			} finally {
				redisUtil.unlock(redisDataLockKey);
			}
		}
		return delCacheMap;
	}

	@Override
	public Map<String, CacheStatisticsModel> getCacheStatistics() {
		return getStatisticsFromRedis();
	}

	@Override
	public CacheStatisticsModel getCacheStatistics(String methodSignature) {
		Map<String, CacheStatisticsModel> statisticsFromRedis = getStatisticsFromRedis();
		if (statisticsFromRedis == null) {
			return null;
		}
		return statisticsFromRedis.get(methodSignature);
	}

	@Override
	public void setCacheStatistics(String methodSignature, CacheStatisticsModel cacheStatisticsModel) {
		setStatisticsToRedis(methodSignature, cacheStatisticsModel);
	}

	@Override
	public void wipeStatistics(CacheStatisticsModel statisticsModel) {
		String statisticsLockKey = getIntactCacheStatisticsLockKey(statisticsModel.getCacheKey());
		try {
			redisUtil.lock(statisticsLockKey, methodcacheProperties.getRedisLockTimeout(), true);
			deleteStatisticsFromRedis(statisticsModel.getMethodSignature());
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			redisUtil.unlock(statisticsLockKey);
		}
	}

	@Override
	public Map<String, CacheStatisticsModel> wipeStatisticsAll() {
		Map<String, CacheStatisticsModel> resultMap = getCacheStatistics();
		deleteStatisticsAllFromRedis();
		return resultMap;
	}

	/****************************************************************** 私有方法 start ******************************************************************/

	/**
	 * 刷新数据
	 *
	 * @param proxy        			  执行对象
	 * @param data        			  数据
	 * @param expirationTime          数据过期时间
	 * @param redisDataLockKey        数据锁
	 * @param actualDataFunctional    真实数据请求
	 * @param nullable                返回值允许为空
	 * @param cacheKey                缓存key
	 * @param methodSignature         方法签名
	 * @param argsStr                 方法参数信息
	 * @param id                      缓存ID
	 * @param remark                  缓存备注
	 */
	private void refreshData(final Object proxy, final Object data, long expirationTime, String applicationName, String redisDataLockKey,
							 ActualDataFunctional actualDataFunctional, boolean nullable, String cacheKey,
							 String methodSignature, String argsStr, int cacheHashCode, String id, String remark) {
		executorService.execute(() -> {

			Object saveData;
			long saveExpirationTime;

			if(data != null){
				saveData = data;
				saveExpirationTime = expirationTime;
			}else {
				saveData = new NullObject();
				saveExpirationTime = actualDataFunctional.getExpirationTime();
				try {
					saveData = actualDataFunctional.getActualData();
				} catch (Throwable throwable) {
					throwable.printStackTrace();
					logger.info("\n ************* CacheData *************" +
								"\n ** ---- 更新数据至Redis发生异常 ---- **" +
								"\n ** 异常信息：" + throwable.getMessage() +
								"\n *************************************");
				}

			}
			if ((isNotNull(saveData, nullable))) {
				try {
					redisUtil.lock(redisDataLockKey, methodcacheProperties.getRedisLockTimeout(), true);
					log(String.format(	"\n ************* CacheData *************" +
										"\n ** -------- 刷新缓存至Redis ------- **" +
										"\n 执行对象：%s" +
										"\n 方法签名：%s" +
										"\n 方法入参：%s" +
										"\n 缓存数据：%s" +
										"\n 过期时间：%s" +
										"\n *************************************",
								proxy,
								methodSignature,
								argsStr,
								saveData,
								formatDate(saveExpirationTime)));
					setDataToRedis(applicationName, cacheKey, methodSignature, argsStr, cacheHashCode,
							saveData != null ? saveData : new NullObject(), saveExpirationTime, id, remark);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					redisUtil.unlock(redisDataLockKey);
				}
			}
		});
	}

	/**
	 * 构建模糊搜索缓存key
	 *
	 * 缓存哈希规则： 应用名@方法签名@缓存哈希值@缓存ID;
	 *
	 * @param applicationName 应用名
	 * @param methodSignature 方法签名
	 * @param cacheHashCode   缓存哈希值
	 * @param id              缓存ID
	 */
	private String buildCacheKeyPattern(String applicationName, String methodSignature, String cacheHashCode, String id) {
		String cacheKeyPattern = "%{methodSignature}%" + KEY_SEPARATION_CHARACTER + "%{cacheHashCode}%"
				+ KEY_SEPARATION_CHARACTER + "%{id}%";

		if(!StringUtils.isEmpty(applicationName)){
			cacheKeyPattern = applicationName + KEY_SEPARATION_CHARACTER + cacheKeyPattern;
		}

		cacheKeyPattern = METHOD_CACHE_DATA + KEY_SEPARATION_CHARACTER + cacheKeyPattern;

		if (!StringUtils.isEmpty(methodSignature)) {
			cacheKeyPattern = cacheKeyPattern.replace("%{methodSignature}%", "*" + methodSignature + "*");
		} else {
			cacheKeyPattern = cacheKeyPattern.replace("%{methodSignature}%", "*");
		}

		if (!StringUtils.isEmpty(cacheHashCode)) {
			cacheKeyPattern = cacheKeyPattern.replace("%{cacheHashCode}%", "*" + cacheHashCode + "*");
		} else {
			cacheKeyPattern = cacheKeyPattern.replace("%{cacheHashCode}%", "*");
		}

		if (!StringUtils.isEmpty(id)) {
			cacheKeyPattern = cacheKeyPattern.replace("%{id}%", id);
		} else {
			cacheKeyPattern = cacheKeyPattern.replace("%{id}%", "*");
		}

		return cacheKeyPattern;
	}

	/**
	 * 从Redis获取数据
	 *
	 * @param cacheKey      	缓存key
	 * @param intactKeyFlag 	完整key标识
	 * @param shared 			共享式数据
	 * @result 缓存数据
	 */
	private CacheDataModel getDataFromRedis(String cacheKey, boolean intactKeyFlag, boolean shared) {

		String key;
		if (intactKeyFlag) {
			key = cacheKey;
		} else {
			key = getIntactCacheDataKey(cacheKey);
		}

		Object objectByteString = redisUtil.get(key);
		if (!(objectByteString instanceof String)) {
			return null;
		}

		Object dataModel = SerializeUtil.deserialize(SerializeUtil.string2ByteArray((String) objectByteString));
		if (!(dataModel instanceof CacheDataModel)) {
			return null;
		}

		CacheDataModel cacheDataModel = (CacheDataModel) dataModel;

		if(!shared){
			// 独享数据
			return cacheDataModel;
		}

		return DataHelper.decisionCacheDataModel(cacheDataModel);
	}

	/**
	 * 缓存数据至Redis
	 *
	 * @param applicationName         应用名
	 * @param cacheKey                缓存key
	 * @param methodSignature         方法签名
	 * @param argStr                  方法入参
	 * @param cacheHashCode           缓存哈希
	 * @param data                    数据
	 * @param expireTimeStamp         过期时间
	 * @param id                      缓存ID
	 * @param remark                  缓存备注
	 */
	private void setDataToRedis(String applicationName, String cacheKey, String methodSignature, String argStr,
								int cacheHashCode, Object data, long expireTimeStamp, String id, String remark) {

		CacheDataModel cacheDataModel = new CacheDataModel(applicationName, methodSignature, argStr, cacheHashCode,
				data, expireTimeStamp);

		if (!StringUtils.isEmpty(id)) {
			cacheDataModel.setId(id);
		}

		if (!StringUtils.isEmpty(remark)) {
			cacheDataModel.setRemark(remark);
		}

		setDataToRedis(cacheKey, cacheDataModel, expireTimeStamp - new Date().getTime());
	}

	/**
	 * 获取缓存统计
	 *
	 * @return 缓存统计信息
	 */
	@SuppressWarnings("unchecked")
	private Map<String, CacheStatisticsModel> getStatisticsFromRedis() {
		Map<String, CacheStatisticsModel> resultMap = new HashMap<>();
		List<Object> objects = redisUtil.hValues(METHOD_CACHE_STATISTICS);
		if (objects == null) {
			return null;
		}

		for (Object object : objects) {
			if (object instanceof String) {
				Object model = SerializeUtil.deserialize(SerializeUtil.string2ByteArray((String) object));
				if (model instanceof CacheStatisticsModel) {
					CacheStatisticsModel statisticsModel = (CacheStatisticsModel) model;
					resultMap.put(statisticsModel.getMethodSignature(), statisticsModel);
				}
			}
		}

		return resultMap;
	}

	/**
	 * 获取缓存统计
	 *
	 * @return 缓存统计信息
	 */
	@SuppressWarnings("unchecked")
	private void deleteStatisticsAllFromRedis() {
		redisUtil.del(METHOD_CACHE_STATISTICS);
	}

	/**
	 * 获取匹配的数据模型
	 *
	 * @param cacheKeys 缓存key
	 * @return 匹配的数据
	 */
	@SuppressWarnings("unchecked")
	private Set<CacheDataModel> getCacheDataModel(Set<String> cacheKeys) {

		Set<CacheDataModel> dataModelSet = new HashSet<>();

		if (cacheKeys.size() <= 0) {
			return dataModelSet;
		}

		CountDownLatch countDownLatch = new CountDownLatch(cacheKeys.size());

		for (String cacheKey : cacheKeys) {
			executorService.execute(() -> {
				try {
					dataModelSet.add(getDataFromRedis(cacheKey, true, false));
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
	 * 获取完整的数据锁key
	 */
	private static String getIntactDataLockKey(String key) {
		return REDIS_LOCK_PREFIX + METHOD_CACHE_DATA + KEY_SEPARATION_CHARACTER + key;
	}

	/**
	 * 获取缓存数据key
	 */
	private static String getIntactCacheDataKey(String key) {
		return METHOD_CACHE_DATA + KEY_SEPARATION_CHARACTER + key;
	}

	/**
	 * 获取完整的数据锁key
	 */
	private static String getIntactCacheStatisticsLockKey(String key) {
		return REDIS_LOCK_PREFIX + METHOD_CACHE_STATISTICS + KEY_SEPARATION_CHARACTER + key;
	}

	/**
	 * 保存数据至Redis
	 * 这里会对返回值进行反序列化
	 */
	private void setDataToRedis(String cacheKey, CacheDataModel cacheDataModel, long timeout) {
		redisUtil.set(getIntactCacheDataKey(cacheKey), SerializeUtil.byteArray2String(SerializeUtil.serizlize(cacheDataModel)), timeout);
	}

	/**
	 * 保存数据至 Redis
	 * 这里会对返回值进行反序列化
	 */
	private void deleteDataFromRedis(String cacheKey) {
		redisUtil.del(getIntactCacheDataKey(cacheKey));
	}

	/**
	 * 保存缓存统计信息至Redis
	 * Redis缓存信息模型(hash)
	 * "METHOD_CACHE_STATISTICS":{
	 * 方法签名:(序列化后的)统计信息
	 * }
	 */
	private void setStatisticsToRedis(String methodSignature, CacheStatisticsModel cacheStatisticsModel) {
		redisUtil.hset(METHOD_CACHE_STATISTICS, methodSignature,
				SerializeUtil.byteArray2String(SerializeUtil.serizlize(cacheStatisticsModel)));
	}

	/**
	 * 保存缓存统计信息至Redis
	 * Redis缓存信息模型(hash)
	 * "METHOD_CACHE_STATISTICS":{
	 * 方法签名:(序列化后的)统计信息
	 * }
	 */
	private void deleteStatisticsFromRedis(String methodSignature) {
		redisUtil.hdel(METHOD_CACHE_STATISTICS, methodSignature);
	}

	/**
	 * 日志记录
	 */
	private void log(String info) {
		if (methodcacheProperties.isEnableLog()) {
			logger.info(info);
		}
	}

	/****************************************************************** 私有方法  end  ******************************************************************/
}
