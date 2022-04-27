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
	private final static Map<String, Map<Integer, CacheDataModel>> cacheData = new ConcurrentHashMap<>();

	/**
	 * 缓存数据过期信息
	 * 格式：<过期时间（时间戳，毫秒）,<方法签名,方法入参哈希>>
	 * */
	private final static Map<Long, Map<String,Set<Integer>>> dataExpireInfo = new ConcurrentHashMap<>();


	/**
	 * 锁
	 * */
	private static ReentrantLock cacheDataLock = new ReentrantLock();

	private static final ExecutorService executorService = Executors.newFixedThreadPool(10);


	static {

		/**
		 * 开启一个线程，查询并剔除已过期的数据
		 * */
		Executors.newSingleThreadExecutor().execute(()->{
			while (true){
				List<Long> expireTimeStampKeySetKeyList;
				Set<Long> expireTimeStampKeySet;
				try {
					cacheDataLock.lock();
					expireTimeStampKeySet = dataExpireInfo.keySet();
					if (expireTimeStampKeySet.size() <= 0) {
						// 没有过期信息数据
						continue;
					}

					expireTimeStampKeySetKeyList = new ArrayList<>(expireTimeStampKeySet);
					expireTimeStampKeySetKeyList.sort((l1, l2) -> (int) (l1 - l2));
					long nowTimeStamp = new Date().getTime();
					for (long expireTimeStamp : expireTimeStampKeySetKeyList) {
						if (expireTimeStamp > nowTimeStamp) {
							// 最接近的时间还没到，本次循环跳过
							break;
						}

						// 移除过期缓存数据 <过期时间（时间戳，毫秒）,<方法签名,方法入参哈希>>
						Map<String,Set<Integer>> methodArgsHashCodeMap = dataExpireInfo.get(expireTimeStamp);
						if(methodArgsHashCodeMap == null || methodArgsHashCodeMap.isEmpty()){
							continue;
						}
						Set<String> methodSignatureSet =  methodArgsHashCodeMap.keySet();
						for (String methodSignature : methodSignatureSet) {
							Set<Integer> argsHashCodeSet = methodArgsHashCodeMap.get(methodSignature);
							for(Integer argsHashCode : argsHashCodeSet){
								doRemoveData(methodSignature,argsHashCode);
							}

							methodArgsHashCodeMap.remove(methodSignature);
						}
						dataExpireInfo.remove(expireTimeStamp);
					}
				} finally {
					cacheDataLock.unlock();
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
	}

	private static void doRemoveData(String methodSignature, Integer argsHashCode) {
		try {
			CacheDataModel cacheDataModel = getDataFromMemory(methodSignature,argsHashCode);
			if(cacheDataModel != null && cacheDataModel.isExpired()){

				log(String.format(  "\n >>>> 移除缓存 <<<<" +
									"\n method：%s" +
									"\n args：%s" +
									"\n -----------------------",methodSignature,cacheDataModel.getArgs()));

				// <方法签名,<方法入参哈希,数据>>
				Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature);
				cacheDataModelMap.remove(argsHashCode);
				if(cacheDataModelMap.isEmpty()){
					cacheData.remove(methodSignature);
				}
			}

		}catch (Exception e){
			e.printStackTrace();
			logger.error("移除数据出现异常：" + e.getMessage());
		}finally {

		}
	}

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

			cacheDataModel = getDataFromMemory(methodSignature,argsHashCode);
			log(String.format(  "\n ************* CacheData *************" +
								"\n **--------- 从内存中获取缓存 ------- **" +
								"\n ** 方法签名：%s" +
								"\n ** 方法入参：%s" +
								"\n ** 缓存命中：%s" +
								"\n ** 过期时间：%s" +
								"\n *************************************",
					methodSignature,
					argsInfo,
					cacheDataModel != null ? "是":"否",
					(cacheDataModel == null ? "无" : formatDate(cacheDataModel.getExpireTimeStamp()))));


		if (cacheDataModel == null || cacheDataModel.isExpired()) {
			try {
				// 没有获取到数据或者数据已过期，加锁再次尝试获取
				cacheDataLock.lock();
				cacheDataModel = getDataFromMemory(methodSignature, argsHashCode);
				log(String.format(	"\n ************* CacheData *************" +
									"\n **------- 从内存获取缓存(加锁) ----- **" +
									"\n ** 方法签名：%s" +
									"\n ** 方法入参：%s" +
									"\n ** 缓存命中：%s" +
									"\n ** 过期时间：%s" +
									"\n *************************************",
						methodSignature,
						argsInfo,
						cacheDataModel != null ? "是":"否",
						(cacheDataModel == null ? "无" : formatDate(cacheDataModel.getExpireTimeStamp()))));


				if (cacheDataModel == null || cacheDataModel.isExpired()) {
					// 没获取到数据或者数据已过期，发起实际请求

					Object data = actualDataFunctional.getActualData();
					log(String.format(	"\n ************* CacheData *************"+
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
											"\n ** --------- 设置缓存至内存 -------- **" +
											"\n ** 方法签名：%s" +
											"\n ** 方法入参：%s" +
											"\n ** 缓存数据：%s" +
											"\n ** 过期时间：%s" +
											"\n *************************************",
								methodSignature,
								argsInfo,
								data,
								formatDate(expirationTime)));

						setDataToMemory(methodSignature, argsHashCode, argsInfo, data, expirationTime);

					}

					return data;
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.info("\n ************* CacheData *************" +
							"\n ** -------- 获取数据发生异常 ------- **" +
							"\n ** 异常信息：" + e.getMessage() +
							"\n *************************************");
				return null;

			} finally {
				cacheDataLock.unlock();
			}
		}


			if(refreshData){
				// 刷新数据
				executorService.execute(()->{
					try {
						cacheDataLock.lock();
						Object data = actualDataFunctional.getActualData();
						if (data != null) {
							long expirationTime = actualDataFunctional.getExpirationTime();
							log(String.format(  "\n ************* CacheData *************" +
												"\n ** --------- 刷新缓存至内存 -------- **" +
												"\n ** 方法签名：%s" +
												"\n ** 方法入参：%s" +
												"\n ** 缓存数据：%s" +
												"\n ** 过期时间：%s" +
												"\n *************************************",
									method,
									Arrays.toString(args),
									data,
									formatDate(expirationTime)));

							setDataToMemory(methodSignature, argsHashCode, argsInfo, data, expirationTime);
						}
					} catch (Throwable throwable) {
						throwable.printStackTrace();
						logger.info("\n ************* CacheData *************" +
									"\n ** ---- 异步更新数据至内存发生异常 --- **" +
									"\n 异常信息：" + throwable.getMessage() +
									"\n *************************************");
					}finally {
						cacheDataLock.unlock();
					}
				});
			}
			return cacheDataModel.getData();


	}

	@Override
	public List<Map<String, String>> getKeys(String key) {
		List<Map<String,String>> keyList = new ArrayList<>();
		Set<String> hkeys = cacheData.keySet();
		for(String item : hkeys){
			Map<Long, CacheDataModel> dataModelMap = cacheData.get(item); // <方法入参哈希值,数据>
			Set<Long> argsHashCodes = dataModelMap.keySet(); // 方法入参哈希值集合
			for(long argsHashCode : argsHashCodes){
				String itemKey = item + "_" + argsHashCode;
				String itemHashCode = String.valueOf(itemKey.hashCode());
				if(!StringUtils.isEmpty(key)){
					if(itemKey.contains(key) || itemHashCode.contains(key)){
						Map<String, String> keyMap = new HashMap<>();
						keyMap.put("key",itemKey);
						keyMap.put("hash",itemHashCode);
						keyList.add(keyMap);

					}
				}else {
					Map<String, String> keyMap = new HashMap<>();
					keyMap.put("key",itemKey);
					keyMap.put("hash",itemHashCode);
					keyList.add(keyMap);
				}
			}

		}
		return keyList;
	}

	@Override
	public CacheDataModel getData(String key) {
		// TODO: 2022/4/15
//		return new CacheDataModel("",0,"");
		return null;
	}

	/**
	 * 从内存获取数据
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * */
	private static CacheDataModel getDataFromMemory(String methodSignature, long argsHashCode){

		Map<Long, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature);
		if(cacheDataModelMap != null){
			return cacheDataModelMap.get(argsHashCode);
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
	private boolean setDataToMemory(String methodSignature,long argsHashCode, String args, Object data, long expireTimeStamp) {
		CacheDataModel cacheDataModel = new CacheDataModel(methodSignature, (long)methodSignature.hashCode(), args, argsHashCode, data, expireTimeStamp);

		Map<Long, CacheDataModel> cacheDataModelMap = cacheData.computeIfAbsent(methodSignature, k -> new HashMap<>());
		cacheDataModelMap.put(argsHashCode,cacheDataModel);

		// 缓存过期时间
		if (expireTimeStamp > 0L) {
			// 记录缓存数据过期信息 <过期时间（时间戳，毫秒）,<方法签名,方法入参哈希>>
			Map<String,Set<Long>> methodArgsHashCodeMap = dataExpireInfo.computeIfAbsent(expireTimeStamp, k -> new HashMap<>());
			methodArgsHashCodeMap.computeIfAbsent(methodSignature,k->new HashSet<>()).add(argsHashCode);
		}

		return true;
	}

	/**
	 * 移除过期数据
	 * */
	private static void doRemoveData(String methodSignature, Integer argsHashCode) {
		try {
			CacheDataModel cacheDataModel = getDataFromMemory(methodSignature,argsHashCode);
			if(cacheDataModel != null && cacheDataModel.isExpired()){

				log(String.format(  "\n ************* CacheData *************" +
									"\n ** ------------ 移除缓存 ---------- **" +
									"\n ** 方法签名：%s" +
									"\n ** 方法入参：%s" +
									"\n *************************************",
						methodSignature,
						cacheDataModel.getArgs()));

				// <方法签名,<方法入参哈希,数据>>
				Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature);
				cacheDataModelMap.remove(argsHashCode);
				if(cacheDataModelMap.isEmpty()){
					cacheData.remove(methodSignature);
				}
			}

		}catch (Exception e){
			e.printStackTrace();
			logger.error(	"\n ************* CacheData *************" +
							"\n ** 移除数据出现异常：" + e.getMessage() +
							"\n *************************************");
		}
	}

	private static void log(String info){
		if(enableLog){
			logger.info(info);
		}
	}
}
