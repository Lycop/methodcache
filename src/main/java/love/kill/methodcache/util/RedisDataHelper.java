package love.kill.methodcache.util;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.aspect.CacheMethodAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
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

	private RedisUtil redisUtil;
	private MethodcacheProperties methodcacheProperties;

	private static boolean enableLog = false;

	public void setRedisUtil(RedisUtil redisUtil) {
		this.redisUtil = redisUtil;
	}

	public RedisDataHelper(MethodcacheProperties methodcacheProperties) {
		this.methodcacheProperties = methodcacheProperties;
		enableLog = methodcacheProperties.isEnableLog();
	}

	@Override
	public Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional){

		String methodSignature = method.toGenericString(); // 方法签名
		Integer argsHashCode = DataUtil.getArgsHashCode(args); // 入参哈希
		String argsInfo = Arrays.toString(args);

		String redisLockKey = REDIS_LOCK_PREFIX + REDIS_LOCK_CACHE_DATA + methodSignature;

		CacheDataModel cacheDataModel;
		try {
			cacheDataModel = getDataFromRedis(methodSignature, argsHashCode);
			log(String.format(  "\n >>>> 获取缓存(无锁) <<<<" +
								"\n method：%s" +
								"\n args：%s" +
								"\n 缓存命中：%s" +
								"\n -----------------------",methodSignature,argsInfo,(cacheDataModel != null)));

			if(cacheDataModel == null || cacheDataModel.isExpired()){
				// 没有获取到数据或者数据已过期，加锁再次尝试获取
				while (!redisUtil.lock(redisLockKey)) {}
				cacheDataModel = getDataFromRedis(methodSignature, argsHashCode);
				log(String.format(  "\n >>>> 获取缓存(加锁) <<<<" +
									"\n method：%s" +
									"\n args：%s" +
									"\n 缓存命中：%s" +
									"\n -----------------------",methodSignature,argsInfo,(cacheDataModel != null)));

				if(cacheDataModel == null || cacheDataModel.isExpired()){
					// 没获取到数据或者数据已过期，发起实际请求

					Object data = actualDataFunctional.getActualData();
					log(String.format(  "\n >>>> 发起请求 <<<<" +
										"\n method：%s" +
										"\n args：%s" +
										"\n 数据：%s" +
										"\n -----------------------",methodSignature,argsInfo,data));

					if (data != null) {
						long expirationTime = actualDataFunctional.getExpirationTime();
						log(String.format(  "\n >>>> 设置缓存 <<<<" +
											"\n method：%s" +
											"\n args：%s" +
											"\n 数据：%s" +
											"\n 过期时间：%s" +
											"\n -----------------------",methodSignature,argsInfo,data,expirationTime));

						setDataToRedis(methodSignature, argsHashCode, argsInfo, data, expirationTime);
					}

					return data;
				}
			}

			if(refreshData){
				// 刷新数据
				executorService.execute(()->{
					try {
						Object data = actualDataFunctional.getActualData();
						if (data != null) {
							long expirationTime = actualDataFunctional.getExpirationTime();
							log(String.format(  "\n >>>> 刷新缓存 <<<<" +
												"\n method：%s" +
												"\n args：%s" +
												"\n 数据：%s" +
												"\n 过期时间：%s" +
												"\n -----------------------",method,Arrays.toString(args),data,expirationTime));
							setDataToRedis(methodSignature, argsHashCode, argsInfo, data, expirationTime);
						}
					} catch (Throwable throwable) {
						throwable.printStackTrace();
					}
				});
			}

			return cacheDataModel.getData();

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
	 * 从Redis获取数据
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * */
	private CacheDataModel getDataFromRedis(String methodSignature, Integer argsHashCode) {

		Object objectByteString = redisUtil.hget(methodSignature, Integer.toString(argsHashCode));
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
	private boolean setDataToRedis(String methodSignature,Integer argsHashCode, String args, Object data, long expireTimeStamp) {
		CacheDataModel cacheDataModel = new CacheDataModel(methodSignature, argsHashCode, args, data, expireTimeStamp);
		return redisUtil.hset(methodSignature,Integer.toString(argsHashCode),byteArray2String(SerializeUtil.serizlize(cacheDataModel)));
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
