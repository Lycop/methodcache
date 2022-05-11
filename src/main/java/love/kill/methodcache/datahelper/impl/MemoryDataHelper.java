package love.kill.methodcache.datahelper.impl;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.datahelper.CacheDataModel;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.DataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

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

	private static Logger logger = LoggerFactory.getLogger(MemoryDataHelper.class);

	private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

	/**
	 * 属性配置
	 * */
	private final MethodcacheProperties methodcacheProperties;

	/**
	 * 开启日志
	 * */
	private static boolean enableLog = false;

	public MemoryDataHelper(MethodcacheProperties methodcacheProperties) {
		this.methodcacheProperties = methodcacheProperties;
		enableLog = methodcacheProperties.isEnableLog();
	}

	/**
	 * 缓存数据
	 * 内容：<方法签名,<方法入参哈希,数据>>
	 * */
	private final static Map<String, Map<Integer, CacheDataModel>> cacheData = new ConcurrentHashMap<>();

	/**
	 * 缓存数据过期信息
	 * 内容：<过期时间（时间戳，毫秒）,<方法签名,方法入参哈希>>
	 * */
	private final static Map<Long, Map<String,Set<Integer>>> dataExpireInfo = new ConcurrentHashMap<>();


	/**
	 * 缓存数据锁
	 * */
	private static ReentrantLock cacheDataLock = new ReentrantLock();


	static {

		/**
		 * 剔除过期数据
		 * */
		Executors.newSingleThreadExecutor().execute(()->{
			while (true){
				List<Long> expireTimeStampKeySetKeyList;
				Set<Long> expireTimeStampKeySet;
				try {
					cacheDataLock.lock();
					expireTimeStampKeySet = dataExpireInfo.keySet();
					if (expireTimeStampKeySet.size() <= 0) {
						// 没有过期信息
						continue;
					}

					expireTimeStampKeySetKeyList = new ArrayList<>(expireTimeStampKeySet);
					expireTimeStampKeySetKeyList.sort((l1, l2) -> (int) (l1 - l2));
					long nowTimeStamp = new Date().getTime();
					for (long expireTimeStamp : expireTimeStampKeySetKeyList) {
						if (expireTimeStamp > nowTimeStamp) {
							// 最接近当前时间的数据还没到期
							break;
						}

						// 移除过期缓存数据
						Map<String,Set<Integer>> methodArgsHashCodeMap = dataExpireInfo.get(expireTimeStamp); // <方法签名,方法入参哈希集合>
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

	@Override
	public Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark) {

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
					(cacheDataModel == null ? "无" : formatDate(cacheDataModel.getExpireTime()))));


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
						(cacheDataModel == null ? "无" : formatDate(cacheDataModel.getExpireTime()))));


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

						setDataToMemory(methodSignature, argsHashCode, argsInfo, data, expirationTime, id, remark);

					}

					return data;
				}
			} catch (Throwable throwable) {
				throwable.printStackTrace();
				logger.info("\n ************* CacheData *************" +
							"\n ** -------- 获取数据发生异常 ------- **" +
							"\n ** 异常信息：" + throwable.getMessage() +
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

							setDataToMemory(methodSignature, argsHashCode, argsInfo, data, expirationTime, id, remark);
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
	public Map<String, Map<String,Object>> getCaches(String key) {

		Map<String, Map<String,Object>> cacheMap = new HashMap<>();

		List<Map<Integer, CacheDataModel>> dataModelMapValues= new ArrayList<>(cacheData.values()); //cacheData <方法签名,<方法入参哈希,数据>>
		for(Map<Integer, CacheDataModel> dataModelMap : dataModelMapValues){
			if(dataModelMap.isEmpty()){
				continue;
			}
			List<CacheDataModel> dataModelList = new ArrayList<>(dataModelMap.values());
			for(CacheDataModel cacheDataModel : dataModelList){
				if(cacheDataModel.isExpired()){
					// 缓存已过期
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
	public void wipeCache(int cacheHashCode) {
		List<Map<Integer, CacheDataModel>> dataModelMapValues = new ArrayList<>(cacheData.values()); // cacheData <方法签名,<方法入参哈希,数据>>
		if (dataModelMapValues.size() <= 0) {
			return;
		}

		try {
			cacheDataLock.lock();

			dataModelMapValues = new ArrayList<>(cacheData.values());
			if (dataModelMapValues.size() <= 0) {
				return;
			}

			for (Map<Integer, CacheDataModel> dataModelMap : dataModelMapValues) {
				if (dataModelMap.isEmpty()) {
					continue;
				}
				List<CacheDataModel> dataModelList = new ArrayList<>(dataModelMap.values());
				for (CacheDataModel cacheDataModel : dataModelList) {

					if (cacheDataModel == null || cacheDataModel.isExpired()) {
						// 缓存已过期
						continue;
					}

					if (cacheDataModel.getCacheHashCode() == cacheHashCode) {
						cacheDataModel.expired();
						setDataToMemory(cacheDataModel);
						break;
					}
				}
			}

		} catch (Throwable throwable) {
			throwable.printStackTrace();
		} finally {
			cacheDataLock.unlock();
		}
	}

	/**
	 * 从内存获取数据
	 *
	 * @param methodSignature 方法签名
	 * @param argsHashCode 入参哈希
	 * */
	private static CacheDataModel getDataFromMemory(String methodSignature, Integer argsHashCode){

		Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature);
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
	 * @param expireTime 过期时间
	 *
	 * 这里会对返回值进行反序列化
	 * */
	private void setDataToMemory(String methodSignature,Integer argsHashCode, String args, Object data, long expireTime,String id, String remark) {
		CacheDataModel cacheDataModel = new CacheDataModel(methodSignature, args, argsHashCode, data, expireTime);

		if(StringUtils.isEmpty(id)){
			cacheDataModel.setId(String.valueOf(cacheDataModel.getMethodSignatureHashCode()));
		}else {
			cacheDataModel.setId(id);
		}

		if(!StringUtils.isEmpty(remark)){
			cacheDataModel.setRemark(remark);
		}

		setDataToMemory(cacheDataModel);

	}

	/**
	 * 缓存数据至内存
	 * 这里会对返回值进行反序列化
	 * */
	private void setDataToMemory(CacheDataModel cacheDataModel) {

		String methodSignature = cacheDataModel.getMethodSignature();
		int argsHashCode = cacheDataModel.getArgsHashCode();

		Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.computeIfAbsent(methodSignature, k -> new HashMap<>());
		cacheDataModelMap.put(argsHashCode,cacheDataModel);

		long expireTime = cacheDataModel.getExpireTime();

		// 缓存过期时间
		if (expireTime > 0L) {
			// 记录缓存数据过期信息 <过期时间（时间戳，毫秒）,<方法签名,方法入参哈希>>
			Map<String,Set<Integer>> methodArgsHashCodeMap = dataExpireInfo.computeIfAbsent(expireTime, k -> new HashMap<>());
			methodArgsHashCodeMap.computeIfAbsent(methodSignature,k->new HashSet<>()).add(argsHashCode);
		}

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
