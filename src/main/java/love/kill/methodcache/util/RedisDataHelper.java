package love.kill.methodcache.util;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.aspect.CacheMethodAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public class RedisDataHelper implements DataHelper {

	private static Logger logger = LoggerFactory.getLogger(CacheMethodAspect.class);

	private static final String REDIS_LOCK_PREFIX = "REDIS_LOCK_"; // redis锁前缀
	private static final  String REDIS_LOCK_CACHE_DATA = "CACHE_DATA"; // 缓存数据锁

	private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

	RedisUtil redisUtil;
	MethodcacheProperties methodcacheProperties;

	private static boolean enableLog = false;

	public void setRedisUtil(RedisUtil redisUtil) {
		this.redisUtil = redisUtil;
	}

	public RedisDataHelper(MethodcacheProperties methodcacheProperties) {
		this.methodcacheProperties = methodcacheProperties;
		enableLog = methodcacheProperties.isEnableLog();
	}

	@Override
	public Object getData(String key ,boolean refreshData , ActualDataFunctional actualDataFunctional) {

		String redisLockKey = REDIS_LOCK_PREFIX + REDIS_LOCK_CACHE_DATA + key;

		Object resultObject;
		try {
			resultObject = getDataFromRedis(key);
			if(enableLog){
				logger.info("\n >>>> 获取缓存(无锁) <<<<" +
							"\n key：" + key +
							"\n 缓存命中：" + (resultObject != null) +
							"\n -----------------------");
			}

			if(resultObject == null){
				// 没有获取到数据，加锁再次尝试获取
				while (!redisUtil.lock(redisLockKey)) {}
				resultObject = getDataFromRedis(key);
				if(enableLog){
					logger.info("\n >>>> 获取缓存(加锁) <<<<" +
								"\n key：" + key +
								"\n 缓存命中：" + (resultObject != null) +
								"\n -----------------------");
				}

				if(resultObject == null){
					// 没获取到数据，发起实际请求
					resultObject = actualDataFunctional.getActualData();
					if(enableLog){
						logger.info("\n >>>> 发起请求 <<<<" +
									"\n key：" + key +
									"\n 数据：" + resultObject +
									"\n -----------------");
					}

					if (resultObject != null) {
						long expirationTime = actualDataFunctional.getExpirationTime();
						if(enableLog){
							logger.info("\n >>>> 更新数据 <<<<" +
										"\n key：" + key +
										"\n 过期时间：" + expirationTime +
										"\n 数据：" + resultObject +
										"\n ------------------");
						}
						redisUtil.set(key, byteArray2String(SerializeUtil.serizlize(resultObject)), expirationTime / 1000);
					}
				}

				return resultObject;

			}else {

				if(refreshData){
					// 刷新数据
					executorService.execute(()->{
						try {
							Object obj = actualDataFunctional.getActualData();
							if (obj != null) {
								setData2Redis(key, obj, actualDataFunctional.getExpirationTime());
							}
						} catch (Throwable throwable) {
							throwable.printStackTrace();
						}
					});
				}
				return resultObject;
			}

		}  catch (Exception e) {
			e.printStackTrace();
			logger.info("\n >>>> getData发生运行异常 <<<<" +
						"\n 异常信息：" + e.getMessage() +
						"\n ---------------------------");
			return null;

		} finally {
			redisUtil.unlock(redisLockKey);
		}
	}


	/**
	 * 数据保存至Redis中
	 * 这里会对数据进行序列化
	 * */
	private boolean setData2Redis(String key, Object value, long time) {
		try {
			String redisLockKey = REDIS_LOCK_PREFIX + REDIS_LOCK_CACHE_DATA + key;
			redisUtil.set(redisLockKey, byteArray2String(SerializeUtil.serizlize(value)),time / 1000);
			return true;

		}catch (Throwable throwable) {
			throwable.printStackTrace();
			logger.info("\n >>>> setData发生异常 <<<<" +
						"\n 异常信息：" + throwable.getMessage() +
						"\n ------------------------");
			return false;
		}

	}

	/**
	 * 从Redis获取数据
	 * 这里会对返回值进行反序列化
	 * */
	private Object getDataFromRedis(String key) {
		try {
			Object object = redisUtil.get(key);
			if(object instanceof String){
				return SerializeUtil.deserialize(string2byteArray((String) object));
			}
			return null;

		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			logger.warn("从 redis 获取数据发生异常：" + e.getMessage());
			return null;
		}
	}



	private String byteArray2String(byte[] bytes){
		return Base64.getEncoder().encodeToString(bytes);
	}

	private byte[] string2byteArray(String str){
		return Base64.getDecoder().decode(str);
	}
}
