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
	 * 内容：<方法签名,<缓存哈希值,数据>>
	 * */
	private final static Map<String, Map<Integer, CacheDataModel>> cacheData = new ConcurrentHashMap<>();

	/**
	 * 缓存数据过期信息
	 * 内容：<过期时间(时间戳，毫秒),<方法签名,[缓存哈希值]>>
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
						Map<String,Set<Integer>> methodArgsHashCodeMap = dataExpireInfo.get(expireTimeStamp); // <方法签名,[缓存哈希值]>
						Set<String> methodSignatureSet =  methodArgsHashCodeMap.keySet();
						for (String methodSignature : methodSignatureSet) {
							Set<Integer> cacheHashCodeSet = methodArgsHashCodeMap.get(methodSignature);
							for(Integer cacheHashCode : cacheHashCodeSet){
								doRemoveData(methodSignature,cacheHashCode);
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
		int methodSignatureHashCode = methodSignature.hashCode(); // 方法签名哈希值
		int argsHashCode = DataUtil.getArgsHashCode(args); // 入参哈希值
		String argsInfo = Arrays.toString(args); // 入参内容
		int cacheHashCode = DataUtil.hash(String.valueOf(methodSignatureHashCode) + String.valueOf(argsHashCode)); // 缓存哈希值

		if(StringUtils.isEmpty(id)){
			id = String.valueOf(methodSignature.hashCode());
		}

		CacheDataModel cacheDataModel = getDataFromMemory(methodSignature,cacheHashCode);
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
				// 加锁再次获取
				cacheDataLock.lock();
				cacheDataModel = getDataFromMemory(methodSignature, cacheHashCode);
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

						setDataToMemory(methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, data, expirationTime, id, remark);

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


		if (refreshData) {

			final String finalId = id;

			// 刷新数据
			executorService.execute(() -> {
				try {
					cacheDataLock.lock();
					Object data = actualDataFunctional.getActualData();
					if (data != null) {
						long expirationTime = actualDataFunctional.getExpirationTime();
						log(String.format(	"\n ************* CacheData *************" +
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

						setDataToMemory(methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, data, expirationTime, finalId, remark);
					}
				} catch (Throwable throwable) {
					throwable.printStackTrace();
					logger.info("\n ************* CacheData *************" +
								"\n ** ---- 异步更新数据至内存发生异常 --- **" +
								"\n 异常信息：" + throwable.getMessage() +
								"\n *************************************");
				} finally {
					cacheDataLock.unlock();
				}
			});
		}
		return cacheDataModel.getData();


	}

	@Override
	public Map<String, Map<String,Object>> getCaches(String match, String select) {

		Map<String, Map<String,Object>> cacheMap = new HashMap<>();

		Set<Map<Integer, CacheDataModel>> dataModelMapSet = new HashSet<>(cacheData.values()); //缓存数据
		for(Map<Integer, CacheDataModel> dataModelMap : dataModelMapSet){ // <缓存哈希值,数据>
			if(dataModelMap.isEmpty()){
				continue;
			}

			Set<CacheDataModel> dataModelSet;

			if(StringUtils.isEmpty(match)){
				dataModelSet = new HashSet<>(dataModelMap.values());
			}else {
				// 模糊匹配，支持：方法签名、缓存哈希值、缓存ID
				dataModelSet = new HashSet<>();
				for(Integer cacheHashCode : dataModelMap.keySet()){
					if(String.valueOf(cacheHashCode).contains(match)){
						CacheDataModel dataModel = dataModelMap.get(cacheHashCode);

						if(dataModel.isExpired()){
							continue;
						}
						dataModelSet.add(dataModel);
					}
				}

				for(CacheDataModel dataModel : dataModelMap.values()){
					if(dataModel.isExpired()){
						continue;
					}

					String methodSignature = dataModel.getMethodSignature();
					String id = dataModel.getId();

					if(methodSignature.contains(match) || id.contains(match)){
						dataModelSet.add(dataModel);
					}
				}
			}

			for (CacheDataModel dataModel : dataModelSet) {
				if(dataModel == null || dataModel.isExpired()){
					continue;
				}

				filterDataModel(cacheMap, dataModel, select);
			}

		}
		return cacheMap;
	}


	@Override
	public Map<String, Map<String, Object>> wipeCache(String id, String cacheHashCode) {

		Map<String, Map<String, Object>> delCacheMap = new HashMap<>();

		Set<Map<Integer, CacheDataModel>> dataModelMapSet = new HashSet<>(cacheData.values()); //缓存数据

		try {
			cacheDataLock.lock();

			Set<Integer> removeCacheHashCode = new HashSet<>();
			for (Map<Integer, CacheDataModel> dataModelMap : dataModelMapSet) { // <缓存哈希值,数据>
				if (dataModelMap.isEmpty()) {
					continue;
				}

				for(Integer key : dataModelMap.keySet()){
					CacheDataModel dataModel = dataModelMap.get(key);
					if(dataModel == null || dataModel.isExpired()){
						continue;
					}

					if (	(StringUtils.isEmpty(id) && StringUtils.isEmpty(cacheHashCode)) ||
							id.equals(dataModel.getId()) ||
							cacheHashCode.equals(String.valueOf(dataModel.getCacheHashCode()))
					) {
						dataModel.expired();

						dataModelMap.remove(key);
						removeCacheHashCode.add(dataModel.getCacheHashCode());
						filterDataModel(delCacheMap, dataModel, "");
					}
				}
			}

			if(removeCacheHashCode.size() > 0){
				Set<Map<String,Set<Integer>>>  dataExpireInfoValues = new HashSet<>(dataExpireInfo.values()); //<过期时间(时间戳，毫秒),<方法签名,[缓存哈希值]>>
				for(Map<String,Set<Integer>> dataExpireInfoValue : dataExpireInfoValues){
					for(Set<Integer> c : dataExpireInfoValue.values()){
//						c.removeAll(removeCacheHashCode);
						// TODO: 2022/6/10  
					}
				}
			}


		} catch (Exception e) {
			e.printStackTrace();
			cacheDataLock.unlock();
		}

		return delCacheMap;
	}

	/**
	 * 从内存获取数据
	 *
	 * @param methodSignature 方法签名
	 * @param cacheHashCode 缓存哈希值
	 * */
	private static CacheDataModel getDataFromMemory(String methodSignature, Integer cacheHashCode){

		Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature);
		if(cacheDataModelMap != null){
			return cacheDataModelMap.get(cacheHashCode);
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
	private void setDataToMemory(String methodSignature, int methodSignatureHashCode, String args, int argsHashCode, int cacheHashCode,  Object data, long expireTime, String id, String remark) {

		CacheDataModel cacheDataModel = new CacheDataModel(methodSignature, methodSignatureHashCode, args, argsHashCode, cacheHashCode, data, expireTime);

		if(!StringUtils.isEmpty(id)){
			cacheDataModel.setId(id);
		}

		if(!StringUtils.isEmpty(remark)){
			cacheDataModel.setRemark(remark);
		}

		setDataToMemory(cacheDataModel);

	}

	/**
	 * 缓存数据至内存
	 * */
	private void setDataToMemory(CacheDataModel cacheDataModel) {

		String methodSignature = cacheDataModel.getMethodSignature();
		int cacheHashCode = cacheDataModel.getCacheHashCode();

		Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.computeIfAbsent(methodSignature, k -> new HashMap<>());
		cacheDataModelMap.put(cacheHashCode,cacheDataModel);

		long expireTime = cacheDataModel.getExpireTime();

		if (expireTime > 0L) {
			// 记录缓存数据过期信息 <过期时间（时间戳，毫秒）,<方法签名,缓存哈希值>>
			Map<String,Set<Integer>> methodArgsHashCodeMap = dataExpireInfo.computeIfAbsent(expireTime, k -> new HashMap<>());
			methodArgsHashCodeMap.computeIfAbsent(methodSignature,k->new HashSet<>()).add(cacheHashCode);
		}

	}

	/**
	 * 移除过期数据
	 * */
	private static void doRemoveData(String methodSignature, Integer cacheHashCode) {
		try {
			CacheDataModel cacheDataModel = getDataFromMemory(methodSignature,cacheHashCode);
			if(cacheDataModel != null && cacheDataModel.isExpired()){

				log(String.format(  "\n ************* CacheData *************" +
									"\n ** ------------ 移除缓存 ---------- **" +
									"\n ** 方法签名：%s" +
									"\n ** 方法入参：%s" +
									"\n *************************************",
						methodSignature,
						cacheDataModel.getArgs()));

				Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature); // <缓存哈希值,数据>
				cacheDataModelMap.remove(cacheHashCode);
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

	public static void main(String[] args) {

		Integer integer1 = 1;
		Integer integer2 = 2;
		Integer integer3 = 3;

		Set<Integer> set1 = new HashSet<>();
		set1.add(integer1);
		set1.add(integer2);

		Set<Integer> set2 = new HashSet<>();
		set2.add(integer3);


		Map<String,Set<Integer>> mapA = new HashMap<>();
		mapA.put("1",set1);
		mapA.put("2",set2);

		System.out.println(">>>" + mapA.values());



	}
}
