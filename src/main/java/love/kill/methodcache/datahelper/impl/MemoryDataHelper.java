package love.kill.methodcache.datahelper.impl;

import love.kill.methodcache.MethodcacheProperties;

import love.kill.methodcache.SpringApplicationProperties;
import love.kill.methodcache.datahelper.CacheDataModel;
import love.kill.methodcache.datahelper.CacheStatisticsModel;
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
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public class MemoryDataHelper implements DataHelper {

	private static Logger logger = LoggerFactory.getLogger(MemoryDataHelper.class);

	private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

	/**
	 * 缓存数据
	 * 内容：<方法签名,<缓存哈希值,数据>>
	 */
	private final static Map<String, Map<Integer, CacheDataModel>> cacheData = new ConcurrentHashMap<>();

	/**
	 * 缓存数据过期信息
	 * 内容：<过期时间(时间戳，毫秒),<方法签名,[缓存哈希值]>>
	 */
	private final static Map<Long, Map<String, Set<Integer>>> dataExpireInfo = new ConcurrentHashMap<>();

	/**
	 * 缓存统计
	 * 内容：<方法签名, 缓存情况>
	 */
	private final static Map<String, CacheStatisticsModel> cacheStatistics = new ConcurrentHashMap<>();

	/**
	 * 配置属性
	 */
	private final MethodcacheProperties methodcacheProperties;

	/**
	 * 应用名
	 * */
	private String applicationName;

	/**
	 * 缓存数据锁
	 */
	private static ReentrantLock cacheDataLock = new ReentrantLock();

	/**
	 * 缓存数据总大小
	 */
	private static AtomicLong cacheDataSize = new AtomicLong(0L);

	/**
	 * 缓存数据总个数
	 */
	private static AtomicInteger cacheDataCount = new AtomicInteger(0);


	/**
	 * GC阈值
	 */
	private final double gcThreshold;

	public MemoryDataHelper(MethodcacheProperties methodcacheProperties, SpringApplicationProperties springApplicationProperties,
							MemoryMonitor memoryMonitor) {
		this.methodcacheProperties = methodcacheProperties;

		if (StringUtils.isEmpty(this.applicationName = methodcacheProperties.getName())) {
			this.applicationName = springApplicationProperties.getName();
		}

		this.gcThreshold = new BigDecimal(methodcacheProperties.getGcThreshold())
				.divide(new BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP).doubleValue();


		// 移除过期数据
		Executors.newSingleThreadExecutor().execute(() -> {
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
						for (String methodSignature : new HashSet<>(methodArgsHashCodeMap.keySet())) {
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
		});

		// 统计
		if (methodcacheProperties.isEnableStatistics()) {
			Executors.newSingleThreadExecutor().execute(() -> {
				while (true) {
					try {
						CacheStatisticsNode statisticsNode = cacheStatisticsInfoQueue.take();
						synchronized (cacheStatistics) {
							String methodSignature = statisticsNode.getMethodSignature();
							CacheStatisticsModel statisticsModel = increaseStatistics(getCacheStatistics(methodSignature), statisticsNode);
							setCacheStatistics(methodSignature, statisticsModel);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}

		if (memoryMonitor != null) {
			// 监听内存状况
			memoryMonitor.sub(this::gc);
		}
	}


	@Override
	public Object getData(Object proxy, Method method, Object[] args, String isolationSignal, boolean refreshData,
						  ActualDataFunctional actualDataFunctional, String id, String remark,
						  boolean nullable) throws Throwable {

		long startTime = new Date().getTime();
		String methodSignature = method.toGenericString(); // 方法签名
		int methodSignatureHashCode = methodSignature.hashCode(); // 方法签名哈希值
		int argsHashCode = DataUtil.getArgsHashCode(args); // 入参哈希值
		String argsStr = Arrays.toString(args); // 入参
		int cacheHashCode = getCacheHashCode(applicationName, methodSignatureHashCode, argsHashCode, isolationSignal); // 缓存哈希值
		if (StringUtils.isEmpty(id)) {
			id = String.valueOf(methodSignature.hashCode());
		}


		String cacheKey = getCacheKey(applicationName, methodSignature, cacheHashCode, id);
		CacheDataModel cacheDataModel = getDataFromMemory(methodSignature, cacheHashCode);
		boolean hit = (cacheDataModel != null && !cacheDataModel.isExpired());
		log(String.format(	"\n ************* CacheData *************" +
							"\n **--------- 从内存中获取缓存 ------- **" +
							"\n ** 执行对象：%s" +
							"\n ** 方法签名：%s" +
							"\n ** 方法入参：%s" +
							"\n ** 缓存命中：%s" +
							"\n ** 过期时间：%s" +
							"\n *************************************",
				proxy,
				methodSignature,
				argsStr,
				hit ? "是" : "否",
				hit ? formatDate(cacheDataModel.getExpireTime()) : "无"));


		if (!hit) {
			try {
				// 加锁再次获取
				cacheDataLock.lock();
				cacheDataModel = getDataFromMemory(methodSignature, cacheHashCode);
			}finally {
				cacheDataLock.unlock();
			}

			hit = (cacheDataModel != null && !cacheDataModel.isExpired());
			log(String.format(	"\n ************* CacheData *************" +
								"\n **------- 从内存获取缓存(加锁) ----- **" +
								"\n ** 执行对象：%s" +
								"\n ** 方法签名：%s" +
								"\n ** 方法入参：%s" +
								"\n ** 缓存命中：%s" +
								"\n ** 过期时间：%s" +
								"\n *************************************",
					proxy,
					methodSignature,
					argsStr,
					hit ? "是" : "否",
					hit ? formatDate(cacheDataModel.getExpireTime()) : "无"));

			if (!hit) {
				Object data;
				try {
					// 发起实际请求
					data = actualDataFunctional.getActualData();
					log(String.format(	"\n ************* CacheData *************" +
										"\n ** ----------- 发起请求 ----------- **" +
										"\n ** 执行对象：%s" +
										"\n ** 方法签名：%s" +
										"\n ** 方法入参：%s" +
										"\n ** 返回数据：%s" +
										"\n *************************************",
							proxy,
							methodSignature,
							argsStr,
							data));
				} catch (Throwable throwable) {
					throwable.printStackTrace();
					String uuid = UUID.randomUUID().toString().trim().replaceAll("-", "");
					logger.info("\n ************* CacheData *************" +
								"\n ** -------- 获取数据发生异常 ------- **" +
								"\n ** 异常信息(UUID=" + uuid + ")：" + throwable.getMessage() + "\n" + printStackTrace(throwable.getStackTrace()) +
								"\n *************************************");

					if (methodcacheProperties.isEnableStatistics()) {
						recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, argsStr, argsHashCode, cacheHashCode,
								id, remark, hit, true, printStackTrace(throwable, uuid), startTime, new Date().getTime());
					}

					throw throwable;
				}

				if (methodcacheProperties.isEnableStatistics()) {
					recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, argsStr, argsHashCode, cacheHashCode,
							id, remark, hit, false, "", startTime, new Date().getTime());
				}

				if (isNotNull(data, nullable)) {
					long expirationTime = actualDataFunctional.getExpirationTime();
					refreshData(proxy, data, expirationTime, applicationName, actualDataFunctional, nullable, methodSignature, argsStr, cacheHashCode, id, remark);
				}
				return data;
			}
		}

		if (methodcacheProperties.isEnableStatistics()) {
			recordStatistics(cacheKey, methodSignature, methodSignatureHashCode, argsStr, argsHashCode,
					cacheHashCode, id, remark, hit, false, "", startTime, new Date().getTime());
		}

		if (refreshData) {
			refreshData(proxy, null, -1, applicationName, actualDataFunctional, nullable, methodSignature, argsStr, cacheHashCode, id, remark);
		}

		return cacheDataModel.getData();
	}

	@Override
	public Map<String, Map<String, Object>> getCaches(String match) {

		Map<String, Map<String, Object>> cacheMap = new HashMap<>();

		Set<Map<Integer, CacheDataModel>> dataModelMapSet = new HashSet<>(cacheData.values()); //缓存数据
		for (Map<Integer, CacheDataModel> dataModelMap : dataModelMapSet) { // <缓存哈希值,数据>
			if (dataModelMap.isEmpty()) {
				continue;
			}

			Set<CacheDataModel> dataModelSet;

			if (StringUtils.isEmpty(match)) {
				dataModelSet = new HashSet<>(dataModelMap.values());
			} else {
				// 模糊匹配，支持：缓存哈希值、方法签名、缓存ID

				dataModelSet = new HashSet<>();
				for (Integer cacheHashCode : new HashSet<>(dataModelMap.keySet())) {
					CacheDataModel dataModel = dataModelMap.get(cacheHashCode);
					String methodSignature = dataModel.getMethodSignature();
					String id = dataModel.getId();
					if (match.equals(String.valueOf(cacheHashCode)) || methodSignature.contains(match) || id.contains(match)) {
						if (!dataModel.isExpired()) {
							dataModelSet.add(dataModel);
						}
					}
				}
			}

			for (CacheDataModel dataModel : dataModelSet) {
				if (dataModel != null && !dataModel.isExpired()) {
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
				while (iterator.hasNext()) {
					Integer key = iterator.next();
					CacheDataModel dataModel = dataModelMap.get(key);
					if (dataModel == null || dataModel.isExpired()) {
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

			if (removeCacheHashCode.size() > 0) {
				Iterator<Map<String, Set<Integer>>> dataExpireInfoValuesIterator = dataExpireInfo.values().iterator(); // <方法签名,[缓存哈希值]>
				while (dataExpireInfoValuesIterator.hasNext()) {
					Map<String, Set<Integer>> dataExpireInfoValue = dataExpireInfoValuesIterator.next();
					Iterator<Set<Integer>> dataExpireInfoValueIterator = dataExpireInfoValue.values().iterator();
					while (dataExpireInfoValueIterator.hasNext()) {
						Set<Integer> cacheHashCodeInDataExpireInfo = dataExpireInfoValueIterator.next();
						cacheHashCodeInDataExpireInfo.removeAll(removeCacheHashCode);
						if (cacheHashCodeInDataExpireInfo.isEmpty()) {
							dataExpireInfoValueIterator.remove();
						}
					}

					if (dataExpireInfoValue.isEmpty()) {
						dataExpireInfoValuesIterator.remove();
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			cacheDataLock.unlock();
		}

		return delCacheMap;
	}

	@Override
	public Map<String, CacheStatisticsModel> getCacheStatistics() {
		return cacheStatistics;
	}

	@Override
	public CacheStatisticsModel getCacheStatistics(String methodSignature) {
		return cacheStatistics.get(methodSignature);
	}

	@Override
	public void setCacheStatistics(String methodSignature, CacheStatisticsModel statisticsModel) {
		cacheStatistics.put(methodSignature, statisticsModel);
	}

	@Override
	public void wipeStatistics(CacheStatisticsModel cacheStatisticsModel) {
		synchronized (cacheStatistics) {
			cacheStatistics.remove(cacheStatisticsModel.getMethodSignature());
		}
	}

	@Override
	public Map<String, CacheStatisticsModel> wipeStatisticsAll() {
		Map<String, CacheStatisticsModel> resultMap = new ConcurrentHashMap<>(cacheStatistics);
		synchronized (cacheStatistics) {
			cacheStatistics.clear();
		}
		return resultMap;
	}

	/****************************************************************** 私有方法 start ******************************************************************/

	/**
	 * 刷新数据
	 *
	 * @param proxy    	  			  执行对象
	 * @param data			    	  数据
	 * @param expirationTime    	  数据过期时间
	 * @param applicationName    	  应用名
	 * @param actualDataFunctional    真实数据请求
	 * @param nullable                返回值允许为空
	 * @param methodSignature         方法签名
	 * @param argsStr                 方法参数
	 * @param id                      缓存ID
	 * @param remark                  缓存备注
	 */
	private void refreshData(final Object proxy, final Object data, long expirationTime, String applicationName,
							 ActualDataFunctional actualDataFunctional, boolean nullable, String methodSignature,
							 String argsStr, int cacheHashCode, String id, String remark) {

		executorService.execute(() -> {

			Object saveData;
			long saveExpirationTime;

			if(data != null){
				saveData = data;
				saveExpirationTime = expirationTime;
			}else {
				saveData = new NullObject();
				saveExpirationTime = actualDataFunctional.getExpirationTime();
				try {
					saveData = actualDataFunctional.getActualData();
				} catch (Throwable throwable) {
					throwable.printStackTrace();
					String uuid = UUID.randomUUID().toString().trim().replaceAll("-", "");
					logger.info("\n ************* CacheData *************" +
							"\n ** ---- 异步更新数据至内存发生异常 --- **" +
							"\n 异常信息(UUID=" + uuid + ")：" + throwable.getMessage() + "\n" +
							printStackTrace(throwable.getStackTrace()) +
							"\n *************************************");
				}
			}



			if (isNotNull(saveData, nullable)) {
				try {
					cacheDataLock.lock();
					setDataToMemory(applicationName, methodSignature, argsStr, cacheHashCode,
							saveData != null ? saveData : new NullObject() , saveExpirationTime, id, remark);
					log(String.format(	"\n ************* CacheData *************" +
										"\n ** --------- 异步刷新缓存至内存 -------- **" +
										"\n ** 执行对象：%s" +
										"\n ** 方法签名：%s" +
										"\n ** 方法入参：%s" +
										"\n ** 缓存数据：%s" +
										"\n ** 过期时间：%s" +
										"\n *************************************",
								proxy,
								methodSignature,
								argsStr,
								saveData,
								formatDate(saveExpirationTime)));
				} finally {
					cacheDataLock.unlock();
				}
			}

		});
	}

	/**
	 * 从内存获取缓存数据
	 *
	 * @param methodSignature 方法签名
	 * @param cacheHashCode   缓存哈希值
	 * @return 缓存数据
	 */
	private static CacheDataModel getDataFromMemory(String methodSignature, Integer cacheHashCode) {

		Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature);
		if (cacheDataModelMap != null) {
			return cacheDataModelMap.get(cacheHashCode);
		}

		return null;
	}

	/**
	 * 保存缓存数据至内存
	 *
	 * @param applicationName 应用名
	 * @param methodSignature 方法签名
	 * @param args            入参
	 * @param cacheHashCode   缓存哈希值
	 * @param data            数据
	 * @param expireTime      过期时间
	 * @param id		      缓存ID
	 * @param remark		  缓存备注
	 */
	private void setDataToMemory(String applicationName, String methodSignature, String args, int cacheHashCode,
								 Object data, long expireTime, String id, String remark) {

		CacheDataModel cacheDataModel = new CacheDataModel(applicationName, methodSignature, args, cacheHashCode, data,
				expireTime);

		if (!StringUtils.isEmpty(id)) {
			cacheDataModel.setId(id);
		}

		if (!StringUtils.isEmpty(remark)) {
			cacheDataModel.setRemark(remark);
		}

		setDataToMemory(cacheDataModel);

	}

	/**
	 * 缓存数据至内存
	 *
	 * @param cacheDataModel 缓存数据
	 */
	private void setDataToMemory(CacheDataModel cacheDataModel) {

		String methodSignature = cacheDataModel.getMethodSignature();
		int cacheHashCode = cacheDataModel.getCacheHashCode();

		Map<Integer, CacheDataModel> cacheDataModelMap =
				cacheData.computeIfAbsent(methodSignature, k -> new HashMap<>());
		cacheDataModelMap.put(cacheHashCode, cacheDataModel);

		long expireTime = cacheDataModel.getExpireTime();

		if (expireTime > 0L) {
			// 记录缓存数据过期信息 <过期时间（时间戳，毫秒）,<方法签名,缓存哈希值>>
			Map<String, Set<Integer>> methodArgsHashCodeMap =
					dataExpireInfo.computeIfAbsent(expireTime, k -> new HashMap<>());
			methodArgsHashCodeMap.computeIfAbsent(methodSignature, k -> new HashSet<>()).add(cacheHashCode);
		}

		cacheDataSize.addAndGet(cacheDataModel.getInstanceSize());
		cacheDataCount.incrementAndGet();

	}

	/**
	 * 移除过期数据
	 *
	 * @param methodSignature 方法签名
	 * @param cacheHashCode   缓存哈希值
	 */
	private void doRemoveData(String methodSignature, Integer cacheHashCode) {
		try {
			CacheDataModel cacheDataModel = getDataFromMemory(methodSignature, cacheHashCode);
			if (cacheDataModel != null && cacheDataModel.isExpired()) {
				doRemoveData(cacheDataModel);
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("\n ************* CacheData *************" +
					"\n ** 移除数据出现异常：" + e.getMessage() + "\n" + printStackTrace(e.getStackTrace()) +
					"\n *************************************");
		}
	}

	/**
	 * 移除数据
	 *
	 * @param cacheDataModel 要删除的数据
	 */
	private void doRemoveData(CacheDataModel cacheDataModel) {
		String methodSignature = cacheDataModel.getMethodSignature();
		int cacheHashCode = cacheDataModel.getCacheHashCode();
		log(String.format(	"\n ************* CacheData *************" +
							"\n ** ------------ 移除缓存 ---------- **" +
							"\n ** 方法签名：%s" +
							"\n ** 方法入参：%s" +
							"\n *************************************",
				methodSignature,
				cacheDataModel.getArgs()));

		Map<Integer, CacheDataModel> cacheDataModelMap = cacheData.get(methodSignature); // <缓存哈希值,数据>
		cacheDataSize.addAndGet(-cacheDataModelMap.remove(cacheHashCode).getInstanceSize());
		cacheDataCount.decrementAndGet();
		if (cacheDataModelMap.isEmpty()) {
			cacheData.remove(methodSignature);
		}
	}

	/**
	 * 内存回收
	 *
	 * @param memUsage 内存使用信息
	 */
	private void gc(MemoryUsage memUsage) {
		if (memUsage == null) {
			memUsage = getMemoryOldGenUsage();

			if (memUsage == null) {
				logger.error("[methodcache]获取内存信息失败");
				return;
			}
		}

		try {
			cacheDataLock.lock();

			long used = memUsage.getUsed();
			long max = memUsage.getMax();
			long cacheDataSize = getCacheDataSize();

			if (!isAlarmed(cacheDataSize, used, gcThreshold)) {
				logger.info("[methodcache]缓存数据未达到GC阈值，本次不回收。数据大小：" + cacheDataSize + "；内存使用：" +
						used + "；GC阈值=" + gcThreshold);
				return;
			}

			doGC(cacheDataSize, used, max);

		} finally {
			cacheDataLock.unlock();
		}
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
	 * 断言回收数据的大小
	 *
	 * @param size 缓存数据大小
	 * @param used 内存使用大小
	 * @param max  内存最大上限
	 * @return 回收大小
	 */
	private long assertGCCapacity(long size, long used, long max) {

		BigDecimal biSize = new BigDecimal(size);
		BigDecimal biUsed = new BigDecimal(used);
		BigDecimal biMax = new BigDecimal(max);

		if (biSize.compareTo(biUsed.multiply(new BigDecimal(0.9))) >= 0 ||
				biSize.compareTo(biMax.multiply(new BigDecimal(0.4))) >= 0) {
			return biSize.multiply(new BigDecimal(0.3)).longValue();
		}

		return biSize.multiply(new BigDecimal(0.5)).longValue();
	}

	/**
	 * 是否触发警告
	 *
	 * @param used   已用内存
	 * @param limit  上限
	 * @param cordon 警戒线
	 */
	private boolean isAlarmed(long used, long limit, double cordon) {
		BigDecimal bigUsed = new BigDecimal(used);
		BigDecimal bigLimit = new BigDecimal(limit);
		return bigUsed.compareTo(bigLimit.multiply(new BigDecimal(cordon))) >= 0;
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
		logger.info("[methodcache]开始GC：数据条数=" + getCacheDataCount() + "，数据大小=" + size + "，计划回收=" +
				gcCapacity + "(" + (gcCapacity >> 10) + "K)");
		long startAt = new Date().getTime();
		AssertRemoveData removeData = removeData(gcCapacity);
		System.gc();
		logger.info("[methodcache]GC完成：数据条数=" + getCacheDataCount() + "，数据大小=" + getCacheDataSize() + "，" +
				"实际回收=" + removeData.getSize() + "(" + (removeData.getSize() >> 10) + "K)，回收条数=" +
				removeData.getCount() + "，" +
				"耗时=" + (new Date().getTime() - startAt));
	}

	/**
	 * 删除数据
	 *
	 * @param targetCapacity 预期回收大小
	 */
	private AssertRemoveData removeData(long targetCapacity) {

		AssertRemoveData removeDataModel = new AssertRemoveData();

		List<Long> expireTimeStampKeyList = new ArrayList<>(dataExpireInfo.keySet());
		if (expireTimeStampKeyList.size() <= 0) {
			// 没有可删除的信息
			return removeDataModel;
		}

		// 按时间顺序回收缓存数据
		long deleteInstanceSize = 0L;
		expireTimeStampKeyList.sort((l1, l2) -> (int) (l1 - l2));
		for (long eachExpireTimeStamp : expireTimeStampKeyList) {
			Map<String, Set<Integer>> dataExpireInfoMethodSignatureCacheHashCodeMap = dataExpireInfo.get(eachExpireTimeStamp);
			for (String dataExpireInfoMethodSignature : new HashSet<>(dataExpireInfoMethodSignatureCacheHashCodeMap.keySet())) {
				Map<Integer, CacheDataModel> cacheDataCacheHashCodeModelMap = cacheData.get(dataExpireInfoMethodSignature);
				Set<Integer> dataExpireInfoCacheHashCodeSet = dataExpireInfoMethodSignatureCacheHashCodeMap.get(dataExpireInfoMethodSignature);
				Iterator<Integer> dataExpireInfoCacheHashCodeIterator = dataExpireInfoCacheHashCodeSet.iterator();
				while (dataExpireInfoCacheHashCodeIterator.hasNext()) {
					Integer dataExpireInfoCacheHashCode = dataExpireInfoCacheHashCodeIterator.next();
					CacheDataModel cacheDataModel = cacheDataCacheHashCodeModelMap.remove(dataExpireInfoCacheHashCode);

					long instanceSize = cacheDataModel.getInstanceSize();
					cacheDataSize.addAndGet(-instanceSize);
					cacheDataCount.decrementAndGet();
					dataExpireInfoCacheHashCodeIterator.remove();

					removeDataModel.addCount(1);
					if (removeDataModel.addSize(instanceSize) >= targetCapacity) { // 累加实例大小
						break;
					}
				}
				if (dataExpireInfoCacheHashCodeSet.isEmpty()) {
					dataExpireInfoMethodSignatureCacheHashCodeMap.remove(dataExpireInfoMethodSignature);
				}

				if (deleteInstanceSize >= targetCapacity) {
					break;
				}
			}
			if (dataExpireInfoMethodSignatureCacheHashCodeMap.isEmpty()) {
				dataExpireInfo.remove(eachExpireTimeStamp);
			}
			if (deleteInstanceSize >= targetCapacity) {
				break;
			}
		}
		return removeDataModel;
	}

	/**
	 * 获取缓存数据大小
	 *
	 * @return 数据大小，单位byte
	 */
	private long getCacheDataSize() {
		return cacheDataSize.get();
	}

	/**
	 * 获取缓存数据条数
	 *
	 * @return 数据条数
	 */
	private int getCacheDataCount() {
		return cacheDataCount.get();
	}

	/**
	 * 断言需删除的数据
	 */
	private class AssertRemoveData {
		/**
		 * 数据大小
		 */
		private AtomicLong size;

		/**
		 * 数据个数
		 */
		private AtomicInteger count;

		AssertRemoveData() {
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

	/**
	 * 打印日志
	 *
	 * @param info 内容
	 */
	private void log(String info) {
		if (methodcacheProperties.isEnableLog()) {
			logger.info(info);
		}
	}
	/****************************************************************** 私有方法  end  ******************************************************************/
}
