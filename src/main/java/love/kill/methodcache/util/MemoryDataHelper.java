package love.kill.methodcache.util;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.aspect.CacheMethodAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
	 * */
	private final static Map<String,CacheDataModel> cacheData = new ConcurrentHashMap<>();

	/**
	 * 缓存数据过期信息
	 * key，过期时间（时间戳，毫秒）
	 * value，过期的key
	 * */
	private final static Map<Long, Set<String>> dataExpireInfo = new ConcurrentHashMap<>();


	/**
	 * 读写锁
	 * */
	private static ReentrantReadWriteLock cacheDataLock = new ReentrantReadWriteLock();
	private static ReentrantReadWriteLock.ReadLock cacheDataReadLock = cacheDataLock.readLock();
	private static ReentrantReadWriteLock.WriteLock cacheDataWriteLock = cacheDataLock.writeLock();

	private static final ExecutorService executorService = Executors.newFixedThreadPool(10);


	static {

		/**
		 * 开启一个线程，查询并剔除已过期的数据
		 * */
		Executors.newSingleThreadExecutor().execute(()->{
			while (true){
				List<Long> expireInfoKeyList;
				Set<Long> expireInfoKeySet;
				try {
					cacheDataReadLock.lock();
					expireInfoKeySet = dataExpireInfo.keySet();
					if(expireInfoKeySet.size() <= 0){
						// 没有过期信息数据
						continue;
					}
					expireInfoKeyList = new ArrayList<>(expireInfoKeySet);
				}finally {
					cacheDataReadLock.unlock();
				}


				long nowTimeStamp = new Date().getTime();
				expireInfoKeyList.sort((l1, l2) -> (int) (l1 - l2));

				for (long expireInfoKey : expireInfoKeyList) {
					if (expireInfoKey > nowTimeStamp) {
						// 最接近的时间还没到，本次循环跳过
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						break;
					}

					// 移除过期缓存数据
					Set<String> invalidCachekeySet = dataExpireInfo.get(expireInfoKey);
					for (String invalidCachekey : invalidCachekeySet) {
						doRemoveData(invalidCachekey,false);
					}
					dataExpireInfo.remove(expireInfoKey);
				}

			}
		});
	}

	public MemoryDataHelper(MethodcacheProperties methodcacheProperties) {
		this.methodcacheProperties = methodcacheProperties;
		enableLog = methodcacheProperties.isEnableLog();
	}

	@Override
	public Object getData(String key,boolean refreshData,ActualDataFunctional actualDataFunctional) {

		Object resultObject = doGetData(key);
		if (enableLog) {
			logger.info("\n >>>> 从内存获取缓存 <<<<" +
						"\n key：" + key +
						"\n 缓存命中：" + (resultObject != null) +
						"\n --------------" +
					"------------");
		}

		if(resultObject == null){
			// 发起实际请求
			resultObject = actualDataFunctional.getActualData();
			if (enableLog) {
				logger.info("\n >>>> 发起请求 <<<<" +
							"\n key：" + key +
							"\n 数据：" + resultObject +
							"\n -----------------");
			}

			if (resultObject != null) {
				setData(key, resultObject, actualDataFunctional.getExpirationTime());
			}

			return resultObject;


		}else {
			if(refreshData){
				executorService.execute(()->{
					Object obj = null;
					try {
						obj = actualDataFunctional.getActualData();
					} catch (Throwable throwable) {
						throwable.printStackTrace();
					}

					if (obj != null) {
						setData(key, obj, actualDataFunctional.getExpirationTime());
					}
				});
			}
			return resultObject;
		}
	}

	private boolean setData(String key, Object value, long time) {
		try {
			cacheDataWriteLock.lock();
			if(enableLog){
				logger.info("\n >>>> 更新缓存至内存 <<<<" +
							"\n key：" + key +
							"\n 过期时间：" + time +
							"\n 数据：" + value +
							"\n -----------------------");
			}

			doRemoveData(key,true);

			if(time <= 0L){
				// 永久有效
				cacheData.put(key, new CacheDataModel(value));
			}else {

				// 记录缓存数据过期信息
				long expireTimeStamp = new Date().getTime() + time;
				Set<String> keySet = dataExpireInfo.computeIfAbsent(expireTimeStamp, k -> new HashSet<>());
				keySet.add(key);

				// 缓存数据
				cacheData.put(key, new CacheDataModel(value, expireTimeStamp));
			}

		} finally {
			cacheDataWriteLock.unlock();
		}
		return true;
	}

	/**
	 * 获取数据
	 * 返回前先判断数据是否过期，过期则返回null，并移除该数据
	 * */
	private static Object doGetData(String key){
		try {
			cacheDataReadLock.lock();
			CacheDataModel cacheDataModel = cacheData.get(key);
			if (cacheDataModel != null && !cacheDataModel.isExpired()) {
				return cacheDataModel.getData();
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("获取数据出现异常：：" + e.getMessage());
		}finally {
			cacheDataReadLock.unlock();
		}

		return null;
	}

	private static void doRemoveData(String key,boolean deleteFromExpireInfo){

		try {
			cacheDataWriteLock.lock();

			if (enableLog) {
				logger.info("\n >>>> 移除缓存数据 <<<<" +
							"\n key：" + key +
							"\n ------------------------");
			}

			if(deleteFromExpireInfo){
				// 从"dataExpireInfo"中删除指定key
				CacheDataModel dataModel = cacheData.get(key);
				if(dataModel != null){
					long expireTimeStamp = dataModel.getExpireTimeStamp();
					if(expireTimeStamp != 0){
						Set<String> expireInfoSet = dataExpireInfo.get(expireTimeStamp);
						if(expireInfoSet != null){
							expireInfoSet.remove(key);
						}
					}
				}
			}

			// 从"cacheData"中删除指定key
			cacheData.remove(key);

		}catch (Exception e){
			e.printStackTrace();
			logger.error("移除数据出现异常：" + e.getMessage());
		}finally {
			cacheDataWriteLock.unlock();
		}
	}
}

class CacheDataModel{

	/**
	 * 数据
	 * */
	private Object data;

	/**
	 * 缓存时间
	 * */
	private Date cacheDate = new Date();

	/**
	 * 过期时间（时间戳）
	 * 0 代表 永久有效
	 * */
	private long expireTimeStamp = 0L;

	CacheDataModel(Object data, long expireTimeStamp) {
		this.data = data;
		this.expireTimeStamp = expireTimeStamp;
	}

	CacheDataModel(Object data) {
		this.data = data;
	}

	Object getData() {
		return data;
	}

	public Date getCacheDate() {
		return cacheDate;
	}

	public long getExpireTimeStamp() {
		return expireTimeStamp;
	}

	boolean isExpired(){
		return expireTimeStamp !=0L && new Date().getTime() > expireTimeStamp;
	}

}
