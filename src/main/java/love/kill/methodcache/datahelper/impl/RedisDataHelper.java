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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public class RedisDataHelper implements DataHelper {

	private static Logger logger = LoggerFactory.getLogger(RedisDataHelper.class);

	private static final String REDIS_LOCK_PREFIX = "REDIS_LOCK_"; // redis锁前缀
	private static final  String METHOD_CACHE_DATA = "METHOD_CACHE_DATA"; // 缓存数据
	private static final  String CACHE_KEY_SEPARATION_CHARACTER = "_"; // 缓存Key分隔符

	private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

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

	@Override
	public Map<String, Map<String,Object>> getCaches(String key){
		Map<String, Map<String,Object>> cacheMap = new HashMap<>();
		Set hkeys = redisUtil.hkeys(METHOD_CACHE_DATA);
		if(hkeys != null){
			for(Object eachKey : hkeys){
				if(eachKey instanceof String){
					CacheDataModel cacheDataModel = getDataFromRedis((String)eachKey);
					if(cacheDataModel == null || cacheDataModel.isExpired()){
						// 缓存不存在或已过期
						continue;
					}

					String methodSignature = cacheDataModel.getMethodSignature();

					if(StringUtils.isEmpty(key)){
						putCacheInfo(cacheMap, methodSignature, cacheDataModel);
					}else {
						String id = cacheDataModel.getId();
						String remark = cacheDataModel.getRemark();
						if((!StringUtils.isEmpty(id) && id.contains(key)) || (!StringUtils.isEmpty(remark) && remark.contains(key)) || methodSignature.contains(key)){
							putCacheInfo(cacheMap, methodSignature, cacheDataModel);
						}
					}
				}
			}
		}

		return cacheMap;
	}

	@SuppressWarnings("unchecked")
	private void putCacheInfo(Map<String, Map<String,Object>> cacheMap, String methodSignature, CacheDataModel cacheDataModel){

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

	@Override
	public boolean wipeCache(int cacheHashCode) {
		Set hkeys = redisUtil.hkeys(METHOD_CACHE_DATA);
		if(hkeys != null){
			for(Object eachKey : hkeys){
				if(eachKey instanceof String){

					// 没有获取到数据或者数据已过期，加锁再次尝试获取
					CacheDataModel cacheDataModel = getDataFromRedis((String)eachKey);
					if(cacheDataModel == null){
						continue;
					}

					String methodSignature = cacheDataModel.getMethodSignature();
					String redisLockKey = REDIS_LOCK_PREFIX + METHOD_CACHE_DATA + methodSignature;
					try {
						while (!redisUtil.lock(redisLockKey)) {}
						cacheDataModel = getDataFromRedis((String)eachKey);

						if(cacheDataModel == null || cacheDataModel.isExpired()){
							// 缓存不存在或已过期
							continue;
						}

						if(cacheDataModel.getCacheHashCode() == cacheHashCode){
							cacheDataModel.expired();
							setDataToRedis(cacheDataModel);
							break;
						}


					}catch (Throwable throwable) {
						throwable.printStackTrace();
					} finally {
						redisUtil.unlock(redisLockKey);
					}

				}
			}
		}
		return true;
	}


	/**
	 * 从Redis获取数据
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * */
	private CacheDataModel getDataFromRedis(String methodSignature, Integer argsHashCode) {
		return getDataFromRedis(methodSignature + CACHE_KEY_SEPARATION_CHARACTER + String.valueOf(argsHashCode));
	}


	/**
	 * 从Redis获取数据
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
	 * 缓存数据至Redis
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * @param args 入参信息
	 * @param data 数据
	 * @param expireTimeStamp 过期时间
	 *
	 * 这里会对返回值进行反序列化
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
	 * 缓存数据至Redis
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
