package love.kill.methodcache.datahelper.impl;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.SpringApplicationProperties;
import love.kill.methodcache.datahelper.CacheDataModel;
import love.kill.methodcache.datahelper.CacheSituationModel;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.util.DataUtil;
import love.kill.methodcache.MemoryMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
	 * spring属性
	 * */
	private final SpringApplicationProperties springProperties;

	/**
	 * 输出缓存日志
	 * */
	private boolean enableLog;

	/**
	 * 开启记录
	 * */
	private boolean enableRecord;

	/**
	 * GC阈值
	 * */
	private final double gcThreshold;

	/**
	 * GC锁头
	 * */
	private final static Object gcLock = new Object();

	/**
	 * 缓存数据
	 * 内容：<方法签名,<缓存哈希值,数据>>
	 * */
	private final static Map<String, Map<Integer, CacheDataModel>> cacheData = new ConcurrentHashMap<>();

	/**
	 * 缓存数据大小
	 * */
	private static AtomicLong cacheDataSize = new AtomicLong(0L);

	/**
	 * 缓存数据个数
	 * */
	private static AtomicInteger cacheDataCount = new AtomicInteger(0);

	/**
	 * 缓存数据过期信息
	 * 内容：<过期时间(时间戳，毫秒),<方法签名,[缓存哈希值]>>
	 * */
	private final static Map<Long, Map<String,Set<Integer>>> dataExpireInfo = new ConcurrentHashMap<>();

	/**
	 * 缓存情况
	 * 内容：<方法签名,<缓存哈希值, 缓存情况>>
	 * */
	private final static Map<String, Map<Integer, CacheSituationModel>> cacheSituation = new ConcurrentHashMap<>();

	/**
	 * 缓存数据锁
	 * */
	private static ReentrantLock cacheDataLock = new ReentrantLock();

	/**
	 * 缓存数据锁
	 * */
	private static ReentrantLock cacheSituationLock = new ReentrantLock();


	public MemoryDataHelper(MethodcacheProperties methodcacheProperties, SpringApplicationProperties springProperties, MemoryMonitor memoryMonitor) {
		this.methodcacheProperties = methodcacheProperties;
		this.springProperties = springProperties;
		this.enableLog = methodcacheProperties.isEnableLog();
		this.enableRecord = methodcacheProperties.isEnableRecord();
		this.gcThreshold = new BigDecimal(methodcacheProperties.getGcThreshold()).divide(new BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP).doubleValue();

		/**
		 * 移除过期数据
		 * */
		Runnable removeExpireData = () -> {
			while (true) {
				List<Long> expireTimeStampKeyList;
				try {
					cacheDataLock.lock();
					expireTimeStampKeyList = new ArrayList<>(dataExpireInfo.keySet());
					if (expireTimeStampKeyList.size() <= 0) {
						// 没有过期信息
						continue;
					}

					expireTimeStampKeyList.sort((l1, l2) -> (int) (l1 - l2));
					long nowTimeStamp = new Date().getTime();
					for (long expireTimeStamp : expireTimeStampKeyList) {
						if (expireTimeStamp > nowTimeStamp) {
							// 最接近当前时间的数据还没到期
							break;
						}

						// 移除过期缓存数据
						Map<String, Set<Integer>> methodArgsHashCodeMap = dataExpireInfo.get(expireTimeStamp);
						Set<String> methodSignatureSet = methodArgsHashCodeMap.keySet();
						for (String methodSignature : methodSignatureSet) {
							Set<Integer> cacheHashCodeSet = methodArgsHashCodeMap.get(methodSignature);
							for (Integer cacheHashCode : cacheHashCodeSet) {
								doRemoveData(methodSignature, cacheHashCode);
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
		};
		Executors.newSingleThreadExecutor().execute(removeExpireData);

		if(enableRecord){
			/**
			 * 记录
			 * */
			Runnable recordRunnable = () -> {
				while (true) {
					try {
						CacheSituationNode situationNode = recordSituationInfoQueue.take();
						String methodSignature = situationNode.getMethodSignature();
						int cacheHashCode = situationNode.getCacheHashCode();

						try {
							cacheSituationLock.lock();

							CacheSituationModel situationModel = getSituation(situationNode, getSituationFromMemory(methodSignature, cacheHashCode));
							setSituationToMemory(situationModel);

						} finally {
							cacheSituationLock.unlock();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
			Executors.newSingleThreadExecutor().execute(recordRunnable);
		}

		if(memoryMonitor != null){
			// 监听内存状况
			memoryMonitor.sub(this::gc);
		}
	}

	/**
	 * 内存回收
	 * @param memUsage 内存使用信息
	 * */
	private void gc(MemoryUsage memUsage) {
		if(memUsage == null){
			memUsage = getMemoryOldGenUsage();

			if(memUsage == null){
				logger.error("[methodcache]获取内存信息失败");
				return;
			}
		}

		synchronized (gcLock) {
			try {
				cacheDataLock.lock();

				long used = memUsage.getUsed();
				long max = memUsage.getMax();
				long cacheDataSize = getCacheDataSize();

				if (!isAlarmed(cacheDataSize, used, gcThreshold)) {
					logger.info("[methodcache]缓存数据未达到GC阈值，本次不回收。数据大小：" + cacheDataSize + "；内存使用：" + used + "；GC阈值=" + gcThreshold);
					return;
				}

				doGC(cacheDataSize, used, max);

			} finally {
				cacheDataLock.unlock();
			}
		}
	}

	/**
	 * 内存回收
	 *
	 * @param size 缓存数据大小
	 * @param used 内存使用大小
	 * @param max  内存最大上限
	 */
	private void doGC(long size, long used, long max) {
		// 断言本次回收目标
		long gcCapacity = assertGCCapacity(size, used, max);
		logger.info("[methodcache]开始GC：数据条数=" + getCacheDataCount() + "，数据大小=" + size + "，计划回收=" + gcCapacity + "(" + (gcCapacity >> 10) + "K)");
		long startAt = new Date().getTime();
		RemoveDataModel removeDataModel = removeData(gcCapacity);
		System.gc();
		logger.info("[methodcache]GC完成：数据条数=" + getCacheDataCount() + "，数据大小=" + getCacheDataSize() + "，" +
				"实际回收=" + removeDataModel.getSize() + "(" + (removeDataModel.getSize() >> 10) + "K)，回收条数=" + removeDataModel.getCount() + "，" +
				"耗时=" + (new Date().getTime() - startAt));
	}


	/**
	 * 获取当前内存占用信息
	 */
	private MemoryUsage getMemoryOldGenUsage() {
		List<MemoryPoolMXBean> memPools = ManagementFactory.getMemoryPoolMXBeans();
		if (memPools != null) {
			for (MemoryPoolMXBean mp : memPools) {
				if (mp.getName().toLowerCase().endsWith("old gen")) {
					return mp.getUsage();
				}
			}
		}
		return null;
	}

	/**
	 * 获取缓存数据大小
	 *
	 * @return 数据大小，单位byte
	 * */
	private long getCacheDataSize() {
		return cacheDataSize.get();
	}

	/**
	 * 获取缓存数据条数
	 *
	 * @return 数据条数
	 * */
	private int getCacheDataCount() {
		return cacheDataCount.get();
	}

	/**
	 * 断言回收数据的大小
	 * @param size 缓存数据大小
	 * @param used 内存使用大小
	 * @param max  内存最大上限
	 * @return 回收大小
	 * */
	private long assertGCCapacity(long size, long used, long max) {

		BigDecimal biSize = new BigDecimal(size);
		BigDecimal biUsed = new BigDecimal(used);
		BigDecimal biMax = new BigDecimal(max);

		if(biSize.compareTo(biUsed.multiply(new BigDecimal(0.9))) >= 0 || biSize.compareTo(biMax.multiply(new BigDecimal(0.4))) >= 0){
			return biSize.multiply(new BigDecimal(0.3)).longValue();
		}

		return biSize.multiply(new BigDecimal(0.5)).longValue();
	}

	/**
	 * 删除数据
	 * @param targetCapacity 预期回收大小
	 * */
	private RemoveDataModel removeData(long targetCapacity){

		RemoveDataModel removeDataModel = new RemoveDataModel();

		List<Long> expireTimeStampKeyList = new ArrayList<>(dataExpireInfo.keySet());
		if(expireTimeStampKeyList.size() <= 0){
			// 没有可删除的信息
			return removeDataModel;
		}

		// 按时间顺序回收缓存数据
		long deleteInstanceSize = 0L;
		expireTimeStampKeyList.sort((l1, l2) -> (int) (l1 - l2));
		for (long eachExpireTimeStamp : expireTimeStampKeyList) {
			Map<String, Set<Integer>> dataExpireInfoMethodSignatureCacheHashCodeMap = dataExpireInfo.get(eachExpireTimeStamp);
			for(String dataExpireInfoMethodSignature : dataExpireInfoMethodSignatureCacheHashCodeMap.keySet()){
				Map<Integer, CacheDataModel> cacheDataCacheHashCodeModelMap = cacheData.get(dataExpireInfoMethodSignature);
				Set<Integer> dataExpireInfoCacheHashCodeSet = dataExpireInfoMethodSignatureCacheHashCodeMap.get(dataExpireInfoMethodSignature);
				Iterator<Integer> dataExpireInfoCacheHashCodeIterator = dataExpireInfoCacheHashCodeSet.iterator();
				while (dataExpireInfoCacheHashCodeIterator.hasNext()){
					Integer dataExpireInfoCacheHashCode = dataExpireInfoCacheHashCodeIterator.next();
					CacheDataModel cacheDataModel = cacheDataCacheHashCodeModelMap.remove(dataExpireInfoCacheHashCode);

					long instanceSize = cacheDataModel.getInstanceSize();
					cacheDataSize.addAndGet(-instanceSize);
					cacheDataCount.decrementAndGet();
					dataExpireInfoCacheHashCodeIterator.remove();

					removeDataModel.addCount(1);
					if(removeDataModel.addSize(instanceSize) >= targetCapacity){ // 累加实例大小
						break;
					}
				}
				if(dataExpireInfoCacheHashCodeSet.isEmpty()){
					dataExpireInfoMethodSignatureCacheHashCodeMap.remove(dataExpireInfoMethodSignature);
				}

				if(deleteInstanceSize >= targetCapacity ){
					break;
				}
			}
			if(dataExpireInfoMethodSignatureCacheHashCodeMap.isEmpty()){
				dataExpireInfo.remove(eachExpireTimeStamp);
			}
			if(deleteInstanceSize >= targetCapacity ){
				break;
			}
		}
		return removeDataModel;
	}


	@Override
	public Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark, boolean nullable) {

		String methodSignature = method.toGenericString(); // 方法签名
		int methodSignatureHashCode = methodSignature.hashCode(); // 方法签名哈希值
		int argsHashCode = DataUtil.getArgsHashCode(args); // 入参哈希值
		String argsInfo = Arrays.toString(args); // 入参内容
		int cacheHashCode = DataUtil.hash(String.valueOf(methodSignatureHashCode) + String.valueOf(argsHashCode)); // 缓存哈希值
		if(StringUtils.isEmpty(id)){
			id = String.valueOf(methodSignature.hashCode());
		}
		long startTime = new Date().getTime();
		CacheDataModel cacheDataModel = getDataFromMemory(methodSignature,cacheHashCode);
		boolean hit = (cacheDataModel != null);

		log(String.format(  "\n ************* CacheData *************" +
							"\n **--------- 从内存中获取缓存 ------- **" +
							"\n ** 方法签名：%s" +
							"\n ** 方法入参：%s" +
							"\n ** 缓存命中：%s" +
							"\n ** 过期时间：%s" +
							"\n *************************************",
				methodSignature,
				argsInfo,
				hit ? "是":"否",
				hit ? formatDate(cacheDataModel.getExpireTime()) : "无"));


		if (!hit || cacheDataModel.isExpired()) {
			try {
				// 加锁再次获取
				cacheDataLock.lock();
				cacheDataModel = getDataFromMemory(methodSignature, cacheHashCode);
				hit = (cacheDataModel != null);
				log(String.format(	"\n ************* CacheData *************" +
									"\n **------- 从内存获取缓存(加锁) ----- **" +
									"\n ** 方法签名：%s" +
									"\n ** 方法入参：%s" +
									"\n ** 缓存命中：%s" +
									"\n ** 过期时间：%s" +
									"\n *************************************",
						methodSignature,
						argsInfo,
						hit ? "是":"否",
						hit ? formatDate(cacheDataModel.getExpireTime()) : "无"));


				if (!hit || cacheDataModel.isExpired()) {
					// 发起实际请求
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

					if (data != null || nullable) {
						long expirationTime = actualDataFunctional.getExpirationTime();
						log(String.format(	"\n ************* CacheData *************" +
											"\n ** --------- 保存缓存至内存 -------- **" +
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
					if (enableRecord) {
						record(null, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, id, remark, hit, startTime);
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
					if (data != null || nullable) {
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
		if (enableRecord) {
			record(null, methodSignature, methodSignatureHashCode, argsInfo, argsHashCode, cacheHashCode, id, remark, hit, startTime);
		}
		return cacheDataModel.getData();


	}

	@Override
	public Map<String, Map<String,Object>> getCaches(String match) {

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
				// 模糊匹配，支持：缓存哈希值、方法签名、缓存ID

				dataModelSet = new HashSet<>();
				for(Integer cacheHashCode : dataModelMap.keySet()){
					CacheDataModel dataModel = dataModelMap.get(cacheHashCode);
					String methodSignature = dataModel.getMethodSignature();
					String id = dataModel.getId();
					if(match.equals(String.valueOf(cacheHashCode)) || methodSignature.contains(match) || id.contains(match)){
						if(!dataModel.isExpired()){
							dataModelSet.add(dataModel);
						}
					}
				}
			}

			for (CacheDataModel dataModel : dataModelSet) {
				if(dataModel != null && !dataModel.isExpired()){
					filterDataModel(cacheMap, dataModel, null);
				}
			}

		}
		return cacheMap;
	}


	@Override
	public Map<String, Map<String, Object>> wipeCache(String id, String cacheHashCode) {

		Map<String, Map<String, Object>> delCacheMap = new HashMap<>();
		try {
			cacheDataLock.lock();
			Set<Integer> removeCacheHashCode = new HashSet<>();
			for (Map<Integer, CacheDataModel> dataModelMap : new HashSet<>(cacheData.values())) { // <缓存哈希值,数据>
				if (dataModelMap.isEmpty()) {
					continue;
				}
				Iterator<Integer> iterator = dataModelMap.keySet().iterator();
				while (iterator.hasNext()){
					Integer key = iterator.next();
					CacheDataModel dataModel = dataModelMap.get(key);
					if(dataModel == null || dataModel.isExpired()){
						continue;
					}

					String dataModelId = dataModel.getId();
					String dataModelCacheHashCode = String.valueOf(dataModel.getCacheHashCode());

					if ((StringUtils.isEmpty(id) && StringUtils.isEmpty(cacheHashCode)) ||
							dataModelId.equals(id) ||
							dataModelCacheHashCode.equals(cacheHashCode)
					) {
						dataModel.expired();
						removeCacheHashCode.add(dataModel.getCacheHashCode());
						filterDataModel(delCacheMap, dataModel, "");

						iterator.remove();
					}
				}
			}

			if(removeCacheHashCode.size() > 0){
				Iterator<Map<String, Set<Integer>>> dataExpireInfoValuesIterator = dataExpireInfo.values().iterator(); // <方法签名,[缓存哈希值]>
				while (dataExpireInfoValuesIterator.hasNext()){
					Map<String,Set<Integer>> dataExpireInfoValue = dataExpireInfoValuesIterator.next();
					Iterator<Set<Integer>> dataExpireInfoValueIterator = dataExpireInfoValue.values().iterator();
					while (dataExpireInfoValueIterator.hasNext()){
						Set<Integer> cacheHashCodeInDataExpireInfo = dataExpireInfoValueIterator.next();
						cacheHashCodeInDataExpireInfo.removeAll(removeCacheHashCode);
						if(cacheHashCodeInDataExpireInfo.isEmpty()){
							dataExpireInfoValueIterator.remove();
						}
					}

					if(dataExpireInfoValue.isEmpty()){
						dataExpireInfoValuesIterator.remove();
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			cacheDataLock.unlock();
		}

		return delCacheMap;
	}

	@Override
	public Map<String, Map<String, Object>> getSituation(String match) {

		Map<String, Map<String,Object>> situationMap = new HashMap<>();

		Set<Map<Integer, CacheSituationModel>> situationModelMapSet = new HashSet<>(cacheSituation.values()); // 缓存情况
		for(Map<Integer, CacheSituationModel> situationModelMap : situationModelMapSet){ // <缓存哈希值,数据>
			if(situationModelMap.isEmpty()){
				continue;
			}

			Set<CacheSituationModel> situationModelSet;

			if(StringUtils.isEmpty(match)){
				situationModelSet = new HashSet<>(situationModelMap.values());
			}else {
				// 模糊匹配，支持：缓存哈希值、方法签名、、缓存ID
				situationModelSet = new HashSet<>();
				for(Integer cacheHashCode : situationModelMap.keySet()){
					CacheSituationModel situationModel = situationModelMap.get(cacheHashCode);
					String methodSignature = situationModel.getMethodSignature();
					String id = situationModel.getId();
					if(match.equals(String.valueOf(cacheHashCode)) || methodSignature.contains(match) || id.contains(match)){
						situationModelSet.add(situationModel);
					}
				}
			}

			for (CacheSituationModel situationModel : situationModelSet) {
				if(situationModel != null){
					filterSituationModel(situationMap, situationModel, match);
				}
			}

		}
		return situationMap;
	}

	/**
	 * 从内存获取缓存数据
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
	 * 保存缓存数据至内存
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

		cacheDataSize.addAndGet(cacheDataModel.getInstanceSize());
		cacheDataCount.incrementAndGet();

	}

	/**
	 * 从内存中获取缓存情况
	 *
	 * @result 缓存情况
	 * */
	private CacheSituationModel getSituationFromMemory(String methodSignature, Integer cacheHashCode) {
		Map<Integer, CacheSituationModel> cacheSituationModelMap = cacheSituation.get(methodSignature);
		if(cacheSituationModelMap != null){
			return cacheSituationModelMap.get(cacheHashCode);
		}

		return null;
	}

	/**
	 * 保存缓存情况至内存
	 * */
	private void setSituationToMemory(CacheSituationModel situationModel) {

		String methodSignature = situationModel.getMethodSignature();
		int cacheHashCode = situationModel.getCacheHashCode();

		Map<Integer, CacheSituationModel> cacheSituationModelMap = cacheSituation.computeIfAbsent(methodSignature, k -> new HashMap<>());
		cacheSituationModelMap.put(cacheHashCode,situationModel);
	}



	/**
	 * 移除过期数据
	 * */
	private void doRemoveData(String methodSignature, Integer cacheHashCode) {
		try {
			CacheDataModel cacheDataModel = getDataFromMemory(methodSignature,cacheHashCode);
			if(cacheDataModel != null && cacheDataModel.isExpired()){
				doRemoveData(cacheDataModel);
			}

		}catch (Exception e){
			e.printStackTrace();
			logger.error(	"\n ************* CacheData *************" +
							"\n ** 移除数据出现异常：" + e.getMessage() +
							"\n *************************************");
		}
	}

	/**
	 * 移除数据
	 * */
	private void doRemoveData(CacheDataModel cacheDataModel) {
		String methodSignature = cacheDataModel.getMethodSignature();
		int cacheHashCode = cacheDataModel.getCacheHashCode();
		log(String.format(  "\n ************* CacheData *************" +
							"\n ** ------------ 移除缓存 ---------- **" +
							"\n ** 方法签名：%s" +
							"\n ** 方法入参：%s" +
							"\n *************************************",
				methodSignature,
				cacheDataModel.getArgs()));

		Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature); // <缓存哈希值,数据>
		cacheDataSize.addAndGet(-cacheDataModelMap.remove(cacheHashCode).getInstanceSize());
		cacheDataCount.decrementAndGet();
		if(cacheDataModelMap.isEmpty()){
			cacheData.remove(methodSignature);
		}
	}

	/**
	 * 打印日志
	 *
	 * @param info 内容
	 * */
	private void log(String info){
		if(enableLog){
			logger.info(info);
		}
	}

	/**
	 * 是否触发警告
	 *
	 *
	 * @param used 已用内存
	 * @param limit 上限
	 * @param cordon 警戒线
	 * */
	private boolean isAlarmed(long used, long limit, double cordon) {
		BigDecimal bigUsed = new BigDecimal(used);
		BigDecimal bigLimit = new BigDecimal(limit);
		return bigUsed.compareTo(bigLimit.multiply(new BigDecimal(cordon))) >= 0;
	}

	/**
	 * 删除数据模型
	 * */
	private class RemoveDataModel{
		/**
		 * 数据大小
		 * */
		private AtomicLong size;

		/**
		 * 数据个数
		 * */
		private AtomicInteger count;

		RemoveDataModel() {
			this.size = new AtomicLong(0L);
			this.count = new AtomicInteger(0);
		}

		long getSize() {
			return size.get();
		}

		int getCount() {
			return count.get();
		}

		long addSize(long size) {
			return this.size.addAndGet(size);
		}

		int addCount(int count) {
			return this.count.addAndGet(count);
		}
	}
}
