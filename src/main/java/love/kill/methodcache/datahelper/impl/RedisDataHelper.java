package love.kill.methodcache.datahelper.impl;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.SpringApplicationProperties;
import love.kill.methodcache.datahelper.CacheDataModel;
import love.kill.methodcache.datahelper.CacheStatisticsModel;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.*;
import org.aopalliance.intercept.MethodInvocation;
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
	 * 应用名
	 */
	private String applicationName;

	/**
	 * 配置属性
	 */
	private final MethodcacheProperties methodcacheProperties;

	/**
	 * redis工具类
	 */
	private RedisUtil redisUtil;

	/**
	 * 缓存数据模型执行线程
	 */
	private static final ExecutorService cacheDataModelExecutorService = ThreadPoolBuilder.buildDefaultThreadPool();

	/**
	 * 锁前缀
	 */
	private static final String REDIS_LOCK_PREFIX = "REDIS_LOCK_";

	/**
	 * 异常方法调用 key
	 */
	private static final String EXCEPTION_METHOD_INVOCATION = "EXCEPTION_METHOD_INVOCATION";


	public RedisDataHelper(MethodcacheProperties methodcacheProperties,
						   SpringApplicationProperties springApplicationProperties, RedisUtil redisUtil) {
		this.redisUtil = redisUtil;
		this.methodcacheProperties = methodcacheProperties;

		if (StringUtils.isEmpty(this.applicationName = methodcacheProperties.getName())) {
			this.applicationName = springApplicationProperties.getName();
		}

		if (StringUtils.isEmpty(this.applicationName)) {
			logger.warn("请注意，项目未指定应用名，这可能会导致不同项目之间出现缓存干扰。可通过配置\"${methodcache.name}或${spring.application.name}\"解决此问题。");
		}

		if (methodcacheProperties.isEnableStatistics()) {
			// 统计缓存请求
			Executors.newSingleThreadExecutor().execute(() -> {
				while (true) {
					try {
						CacheStatisticsNode statisticsNode = cacheStatisticsInfoQueue.take();
						String cacheKey = statisticsNode.getCacheKey();

						String statisticsLockKey = getIntactCacheStatisticsLockKey(getStatisticsRedisKey(), cacheKey);
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
	public CacheDataModel getData(Object proxy, MethodInvocation methodInvocation, String isolationSignal, boolean refreshData,
						  ActualDataFunctional actualDataFunctional, String id, String remark, boolean cacheNull,
						  boolean shared, Class cacheDataAssert) throws Throwable {

		Method method = methodInvocation.getMethod();
		Object[] arguments = methodInvocation.getArguments();
		long startTime = new Date().getTime();
		String methodSignature = method.toGenericString(); // 方法签名
		int methodSignatureHashCode = methodSignature.hashCode(); // 方法签名哈希
		int argsHashCode = DataUtil.getArgsHashCode(arguments); // 方法入参哈希
		String args = Arrays.toString(arguments); // 方法入参信息
		int cacheHashCode = getCacheHashCode(methodSignatureHashCode, argsHashCode, isolationSignal); // 缓存哈希值
		if (StringUtils.isEmpty(id)) {
			id = String.valueOf(methodSignature.hashCode());
		}
		String cacheKey = getCacheKey(methodSignature, cacheHashCode, id); // 构建缓存key
		String dataLockKey = getIntactDataLockKey(cacheKey); // 数据锁

		CacheDataModel cacheDataModel = getDataFromRedis(cacheKey, false, shared);
		boolean hit = cacheDataModel != null && !cacheDataModel.isExpired();
		boolean approved = hit && doCacheDataAssert(cacheDataModel.getData(), cacheDataAssert);
		if (!hit || !approved) {
			if(!hit){
				// 缓存未命中或数据已过期，加锁再次尝试获取
				try {
					redisUtil.lock(dataLockKey, methodcacheProperties.getRedisLockTimeout(), true);
					cacheDataModel = getDataFromRedis(cacheKey, false, shared);
					hit = (cacheDataModel != null && !cacheDataModel.isExpired());
					approved = hit && doCacheDataAssert(cacheDataModel.getData(), cacheDataAssert);
				} finally {
					redisUtil.unlock(dataLockKey);
				}
			}

			if (!hit || !approved) {
				// 发起实际请求
				ActualDataModel actualDataModel;
				Object actualData; // 实际请求返回的数据
				long expireTimeStamp;
				try {
					actualDataModel = actualDataFunctional.getActualData();
					actualData = actualDataModel.getData();
					expireTimeStamp = actualDataModel.getExpirationTime();

					cacheDataModel = new CacheDataModel(getCacheName(), methodSignature, args, cacheHashCode,
							actualData, expireTimeStamp, id, remark);

				} catch (Throwable throwable) {
					if (methodcacheProperties.isEnableStatistics()) {
						recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, args, argsHashCode,
								cacheHashCode, id, remark, false, true, printStackTrace(throwable), startTime,
								new Date().getTime());
					}

					throw throwable;
				}


				if (methodcacheProperties.isEnableStatistics()) {
					recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, args, argsHashCode,
							cacheHashCode, id, remark, hit, false, "", startTime, new Date().getTime());
				}

				if (isNotNull(actualData, cacheNull) && actualDataModel.isSucceeded()) {
					refreshData(proxy, cacheDataModel, actualDataFunctional, cacheNull);
				}

				return cacheDataModel;
			}

		}

		if (methodcacheProperties.isEnableStatistics()) {
			recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, args, argsHashCode, cacheHashCode,
					id, remark, hit, false, "", startTime, new Date().getTime());
		}

		if (refreshData) {
			CacheDataModel refreshCacheDataModel = new CacheDataModel(getCacheName(), methodSignature, args, cacheHashCode,
					null, 0, id, remark);
			refreshData(proxy, refreshCacheDataModel, actualDataFunctional, cacheNull);
		}

		return cacheDataModel;
	}

	@Override
	public void doRefreshData(Object proxy, CacheDataModel cacheDataModel) {

		String id = cacheDataModel.getId();
		Object data = cacheDataModel.getData();
		String methodSignature = cacheDataModel.getMethodSignature();
		String args = cacheDataModel.getArgs();
		int cacheHashCode = cacheDataModel.getCacheHashCode();
		long expireTime = cacheDataModel.getExpireTime();

		String cacheKey = getCacheKey(methodSignature, cacheHashCode, id);
		String dataLockKey = getIntactDataLockKey(cacheKey);

		try {
			redisUtil.lock(dataLockKey, methodcacheProperties.getRedisLockTimeout(), true);
			log(String.format(	"\n ************* CacheData *************" +
								"\n ** -------- 刷新缓存至Redis ------- **" +
								"\n ** 执行对象：%s" +
								"\n ** 方法签名：%s" +
								"\n ** 方法入参：%s" +
								"\n ** 缓存数据：%s" +
								"\n ** 过期时间：%s" +
								"\n *************************************",
					proxy,
					methodSignature,
					args,
					data,
					formatDate(expireTime)));

			setDataToRedis(cacheKey, cacheDataModel);

		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			redisUtil.unlock(dataLockKey);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String,Object> getCaches(String match, int pageSize, int pageNo) {

		Map<String, Object> cacheMap = new LinkedHashMap<>();
		cacheMap.put("pageSize", pageSize);
		cacheMap.put("pageNo", pageNo);

		Set<String> cacheKeys = new HashSet<>();
		if (StringUtils.isEmpty(match)) {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null, null, null)));
		} else {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(match, null, null)));
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null, match, null)));
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null, null, match)));
		}

		cacheMap.put("totalRows", cacheKeys.size());
		cacheMap.put("totalPages", cacheKeys.size() / pageSize + (cacheKeys.size() % pageSize == 0 ? 0 : 1));

		Set<CacheDataModel> dataModelSet = getCacheDataModel(PaginationUtil.paginate(cacheKeys, pageSize, pageNo));

		for (CacheDataModel dataModel : dataModelSet) {
			if (dataModel != null && !dataModel.isExpired()) {
				filterDataModel(cacheMap, dataModel, null);
			}
		}

		return cacheMap;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> wipeCache(String id, String cacheHashCode) {

		Map<String, Object> delCacheMap = new HashMap<>();

		Set<String> cacheKeys = new HashSet<>();

		if (StringUtils.isEmpty(id) && StringUtils.isEmpty(cacheHashCode)) {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null, null, null)));
		} else {
			if (!StringUtils.isEmpty(id)) {
				cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null, null, id)));
			}
			if (!StringUtils.isEmpty(cacheHashCode)) {
				cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(null, cacheHashCode, null)));
			}
		}

		Set<CacheDataModel> dataModelSet = getCacheDataModel(cacheKeys);
		for (CacheDataModel dataModel : dataModelSet) {
			if (dataModel == null || dataModel.isExpired()) {
				continue;
			}

			String cacheKey = getCacheKey(dataModel.getMethodSignature(),
					dataModel.getCacheHashCode(), dataModel.getId()); // 缓存key
			String redisDataLockKey = getIntactDataLockKey(cacheKey);
			try {
				redisUtil.lock(redisDataLockKey, methodcacheProperties.getRedisLockTimeout(), true);
				if (!dataModel.isExpired()) {
					dataModel.expired();
				}
				filterDataModel(delCacheMap, dataModel, "");
				doDeleteDataFromRedis(cacheKey);
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
		doSetStatisticsToRedis(methodSignature, cacheStatisticsModel);
	}

	@Override
	public void wipeStatistics(CacheStatisticsModel statisticsModel) {
		String statisticsLockKey = getIntactCacheStatisticsLockKey(getStatisticsRedisKey(), statisticsModel.getCacheKey());
		try {
			redisUtil.lock(statisticsLockKey, methodcacheProperties.getRedisLockTimeout(), true);
			doDeleteStatisticsFromRedis(statisticsModel.getMethodSignature());
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

	@Override
	public String getApplicationName() {
		return this.applicationName;
	}

	@Override
	public String getMethodCacheName() {
		return this.methodcacheProperties.getName();
	}

	@Override
	public String getMethodcacheGroupName() {
		return this.methodcacheProperties.getGroupName();
	}

	/****************************************************************** 私有方法 start ******************************************************************/


	/**
	 * 构建模糊搜索缓存key
	 * <p>
	 * 缓存哈希规则： 应用名@方法签名@缓存哈希值@缓存ID;
	 *
	 * @param methodSignature 方法签名
	 * @param cacheHashCode   缓存哈希值
	 * @param id              缓存ID
	 */
	private String buildCacheKeyPattern(String methodSignature, String cacheHashCode, String id) {
		String cacheKeyPattern = METHOD_CACHE_DATA + KEY_SEPARATION_CHARACTER + getCacheName() +
				KEY_SEPARATION_CHARACTER + "%{methodSignature}%" +
				KEY_SEPARATION_CHARACTER + "%{cacheHashCode}%" +
				KEY_SEPARATION_CHARACTER + "%{id}%";

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
	 * @param cacheKey      缓存key
	 * @param intactKeyFlag 完整key标识
	 * @param shared        共享式数据
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

		Object dataModel = null;
		try {
			dataModel = SerializeUtil.deserialize(SerializeUtil.string2ByteArray((String) objectByteString));
		} catch (ClassNotFoundException e) {
			logger.warn("类({})不存在，忽略此缓存", e.getMessage());
		}

		if (!(dataModel instanceof CacheDataModel)) {
			return null;
		}

		CacheDataModel cacheDataModel = (CacheDataModel) dataModel;

		if (!shared) {
			// 独享数据
			return cacheDataModel;
		}

		return DataHelper.decisionCacheDataModel(cacheDataModel);
	}

	/**
	 * 缓存数据至Redis
	 *
	 * @param cacheKey          缓存key
	 * @param cacheDataModel	数据模型
	 */
	private void setDataToRedis(String cacheKey, CacheDataModel cacheDataModel) {

		long expireTimeStamp = cacheDataModel.getExpireTime();

		doSetDataToRedis(cacheKey, cacheDataModel, expireTimeStamp - new Date().getTime());
	}

	/**
	 * 获取缓存统计
	 *
	 * @return 缓存统计信息
	 */
	@SuppressWarnings("unchecked")
	private Map<String, CacheStatisticsModel> getStatisticsFromRedis() {
		Map<String, CacheStatisticsModel> resultMap = new HashMap<>();
		List<Object> objects = redisUtil.hValues(getStatisticsRedisKey());
		if (objects == null) {
			return null;
		}

		for (Object object : objects) {
			if (object instanceof String) {
				Object model = null;
				try {
					model = SerializeUtil.deserialize(SerializeUtil.string2ByteArray((String) object));
				} catch (ClassNotFoundException e) {
					logger.warn("类({})不存在，忽略此缓存", e.getMessage());
				}

				if (model instanceof CacheStatisticsModel) {
					CacheStatisticsModel statisticsModel = (CacheStatisticsModel) model;
					resultMap.put(statisticsModel.getMethodSignature(), statisticsModel);
				}
			}
		}

		return resultMap;
	}

	/**
	 * 清除缓存统计
	 */
	@SuppressWarnings("unchecked")
	private void deleteStatisticsAllFromRedis() {
		redisUtil.del(getStatisticsRedisKey());
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
			cacheDataModelExecutorService.execute(() -> {
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
	private static String getIntactCacheStatisticsLockKey(String statisticsRedisKey, String cacheKey) {
		return REDIS_LOCK_PREFIX + statisticsRedisKey + KEY_SEPARATION_CHARACTER + cacheKey;
	}

	/**
	 * 保存数据至Redis
	 * 这里会对返回值进行序列化
	 */
	private void doSetDataToRedis(String cacheKey, CacheDataModel cacheDataModel, long timeout) {
		redisUtil.set(getIntactCacheDataKey(cacheKey), SerializeUtil.byteArray2String(SerializeUtil.serizlize(cacheDataModel)), timeout);
	}

	/**
	 * 从 Redis 删除数据
	 */
	private void doDeleteDataFromRedis(String cacheKey) {
		redisUtil.del(getIntactCacheDataKey(cacheKey));
	}

	/**
	 * 保存缓存统计信息至Redis
	 * Redis缓存信息模型(hash)
	 * "METHOD_CACHE_STATISTICS":{
	 * 方法签名:(序列化后的)统计信息
	 * }
	 */
	private void doSetStatisticsToRedis(String methodSignature, CacheStatisticsModel cacheStatisticsModel) {
		redisUtil.hset(getStatisticsRedisKey(), methodSignature,
				SerializeUtil.byteArray2String(SerializeUtil.serizlize(cacheStatisticsModel)));
	}

	/**
	 * 保存缓存统计信息至Redis
	 * Redis缓存信息模型(hash)
	 * "METHOD_CACHE_STATISTICS":{
	 * 方法签名:(序列化后的)统计信息
	 * }
	 */
	private void doDeleteStatisticsFromRedis(String methodSignature) {
		redisUtil.hdel(getStatisticsRedisKey(), methodSignature);
	}


	/**
	 * 获取统计的 Redis key
	 */
	private String getStatisticsRedisKey() {
		return METHOD_CACHE_STATISTICS + KEY_SEPARATION_CHARACTER + getCacheName();
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
