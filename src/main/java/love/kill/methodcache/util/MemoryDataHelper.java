package love.kill.methodcache.util;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.aspect.CacheMethodAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 内存缓存
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public class MemoryDataHelper implements DataHelper {

	private static Logger logger = LoggerFactory.getLogger(CacheMethodAspect.class);


	/**
	 * 属性配置
	 * */
	private final MethodcacheProperties methodcacheProperties;

	/**
	 * 开启日志
	 * */
	private static boolean enableLog = false;

	/**
	 * 缓存数据
	 * 格式：<方法签名,<方法入参哈希,数据>>
	 * */
	private final static Map<String, Map<String, CacheDataModel>> cacheData = new ConcurrentHashMap<>();

	/**
	 * 缓存数据过期信息
	 * 格式：<过期时间（时间戳，毫秒）,<方法签名,方法入参哈希>>
	 * */
	private final static Map<Long, Map<String,Set<String>>> dataExpireInfo = new ConcurrentHashMap<>();


	/**
	 * 锁
	 * */
	private static ReentrantLock cacheDataLock = new ReentrantLock();

	private static final ExecutorService executorService = Executors.newFixedThreadPool(10);


//	static {
//
//		/**
//		 * 开启一个线程，查询并剔除已过期的数据
//		 * */
//		Executors.newSingleThreadExecutor().execute(()->{
//			while (true){
//				List<Long> expireInfoKeyList;
//				Set<Long> expireInfoKeySet;
//				try {
//					cacheDataReadLock.lock();
//					expireInfoKeySet = dataExpireInfo.keySet();
//					if(expireInfoKeySet.size() <= 0){
//						// 没有过期信息数据
//						continue;
//					}
//					expireInfoKeyList = new ArrayList<>(expireInfoKeySet);
//				}finally {
//					cacheDataReadLock.unlock();
//				}
//
//
//				long nowTimeStamp = new Date().getTime();
//				expireInfoKeyList.sort((l1, l2) -> (int) (l1 - l2));
//
//				for (long expireInfoKey : expireInfoKeyList) {
//					if (expireInfoKey > nowTimeStamp) {
//						// 最接近的时间还没到，本次循环跳过
//						try {
//							Thread.sleep(500);
//						} catch (InterruptedException e) {
//							e.printStackTrace();
//						}
//						break;
//					}
//
//					// 移除过期缓存数据
//					Set<String> invalidCachekeySet = dataExpireInfo.get(expireInfoKey);
//					for (String invalidCachekey : invalidCachekeySet) {
//						doRemoveData(invalidCachekey,false);
//					}
//					dataExpireInfo.remove(expireInfoKey);
//				}
//
//			}
//		});
//	}

	public MemoryDataHelper(MethodcacheProperties methodcacheProperties) {
		this.methodcacheProperties = methodcacheProperties;
		enableLog = methodcacheProperties.isEnableLog();
	}

	@Override
	public Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional) {

		String methodSignature = method.toGenericString(); // 方法签名
		Integer argsHashCode = DataUtil.getArgsHashCode(args); // 入参哈希
		String argsInfo = Arrays.toString(args);

		CacheDataModel cacheDataModel;
		try {
			cacheDataModel = getDataFromMemory(methodSignature,argsHashCode);
			log(String.format(  "\n >>>> 获取缓存(无锁) <<<<" +
								"\n method：%s" +
								"\n args：%s" +
								"\n 缓存命中：%s" +
								"\n -----------------------",methodSignature,argsInfo,(cacheDataModel != null)));

			if(cacheDataModel == null || cacheDataModel.isExpired()){
				// 没有获取到数据或者数据已过期，加锁再次尝试获取
				cacheDataLock.lock();
				cacheDataModel = getDataFromMemory(methodSignature,argsHashCode);
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

						setDataToMemory(methodSignature, argsHashCode, argsInfo, data, expirationTime);

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

							setDataToMemory(methodSignature, argsHashCode, argsInfo, data, expirationTime);
						}
					} catch (Throwable throwable) {
						throwable.printStackTrace();
					}
				});
			}
			return cacheDataModel.getData();

		} catch (Exception e) {
			e.printStackTrace();
			logger.info("\n >>>> getData发生运行异常 <<<<" +
						"\n 异常信息：" + e.getMessage() +
						"\n ---------------------------");
			return null;

		} finally {
			cacheDataLock.unlock();
		}
	}

//	private boolean setData(String key, Object value, long time) {
//		try {
//			cacheDataWriteLock.lock();
//			if(enableLog){
//				logger.info("\n >>>> 更新缓存至内存 <<<<" +
//							"\n key：" + key +
//							"\n 过期时间：" + time +
//							"\n 数据：" + value +
//							"\n -----------------------");
//			}
//
//			doRemoveData(key,true);
//
//			if(time <= 0L){
//				// 永久有效
////				cacheData.put(key, new CacheDataModel(value));
//			}else {
//
//				// 记录缓存数据过期信息
//				long expireTimeStamp = new Date().getTime() + time;
//				Set<String> keySet = dataExpireInfo.computeIfAbsent(expireTimeStamp, k -> new HashSet<>());
//				keySet.add(key);
//
//				// 缓存数据
////				cacheData.put(key, new CacheDataModel(value, expireTimeStamp));
//			}
//
//		} finally {
//			cacheDataWriteLock.unlock();
//		}
//		return true;
//	}


	/**
	 * 从内存获取数据
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * */
	private CacheDataModel getDataFromMemory(String methodSignature, Integer argsHashCode){

		Map<String, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature);
		if(cacheDataModelMap != null){
			return cacheDataModelMap.get(Integer.toString(argsHashCode));
		}
		return null;
	}


	/**
	 * 缓存数据至内存
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * @param args 入参信息
	 * @param data 数据
	 * @param expireTimeStamp 过期时间
	 *
	 * 这里会对返回值进行反序列化
	 * */
	private boolean setDataToMemory(String methodSignature,Integer argsHashCode, String args, Object data, long expireTimeStamp) {
		CacheDataModel cacheDataModel = new CacheDataModel(methodSignature, argsHashCode, args, data, expireTimeStamp);

		Map<String, CacheDataModel> cacheDataModelMap = cacheData.computeIfAbsent(methodSignature, k -> new HashMap<>());
		cacheDataModelMap.put(Integer.toString(argsHashCode),cacheDataModel);
		return true;
	}

	private static void doRemoveData(String key,boolean deleteFromExpireInfo){

		try {


			if (enableLog) {
				logger.info("\n >>>> 移除缓存数据 <<<<" +
							"\n key：" + key +
							"\n ------------------------");
			}

			if(deleteFromExpireInfo){
//				// 从"dataExpireInfo"中删除指定key
//				CacheDataModel_1 dataModel = cacheData.get(key);
//				if(dataModel != null){
//					long expireTimeStamp = dataModel.getExpireTimeStamp();
//					if(expireTimeStamp != 0){
//						Set<String> expireInfoSet = dataExpireInfo.get(expireTimeStamp);
//						if(expireInfoSet != null){
//							expireInfoSet.remove(key);
//						}
//					}
//				}
			}

			// 从"cacheData"中删除指定key
			cacheData.remove(key);

		}catch (Exception e){
			e.printStackTrace();
			logger.error("移除数据出现异常：" + e.getMessage());
		}finally {

		}
	}

	private void log(String info){
		if(enableLog){
			logger.info(info);
		}
	}
}
