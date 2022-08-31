package love.kill.methodcache.datahelper.impl;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.SpringApplicationProperties;
import love.kill.methodcache.datahelper.CacheDataModel;
import love.kill.methodcache.datahelper.CacheStatisticsModel;
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
	 */
	private static final String REDIS_LOCK_PREFIX = "REDIS_LOCK_";

	/**
	 * cpu个数
	 */
	private final static int CPU_COUNT = Runtime.getRuntime().availableProcessors();

	/**
	 * 执行线程
	 */
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
	 */
	private final MethodcacheProperties methodcacheProperties;

	/**
	 * spring属性
	 */
	private final SpringApplicationProperties springApplicationProperties;

	/**
	 * redis工具类
	 */
	private RedisUtil redisUtil;


	public RedisDataHelper(MethodcacheProperties methodcacheProperties, SpringApplicationProperties springApplicationProperties, RedisUtil redisUtil) {
		this.redisUtil = redisUtil;
		this.methodcacheProperties = methodcacheProperties;
		this.springApplicationProperties = springApplicationProperties;

		if (methodcacheProperties.isEnableStatistics()) {
			Executors.newSingleThreadExecutor().execute(() -> {
				while (true) {
					try {
						CacheStatisticsNode statisticsNode = cacheStatisticsInfoQueue.take();
						String cacheKey = statisticsNode.getCacheKey();

						String statisticsLockKey = getIntactCacheStatisticsLockKey(cacheKey);
						try {
							redisUtil.lock(statisticsLockKey, true);
							String methodSignature = statisticsNode.getMethodSignature();
							CacheStatisticsModel statisticsModel = increaseStatistics(getCacheStatistics(methodSignature), statisticsNode);
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
	public Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark, boolean nullable) {

		String applicationName; // 应用名
		if (StringUtils.isEmpty(applicationName = methodcacheProperties.getName())) {
			applicationName = springApplicationProperties.getName();
		}

		String methodSignature = method.toGenericString(); // 方法签名
		int methodSignatureHashCode = methodSignature.hashCode(); // 方法入参哈希
		int argsHashCode = DataUtil.getArgsHashCode(args); // 入参哈希
		String argsInfo = Arrays.toString(args); // 入参信息
		int cacheHashCode = DataUtil.hash(String.valueOf(methodSignatureHashCode) + String.valueOf(argsHashCode)); // 缓存哈希
		if (StringUtils.isEmpty(id)) {
			id = String.valueOf(methodSignature.hashCode());
		}
		String cacheKey = getCacheKey(applicationName, methodSignature, cacheHashCode, id); // 缓存key
		String redisDataLockKey = getIntactDataLockKey(cacheKey);
		long startTime = new Date().getTime();
		CacheDataModel cacheDataModel = getDataFromRedis(cacheKey, false);
		boolean hit = (cacheDataModel != null);

		log(String.format(	"\n ************* CacheData *************" +
							"\n ** ------- 从Redis获取缓存 -------- **" +
							"\n ** 方法签名：%s" +
							"\n ** 方法入参：%s" +
							"\n ** 缓存命中：%s" +
							"\n ** 过期时间：%s" +
							"\n *************************************",
				methodSignature,
				argsInfo,
				hit ? "是" : "否",
				hit ? formatDate(cacheDataModel.getExpireTime()) : "无"));

		if (!hit || cacheDataModel.isExpired()) {
			try {
				// 缓存未命中或数据已过期，加锁再次尝试获取
				redisUtil.lock(redisDataLockKey, true);
				cacheDataModel = getDataFromRedis(cacheKey, false);
				hit = (cacheDataModel != null);
				log(String.format(	"\n ************* CacheData *************" +
									"\n ** ------ 从Redis获取缓存(加锁) ---- **" +
									"\n ** 方法签名：%s" +
									"\n ** 方法入参：%s" +
									"\n ** 缓存命中：%s" +
									"\n ** 过期时间：%s" +
									"\n *************************************",
						methodSignature,
						argsInfo,
						hit ? "是" : "否",
						hit ? formatDate(cacheDataModel.getExpireTime()) : "无"));

				if (!hit || cacheDataModel.isExpired()) {
					// 发起实际请求
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

					if (data != null || nullable) {
						long expirationTime = actualDataFunctional.getExpirationTime();
						log(String.format(	"\n ************* CacheData *************" +
											"\n ** -------- 保存缓存至Redis ------- **" +
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
					if (methodcacheProperties.isEnableStatistics()) {
						record(cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, id, remark, hit, startTime);
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
				redisUtil.unlock(redisDataLockKey);
			}
		}

		if (refreshData) {
			// 刷新数据
			refreshData(redisDataLockKey, actualDataFunctional, nullable, cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, id, remark);
		}
		if (methodcacheProperties.isEnableStatistics()) {
			record(cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, id, remark, hit, startTime);
		}
		return cacheDataModel.getData();
	}


	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Map<String, Object>> getCaches(String match) {

		Map<String, Map<String, Object>> cacheMap = new HashMap<>();

		Set<String> cacheKeys = new HashSet<>();
		if (StringUtils.isEmpty(match)) {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(springApplicationProperties.getName(), null, null, null)));
		} else {
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(springApplicationProperties.getName(), match, null, null)));
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(springApplicationProperties.getName(), null, match, null)));
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(springApplicationProperties.getName(), null, null, match)));
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
			cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(springApplicationProperties.getName(), null, null, null)));
		} else {
			if (!StringUtils.isEmpty(id)) {
				cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(springApplicationProperties.getName(), null, null, id)));
			}

			if (!StringUtils.isEmpty(cacheHashCode)) {
				cacheKeys.addAll(redisUtil.keys(buildCacheKeyPattern(springApplicationProperties.getName(), null, cacheHashCode, null)));
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
			String cacheKey = methodSignature + KEY_SEPARATION_CHARACTER + DataUtil.hash(String.valueOf(methodSignatureHashCode) + String.valueOf(argsHashCode)) + KEY_SEPARATION_CHARACTER + cacheDataId;
			String redisDataLockKey = getIntactDataLockKey(cacheKey);
			try {
				redisUtil.lock(redisDataLockKey);
				if (!dataModel.isExpired()) {
					filterDataModel(delCacheMap, dataModel, "");
					dataModel.expired();
					deleteDataFromRedis(cacheKey);
				}
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

	/****************************************************************** 私有方法 start ******************************************************************/

	/**
	 * 刷新数据
	 *
	 * @param redisLockKey            锁
	 * @param actualDataFunctional    真实数据请求
	 * @param nullable                返回值允许为空
	 * @param cacheKey                缓存key
	 * @param methodSignature         方法签名
	 * @param methodSignatureHashCode 方法签名哈希值
	 * @param argsInfo                方法参数信息
	 * @param argsHashCode            方法参数哈希
	 * @param id                      缓存ID
	 * @param remark                  缓存备注
	 */
	private void refreshData(String redisLockKey, ActualDataFunctional actualDataFunctional, boolean nullable,
							 String cacheKey, String methodSignature, int methodSignatureHashCode, String argsInfo,
							 int argsHashCode, int cacheHashCode, final String id, String remark) {
		executorService.execute(() -> {
			try {
				if (redisUtil.lock(redisLockKey)) {
					Object data = actualDataFunctional.getActualData();
					if (data != null || nullable) {
						long expirationTime = actualDataFunctional.getExpirationTime();
						log(String.format(	"\n ************* CacheData *************" +
											"\n ** -------- 刷新缓存至Redis ------- **" +
											"\n 方法签名：%s" +
											"\n 方法入参：%s" +
											"\n 缓存数据：%s" +
											"\n 过期时间：%s" +
											"\n *************************************",
								methodSignature,
								argsInfo,
								data,
								formatDate(expirationTime)));
						setDataToRedis(cacheKey, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, data, expirationTime, id, remark);
					}
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

	/**
	 * 构建缓存key规则
	 *
	 * @param methodSignature 方法签名
	 * @param cacheHashCode   入参哈希
	 * @param id              id
	 */
	private String buildCacheKeyPattern(String applicationName, String methodSignature, String cacheHashCode, String id) {
		String cacheKeyPattern = "%{applicationName}%" + KEY_SEPARATION_CHARACTER + "%{methodSignature}%" + KEY_SEPARATION_CHARACTER + "%{cacheHashCode}%" + KEY_SEPARATION_CHARACTER + "%{id}%";

		if (!StringUtils.isEmpty(applicationName)) {
			cacheKeyPattern = cacheKeyPattern.replace("%{applicationName}%", "*" + applicationName + "*");
		} else {
			cacheKeyPattern = cacheKeyPattern.replace("%{applicationName}%", "*");
		}

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
			cacheKeyPattern = cacheKeyPattern.replace("%{id}%", "*" + id + "*");
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
	 * @result 缓存数据
	 */
	private CacheDataModel getDataFromRedis(String cacheKey, boolean intactKeyFlag) {
		String key;
		if (intactKeyFlag) {
			key = cacheKey;
		} else {
			key = getIntactCacheDataKey(cacheKey);
		}

		Object objectByteString = redisUtil.get(key);
		if (objectByteString instanceof String) {
			Object dataModel = SerializeUtil.deserialize(string2ByteArray((String) objectByteString));
			if (dataModel instanceof CacheDataModel) {
				return (CacheDataModel) dataModel;
			}
		}
		return null;
	}

	/**
	 * 缓存数据至Redis
	 *
	 * @param cacheKey                缓存key
	 * @param methodSignature         方法签名
	 * @param methodSignatureHashCode 方法签名哈希
	 * @param args                    入参
	 * @param argsHashCode            入参哈希
	 * @param cacheHashCode           缓存哈希
	 * @param data                    数据
	 * @param expireTimeStamp         过期时间
	 * @param id                      缓存ID
	 * @param remark                  缓存备注
	 */
	private void setDataToRedis(String cacheKey, String methodSignature, int methodSignatureHashCode, String args, int argsHashCode,
								int cacheHashCode, Object data, long expireTimeStamp, String id, String remark) {

		CacheDataModel cacheDataModel = new CacheDataModel(methodSignature, methodSignatureHashCode, args, argsHashCode, cacheHashCode, data, expireTimeStamp);

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
				Object model = SerializeUtil.deserialize(string2ByteArray((String) object));
				if (model instanceof CacheStatisticsModel) {
					CacheStatisticsModel statisticsModel = (CacheStatisticsModel) model;
					resultMap.put(statisticsModel.getMethodSignature(), statisticsModel);
				}
			}
		}

		return resultMap;
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
					dataModelSet.add(getDataFromRedis(cacheKey, true));
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
	 * 获取缓存情况key
	 */
	private static String getIntactStatisticsKey(String key) {
		return METHOD_CACHE_STATISTICS + KEY_SEPARATION_CHARACTER + key;
	}

	/**
	 * 保存数据至Redis
	 * 这里会对返回值进行反序列化
	 */
	private void setDataToRedis(String cacheKey, CacheDataModel cacheDataModel, long timeout) {
		redisUtil.set(getIntactCacheDataKey(cacheKey), byteArray2String(SerializeUtil.serizlize(cacheDataModel)), timeout);
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
	 * <p>
	 * Redis缓存信息模型(hash)
	 * "METHOD_CACHE_STATISTICS":{
	 * 方法签名:(序列化后的)统计信息
	 * }
	 */
	private void setStatisticsToRedis(String methodSignature, CacheStatisticsModel cacheStatisticsModel) {
		redisUtil.hset(METHOD_CACHE_STATISTICS, methodSignature, byteArray2String(SerializeUtil.serizlize(cacheStatisticsModel)));
	}

	/**
	 * 字节数组转字符串
	 */
	private String byteArray2String(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	/**
	 * 字符串转字节数组
	 */
	private byte[] string2ByteArray(String str) {
		return Base64.getDecoder().decode(str);
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
