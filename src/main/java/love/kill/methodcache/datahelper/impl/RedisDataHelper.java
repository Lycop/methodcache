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
	private static final  String CACHE_KEY_SEPARATION_CHARACTER = "_";

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


	public RedisDataHelper() {
	}

	public RedisDataHelper(MethodcacheProperties methodcacheProperties, RedisUtil redisUtil) {
		this.methodcacheProperties = methodcacheProperties;
		this.redisUtil = redisUtil;

		this.enableLog = methodcacheProperties.isEnableLog();
	}

	@Override
	public Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark){

		String methodSignature = method.toGenericString(); // 方法签名
		Integer argsHashCode = DataUtil.getArgsHashCode(args); // 入参哈希
		String argsInfo = Arrays.toString(args);

		String redisLockKey = REDIS_LOCK_PREFIX + METHOD_CACHE_DATA + methodSignature;

		CacheDataModel cacheDataModel;

			cacheDataModel = getDataFromRedis(methodSignature, argsHashCode);
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
				cacheDataModel = getDataFromRedis(methodSignature, argsHashCode);
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

						setDataToRedis(methodSignature, argsHashCode, argsInfo, data, expirationTime, id, remark);
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

			if(refreshData){
				// 刷新数据
				executorService.execute(()->{
					try {
						while (!redisUtil.lock(redisLockKey)) {}
						Object data = actualDataFunctional.getActualData();
						if (data != null) {
							long expirationTime = actualDataFunctional.getExpirationTime();
							log(String.format(  "\n ************* CacheData *************" +
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
							setDataToRedis(methodSignature, argsHashCode, argsInfo, data, expirationTime, id, remark);
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

		Set fieldSet = redisUtil.hkeys(METHOD_CACHE_DATA);
		List<String> fieldList = new ArrayList<>();
		for (Object field : fieldSet) {
			if (field instanceof String) {
				fieldList.add((String) field);
			}
		}
		if (fieldList.size() <= 0) {
			return cacheMap;
		}

		List<CacheDataModel> objects = getCacheDataModel(fieldList);

		for (CacheDataModel cacheDataModel : objects) {

			if(cacheDataModel.isExpired()){
				// todo 从redis删除
				continue;
			}

			String methodSignature = cacheDataModel.getMethodSignature();
			if (StringUtils.isEmpty(match)) {
				putCacheInfo(cacheMap, methodSignature, cacheDataModel, select);
			} else {
				String id = cacheDataModel.getId();
				String remark = cacheDataModel.getRemark();
				if ((!StringUtils.isEmpty(id) && id.contains(match)) || (!StringUtils.isEmpty(remark) && remark.contains(match)) || methodSignature.contains(match)) {
					putCacheInfo(cacheMap, methodSignature, cacheDataModel, select);
				}
			}
		}

		return cacheMap;
	}

	@SuppressWarnings("unchecked")
	private void putCacheInfo(Map<String, Map<String,Object>> cacheMap, String methodSignature, CacheDataModel cacheDataModel, String select){

		if(!StringUtils.isEmpty(select)){
			String args = cacheDataModel.getArgs();
			if(!StringUtils.isEmpty(args) && !args.contains(select)){
				return;
			}
		}

		Map<String,Object> keyMap = cacheMap.computeIfAbsent(methodSignature, k -> {
			Map<String,Object> map = new HashMap<>();
			map.put("id",cacheDataModel.getId());
			map.put("remark",cacheDataModel.getRemark());
			return map;
		});

		List<Map<String, Object>> cacheInfoList = (List<Map<String, Object>>) keyMap.computeIfAbsent("cache", k -> new ArrayList<>());

		Map<String,Object> cacheInfo = new HashMap<>();
		cacheInfo.put("hashCode", cacheDataModel.getCacheHashCode());
		cacheInfo.put("args",cacheDataModel.getArgs());
		cacheInfo.put("data",cacheDataModel.getData());
		cacheInfo.put("cacheTime",cacheDataModel.getFormatCacheTime());
		cacheInfo.put("expireTime",cacheDataModel.getFormatExpireTime());

		cacheInfoList.add(cacheInfo);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void wipeCache(int cacheHashCode) {


		Set fieldSet = redisUtil.hkeys(METHOD_CACHE_DATA);
		List<String> fieldList = new ArrayList<>();
		for (Object field : fieldSet) {
			if (field instanceof String) {
				fieldList.add((String) field);
			}
		}
		if (fieldList.size() <= 0) {
			return;
		}

		List<CacheDataModel> objects = getCacheDataModel(fieldList);

		for (CacheDataModel cacheDataModel : objects) {
			if (cacheDataModel == null || cacheDataModel.getCacheHashCode() != cacheHashCode) {
				continue;
			}

			String methodSignature = cacheDataModel.getMethodSignature();
			String redisLockKey = REDIS_LOCK_PREFIX + METHOD_CACHE_DATA + methodSignature;
			try {
				while (!redisUtil.lock(redisLockKey)) {}

				if (cacheDataModel.isExpired()) {
					// 缓存不存在或已过期
					continue;
				}

				if (cacheDataModel.getCacheHashCode() == cacheHashCode) {
					cacheDataModel.expired();
					setDataToRedis(cacheDataModel);
					break;
				}

			} catch (Throwable throwable) {
				throwable.printStackTrace();
			} finally {
				redisUtil.unlock(redisLockKey);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private List<CacheDataModel> getCacheDataModel(List<String> fieldList){

		int batchSize = 500; // 每批大小
		int batchCount = (fieldList.size() / batchSize) + 1; // 总批次数

		CountDownLatch countDownLatch = new CountDownLatch(batchCount);

		List<String>[] batchFieldListArray = new List[batchCount];
		int i = 0;
		for (String field : fieldList) {
			if (batchFieldListArray[i] == null) {
				batchFieldListArray[i] = new ArrayList<>();
			}
			batchFieldListArray[i].add(field);
			if (batchFieldListArray[i].size() > batchSize) {
				if (++i >= batchCount) {
					break;
				}
			}
		}

		List<CacheDataModel> cacheDataModels = new ArrayList<>();

		for (List<String> batchFieldList : batchFieldListArray) {
			executorService.execute(() -> {
				try {
					cacheDataModels.addAll(getDataFromRedis(batchFieldList));
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

		return cacheDataModels;
	}


	/**
	 * 从 Redis 获取数据
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * */
	private CacheDataModel getDataFromRedis(String methodSignature, Integer argsHashCode) {
		return getDataFromRedis(methodSignature + CACHE_KEY_SEPARATION_CHARACTER + String.valueOf(argsHashCode));
	}


	/**
	 * 从 Redis 获取数据
	 *
	 * @param key 缓存key
	 * @result key对应的数据
	 * */
	private CacheDataModel getDataFromRedis(String key) {

		Object objectByteString = redisUtil.hget(METHOD_CACHE_DATA, key);
		if(objectByteString != null){
			Object dataModel = SerializeUtil.deserialize(string2byteArray((String) objectByteString));
			if(dataModel instanceof CacheDataModel){
				return (CacheDataModel)dataModel;
			}
		}
		return null;
	}

	/**
	 * 从 Redis 批量获取数据
	 *
	 * @param keys 缓存key
	 * @result key对应的数据
	 * */
	private List<CacheDataModel> getDataFromRedis(List<String> keys) {
		List<Object> objectByteStringList = redisUtil.hMultiget(METHOD_CACHE_DATA, keys);
		List<CacheDataModel> cacheDataModels = new ArrayList<>();
		for(Object objectByteString : objectByteStringList){
			if(objectByteString instanceof String){
				Object dataModel = SerializeUtil.deserialize(string2byteArray((String) objectByteString));
				if(dataModel instanceof CacheDataModel){
					cacheDataModels.add((CacheDataModel)dataModel);
				}
			}
		}
		return cacheDataModels;
	}


	/**
	 * 缓存数据至Redis
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * @param args 入参信息
	 * @param data 数据
	 * @param expireTimeStamp 过期时间
	 * */
	private void setDataToRedis(String methodSignature,int argsHashCode, String args, Object data, long expireTimeStamp, String id, String remark) {
		CacheDataModel cacheDataModel = new CacheDataModel(methodSignature, args, argsHashCode, data, expireTimeStamp);
		if(StringUtils.isEmpty(id)){
			cacheDataModel.setId(String.valueOf(cacheDataModel.getMethodSignatureHashCode()));
		}else {
			cacheDataModel.setId(id);
		}

		if(!StringUtils.isEmpty(remark)){
			cacheDataModel.setRemark(remark);
		}

		setDataToRedis(cacheDataModel);
	}

	/**
	 * 缓存数据至 Redis
	 * 这里会对返回值进行反序列化
	 * */
	private void setDataToRedis(CacheDataModel cacheDataModel) {
		redisUtil.hset(METHOD_CACHE_DATA, cacheDataModel.getMethodSignature() + CACHE_KEY_SEPARATION_CHARACTER + String.valueOf(cacheDataModel.getArgsHashCode()), byteArray2String(SerializeUtil.serizlize(cacheDataModel)));
	}


	private void log(String info){
		if(enableLog){
			logger.info(info);
		}
	}

	private String byteArray2String(byte[] bytes){
		return Base64.getEncoder().encodeToString(bytes);
	}

	private byte[] string2byteArray(String str){
		return Base64.getDecoder().decode(str);
	}
}
