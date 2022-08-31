package love.kill.methodcache.datahelper;

import love.kill.methodcache.datahelper.impl.RedisDataHelper;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * 数据缓存
 *
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public interface DataHelper {

	SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * 缓存key
	 * */
	String METHOD_CACHE_DATA = "METHOD_CACHE_DATA";

	/**
	 * 缓存统计key
	 * */
	String METHOD_CACHE_STATISTICS = "METHOD_CACHE_STATISTICS";

	/**
	 * 签名和入参的分隔符
	 * */
	String KEY_SEPARATION_CHARACTER = "@";

	/**
	 * cpu个数
	 */
	int CPU_COUNT = Runtime.getRuntime().availableProcessors();

	/**
	 * 执行线程
	 * */
	ExecutorService recordExecutorService = new ThreadPoolExecutor(
			CPU_COUNT + 1, // 核心线程数（CPU核心数 + 1）
			CPU_COUNT * 2 + 1, // 线程池最大线程数（CPU核心数 * 2 + 1）
			1,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(),
			Executors.defaultThreadFactory(),
			new ThreadPoolExecutor.AbortPolicy());

	/**
	 * 获取数据
	 *
	 * @param method 方法
	 * @param args 请求参数
	 * @param refreshData 刷新数据
	 * @param actualDataFunctional 请求模型
	 * @param id 缓存ID
	 * @param remark 缓存备注
	 * @param nullable 缓存null
	 * @return 数据
	 * @throws Exception 获取数据时发生异常
	 * */
	Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark, boolean nullable) throws Exception;


	/**
	 * 请求模型
	 * */
	interface ActualDataFunctional{
		/**
		 * 发起一次真实请求，缓存并返回该数据
		 *
		 * @return 请求数据
		 * @throws Throwable 发起实际请求时发生的异常
		 * */
		Object getActualData() throws Throwable;

		/**
		 * 数据过期时间，时间戳
		 *
		 * @return 过期时间
		 * */
		long getExpirationTime();
	}

	/**
	 * 格式化时间
	 *
	 * @param timeStamp 时间戳
	 * @return 格式化后的时间
	 * */
	default String formatDate(long timeStamp){
		try {
			return formatDate.format(new Date(timeStamp));
		}catch (Exception e){
			e.printStackTrace();
			return String.valueOf(timeStamp);
		}
	}

	/**
	 * 获取缓存数据
	 *
	 * @param match 匹配规则
	 * @return key
	 * */
	Map<String, Map<String,Object>> getCaches(String match);


	/**
	 * 清空数据
	 *
	 * @param id 缓存ID
	 * @param cacheHashCode 缓存哈希值
	 * @return 删除的缓存
	 * */
	Map<String, Map<String,Object>> wipeCache(String id, String cacheHashCode);


	/**
	 * 获取缓存情况
	 *
	 * @param match 匹配规则
	 * @return 缓存信息
	 * */
	default Map<String, CacheStatisticsModel> getStatistics(String match) {

		Map<String, CacheStatisticsModel> cacheStatistics = getCacheStatistics();
		if(cacheStatistics == null){
			return null;
		}

		Map<String, CacheStatisticsModel> resultMap = new HashMap<>();

		for (String methodSignature : cacheStatistics.keySet()) {
			CacheStatisticsModel situationModel = cacheStatistics.get(methodSignature);
			String id = situationModel.getId();
			if (StringUtils.isEmpty(match) ||
					methodSignature.contains(match) ||
					!StringUtils.isEmpty(id) && id.equals(match)
			) {
				resultMap.put(methodSignature, situationModel);
			}
		}
		return resultMap;
	}

	/**
	 * 获取缓存统计
	 *
	 * @return 缓存统计信息
	 * */
	Map<String, CacheStatisticsModel> getCacheStatistics();

	/**
	 * 获取缓存统计
	 *
	 * @param methodSignature 方法签名
	 * @return 缓存统计信息
	 * */
	CacheStatisticsModel getCacheStatistics(String methodSignature);

	/**
	 * 保存缓存统计信息
	 *
	 * @param methodSignature 方法签名
	 * @param cacheStatisticsModel 统计信息
	 * */
	void setCacheStatistics(String methodSignature, CacheStatisticsModel cacheStatisticsModel);

	/**
	 * 缓存统计信息队列
	 * */
	ArrayBlockingQueue<CacheStatisticsNode> cacheStatisticsInfoQueue = new ArrayBlockingQueue<>(10);


	/**
	 * 增加统计信息
	 * */
	default CacheStatisticsModel increaseStatistics(CacheStatisticsModel cacheStatisticsModel, CacheStatisticsNode cacheStatisticsNode){
		if (cacheStatisticsModel == null) {
			cacheStatisticsModel = new CacheStatisticsModel(cacheStatisticsNode.getMethodSignature(), cacheStatisticsNode.getMethodSignatureHashCode(),
					cacheStatisticsNode.getId(), cacheStatisticsNode.getRemark());
		}

		boolean hit = cacheStatisticsNode.isHit(); // 命中
		long startTimestamp = cacheStatisticsNode.getStartTimestamp(); // 请求开始时间戳
		long endTimestamp = cacheStatisticsNode.getEndTimestamp(); // 请求结束时间戳
		long spend = endTimestamp - startTimestamp; // 请求耗时

		if (hit) {
			// 命中
			cacheStatisticsModel.incrementHit();
			cacheStatisticsModel.calculateAvgOfHitSpend(spend);
			cacheStatisticsModel.setMinHitSpend(spend, startTimestamp);
			cacheStatisticsModel.setMaxHitSpend(spend, startTimestamp);
		} else {
			// 未命中
			cacheStatisticsModel.incrementFailure();
			cacheStatisticsModel.calculateAvgOfFailureSpend(spend);
			cacheStatisticsModel.setMinFailureSpend(spend, startTimestamp);
			cacheStatisticsModel.setMaxFailureSpend(spend, startTimestamp);
		}

		return cacheStatisticsModel;
	}

	/**
	 * 缓存统计
	 *
	 * @param cacheKey 缓存key
	 * @param methodSignature 方法签名
	 * @param methodSignatureHashCode 方法签名哈希
	 * @param args 入参
	 * @param argsHashCode 入参哈希
	 * @param cacheHashCode 缓存哈希
	 * @param id 缓存ID
	 * @param remark 缓存备注
	 * @param hit 命中
	 * @param startTimestamp 记录开始时间
	 * */
	default void record(String cacheKey, String methodSignature, int methodSignatureHashCode, String args, int argsHashCode,
						int cacheHashCode, String id, String remark, boolean hit, long startTimestamp) {
		recordExecutorService.execute(() -> {
			try {
				cacheStatisticsInfoQueue.put(new CacheStatisticsNode(cacheKey, methodSignature, methodSignatureHashCode, args, argsHashCode, cacheHashCode, id, remark, hit, startTimestamp, new Date().getTime()));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * 获取缓存key
	 * @param applicationName 应用名
	 * @param methodSignature 方法签名
	 * @param cacheHashCode 缓存签名
	 * @param id 缓存ID
	 * @return 缓存key
	 * */
	default String getCacheKey(String applicationName, String methodSignature, int cacheHashCode, String id) {
		if(StringUtils.isEmpty(applicationName)){
			return methodSignature + KEY_SEPARATION_CHARACTER + cacheHashCode + KEY_SEPARATION_CHARACTER + id;
		}else {
			return applicationName + KEY_SEPARATION_CHARACTER + methodSignature + KEY_SEPARATION_CHARACTER + cacheHashCode + KEY_SEPARATION_CHARACTER + id;
		}
	}

	/**
	 * 获取缓存情况
	 *
	 * */
//	default CacheSituationModel getStatistics(CacheSituationNode situationNode, CacheStatisticsModel statisticsModel) {
//		if (situationModel == null) {
//			situationModel = new CacheSituationModel(situationNode.getMethodSignature(), situationNode.getMethodSignatureHashCode(), situationNode.getArgs(),
//					situationNode.getArgsHashCode(), situationNode.getCacheHashCode(), situationNode.getId(), situationNode.getRemark());
//		}
//
//		int hit = situationModel.getHit();
//		long hitSpend = situationModel.getHitSpend();
//		long minHitSpend = situationModel.getMinHitSpend();
//		long maxHitSpend = situationModel.getMaxHitSpend();
//
//		int failure = situationModel.getFailure();
//		long failureSpend = situationModel.getFailureSpend();
//		long minFailureSpend = situationModel.getMinFailureSpend();
//		long maxFailureSpend = situationModel.getMaxFailureSpend();
//
//		long spend = situationNode.getEndTimestamp() - situationNode.getStartTimestamp(); // 请求耗时
//
//		if (situationNode.isHit()) {
//			situationModel.setHit(hit + 1);
//			situationModel.setHitSpend(hitSpend + spend);
//
//			if (minHitSpend == 0L || minHitSpend > spend) {
//				situationModel.setMinHitSpend(spend);
//				situationModel.setTimeOfMinHitSpend(situationNode.getStartTimestamp());
//			}
//
//			if (maxHitSpend < spend) {
//				situationModel.setMaxHitSpend(spend);
//				situationModel.setTimeOfMaxHitSpend(situationNode.getStartTimestamp());
//			}
//
//		} else {
//			situationModel.setFailure(failure + 1);
//			situationModel.setFailureSpend(failureSpend + spend);
//
//			if (minFailureSpend == 0L || minFailureSpend > spend) {
//				situationModel.setMinFailureSpend(spend);
//				situationModel.setTimeOfMinFailureSpend(situationNode.getStartTimestamp());
//			}
//
//			if (maxFailureSpend < spend) {
//				situationModel.setMaxFailureSpend(spend);
//				situationModel.setTimeOfMaxFailureSpend(situationNode.getStartTimestamp());
//			}
//		}
//
//		return situationModel;
//	}
	/**
	default CacheSituationModel getStatistics(CacheSituationNode situationNode, CacheSituationModel situationModel) {
		if (situationModel == null) {
			situationModel = new CacheSituationModel(situationNode.getMethodSignature(), situationNode.getMethodSignatureHashCode(), situationNode.getArgs(),
					situationNode.getArgsHashCode(), situationNode.getCacheHashCode(), situationNode.getId(), situationNode.getRemark());
		}

		int hit = situationModel.getHit();
		long hitSpend = situationModel.getHitSpend();
		long minHitSpend = situationModel.getMinHitSpend();
		long maxHitSpend = situationModel.getMaxHitSpend();

		int failure = situationModel.getFailure();
		long failureSpend = situationModel.getFailureSpend();
		long minFailureSpend = situationModel.getMinFailureSpend();
		long maxFailureSpend = situationModel.getMaxFailureSpend();

		long spend = situationNode.getEndTimestamp() - situationNode.getStartTimestamp(); // 请求耗时

		if (situationNode.isHit()) {
			situationModel.setHit(hit + 1);
			situationModel.setHitSpend(hitSpend + spend);

			if (minHitSpend == 0L || minHitSpend > spend) {
				situationModel.setMinHitSpend(spend);
				situationModel.setTimeOfMinHitSpend(situationNode.getStartTimestamp());
			}

			if (maxHitSpend < spend) {
				situationModel.setMaxHitSpend(spend);
				situationModel.setTimeOfMaxHitSpend(situationNode.getStartTimestamp());
			}

		} else {
			situationModel.setFailure(failure + 1);
			situationModel.setFailureSpend(failureSpend + spend);

			if (minFailureSpend == 0L || minFailureSpend > spend) {
				situationModel.setMinFailureSpend(spend);
				situationModel.setTimeOfMinFailureSpend(situationNode.getStartTimestamp());
			}

			if (maxFailureSpend < spend) {
				situationModel.setMaxFailureSpend(spend);
				situationModel.setTimeOfMaxFailureSpend(situationNode.getStartTimestamp());
			}
		}

		return situationModel;
	}
	 */

	/**
	 * 筛选符合的缓存数据
	 *
	 * @param cacheMap 缓存数据
	 * @param cacheDataModel 待筛选的节点
	 * @param select 过滤值
	 * */
	@SuppressWarnings("unchecked")
	default void filterDataModel(Map<String, Map<String, Object>> cacheMap, CacheDataModel cacheDataModel, String select) {
		if(!StringUtils.isEmpty(select)){
			String args = cacheDataModel.getArgs();
			if(!StringUtils.isEmpty(args) && !args.contains(select)){
				return;
			}
		}

		Map<String,Object> keyMap = cacheMap.computeIfAbsent(cacheDataModel.getMethodSignature(), k -> {
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

//	/**
//	 * 筛选符合的缓存情况
//	 *
//	 * @param cacheMap 缓存数据
//	 * @param cacheSituationModel 待筛选的节点
//	 * @param match 匹配条件
//	 * */
//	@SuppressWarnings("unchecked")
//	default void filterSituationModel(Map<String, Map<String, Object>> cacheMap, CacheSituationModel cacheSituationModel, String match) {
//
//		if (!StringUtils.isEmpty(match)) {
//			int cacheHashCode = cacheSituationModel.getCacheHashCode();
//			String methodSignature = cacheSituationModel.getMethodSignature();
//			String id = cacheSituationModel.getId();
//			if (!match.equals(String.valueOf(cacheHashCode)) && !methodSignature.contains(match) && !match.equals(id)) {
//				return;
//			}
//		}
//
//		Map<String, Object> keyMap = cacheMap.computeIfAbsent(cacheSituationModel.getMethodSignature(), k -> {
//			Map<String, Object> map = new HashMap<>();
//			map.put("id", cacheSituationModel.getId());
//			map.put("remark", cacheSituationModel.getRemark());
//			map.put("hit", 0);
//			map.put("hitSpend", 0L);
//			map.put("avgOfHitSpend", 0D);
//			map.put("failure", 0);
//			map.put("failureSpend", 0L);
//			map.put("avgOfFailureSpend", 0D);
//			return map;
//		});
//
//		List<Map<String, Object>> returnList = (List<Map<String, Object>>) keyMap.computeIfAbsent("situation", k -> new ArrayList<>());
//
//		Map<String, Object> cacheInfo = new HashMap<>();
//		cacheInfo.put("hashCode", cacheSituationModel.getCacheHashCode());
//		cacheInfo.put("args", cacheSituationModel.getArgs());
//
//		int hit = cacheSituationModel.getHit();
//		long hitSpend = cacheSituationModel.getHitSpend();
//		long minHitSpend = cacheSituationModel.getMinHitSpend();
//		String timeOfMinHitSpend = formatDate(cacheSituationModel.getTimeOfMinHitSpend());
//		long maxHitSpend = cacheSituationModel.getMaxHitSpend();
//		String timeOfMaxHitSpend = formatDate(cacheSituationModel.getTimeOfMaxHitSpend());
//		double avgOfHitSpend = (hit > 0 && hitSpend > 0L) ?
//				new BigDecimal(hitSpend).divide(new BigDecimal(hit), 4, BigDecimal.ROUND_HALF_UP).doubleValue() :
//				0d;
//
//		int failure = cacheSituationModel.getFailure();
//		long failureSpend = cacheSituationModel.getFailureSpend();
//		long minFailureSpend = cacheSituationModel.getMinFailureSpend();
//		String timeOfMinFailureSpend = formatDate(cacheSituationModel.getTimeOfMinFailureSpend());
//		long maxFailureSpend = cacheSituationModel.getMaxFailureSpend();
//		String timeOfMaxFailureSpend = formatDate(cacheSituationModel.getTimeOfMaxFailureSpend());
//		double avgOfFailureSpend = (failure > 0 && failureSpend > 0L) ?
//				new BigDecimal(failureSpend).divide(new BigDecimal(failure), 4, BigDecimal.ROUND_HALF_UP).doubleValue() :
//				0d;
//
//
//		cacheInfo.put("hit", hit);
//		cacheInfo.put("hitSpend", hitSpend);
//		cacheInfo.put("avgOfHitSpend", avgOfHitSpend);
//		cacheInfo.put("minHitSpend", minHitSpend);
//		cacheInfo.put("timeOfMinHitSpend", timeOfMinHitSpend);
//		cacheInfo.put("maxHitSpend", maxHitSpend);
//		cacheInfo.put("timeOfMaxHitSpend", timeOfMaxHitSpend);
//		cacheInfo.put("failure", failure);
//		cacheInfo.put("failureSpend", failureSpend);
//		cacheInfo.put("avgOfFailureSpend", avgOfFailureSpend);
//		cacheInfo.put("minFailureSpend", minFailureSpend);
//		cacheInfo.put("timeOfMinFailureSpend", timeOfMinFailureSpend);
//		cacheInfo.put("maxFailureSpend", maxFailureSpend);
//		cacheInfo.put("timeOfMaxFailureSpend", timeOfMaxFailureSpend);
//		returnList.add(cacheInfo);
//
//
//		int totalHit = getTotalHit(keyMap) + hit;
//		long totalHitSpend = getTotalHitSpend(keyMap) + hitSpend;
//		long totalMinHitSpend = getMinHitSpend(keyMap);
//		String totalTimeOfMinHitSpend = getTimeOfMinHitSpend(keyMap);
//		if (totalMinHitSpend == 0L || totalMinHitSpend > minHitSpend) {
//			totalMinHitSpend = minHitSpend;
//			totalTimeOfMinHitSpend = timeOfMinHitSpend;
//		}
//		long totalMaxHitSpend = getMaxHitSpend(keyMap);
//		String totalTimeOfMaxHitSpend = getTimeOfMaxHitSpend(keyMap);
//		if (totalMaxHitSpend < maxHitSpend) {
//			totalMaxHitSpend = maxHitSpend;
//			totalTimeOfMaxHitSpend = timeOfMaxHitSpend;
//		}
//		double totalAvgOfHitSpend = (totalHit > 0 && totalHitSpend > 0L) ?
//				new BigDecimal(totalHitSpend).divide(new BigDecimal(totalHit), 4, BigDecimal.ROUND_HALF_UP).doubleValue() :
//				0d;
//
//		int totalFailure = getTotalFailure(keyMap) + failure;
//		long totalFailureSpend = getTotalFailureSpend(keyMap) + failureSpend;
//		long totalMinFailureSpend = getMinFailureSpend(keyMap);
//		String totalTimeOfMinFailureSpend = getTimeOfMinFailureSpend(keyMap);
//		if (totalMinFailureSpend == 0L || getMinFailureSpend(keyMap) > minFailureSpend) {
//			totalMinFailureSpend = minFailureSpend;
//			totalTimeOfMinFailureSpend = timeOfMinFailureSpend;
//		}
//		long totalMaxFailureSpend = getMaxFailureSpend(keyMap);
//		String totalTimeOfMaxFailureSpend = getTimeOfMaxFailureSpend(keyMap);
//		if (totalMaxFailureSpend < maxFailureSpend) {
//			totalMaxFailureSpend = maxFailureSpend;
//			totalTimeOfMaxFailureSpend = timeOfMaxFailureSpend;
//		}
//		double totalAvgOfFailureSpend = (totalFailure > 0 && totalFailureSpend > 0L) ?
//				new BigDecimal(totalFailureSpend).divide(new BigDecimal(totalFailure), 4, BigDecimal.ROUND_HALF_UP).doubleValue() :
//				0d;
//
//		keyMap.put("hit", totalHit);
//		keyMap.put("hitSpend", totalHitSpend);
//		keyMap.put("avgOfHitSpend", totalAvgOfHitSpend);
//		keyMap.put("minHitSpend", totalMinHitSpend);
//		keyMap.put("timeOfMinHitSpend", totalTimeOfMinHitSpend);
//		keyMap.put("maxHitSpend", totalMaxHitSpend);
//		keyMap.put("timeOfMaxHitSpend", totalTimeOfMaxHitSpend);
//		keyMap.put("failure", totalFailure);
//		keyMap.put("failureSpend", totalFailureSpend);
//		keyMap.put("avgOfFailureSpend", totalAvgOfFailureSpend);
//		keyMap.put("minFailureSpend", totalMinFailureSpend);
//		keyMap.put("timeOfMinFailureSpend", totalTimeOfMinFailureSpend);
//		keyMap.put("maxFailureSpend", totalMaxFailureSpend);
//		keyMap.put("timeOfMaxFailureSpend", totalTimeOfMaxFailureSpend);
//
//	}


//	default long getTotalFailureSpend(Map<String, Object> keyMap){
//		return keyMap.get("failureSpend") instanceof Long ? (long) keyMap.get("failureSpend") : 0;
//	}
//
//	default int getTotalHit(Map<String, Object> keyMap){
//		return keyMap.get("hit") instanceof Integer ? (int) keyMap.get("hit") : 0;
//	}
//
//	default long getTotalHitSpend(Map<String, Object> keyMap){
//		return keyMap.get("hitSpend") instanceof Long ? (long) keyMap.get("hitSpend") : 0L;
//	}
//
//	default long getMinHitSpend(Map<String, Object> keyMap){
//		return keyMap.get("minHitSpend") instanceof Long ? (long) keyMap.get("minHitSpend") : 0L;
//	}
//
//	default String getTimeOfMinHitSpend(Map<String, Object> keyMap){
//		return keyMap.get("timeOfMinHitSpend") instanceof String ? (String) keyMap.get("timeOfMinHitSpend") : "";
//	}
//
//	default long getMaxHitSpend(Map<String, Object> keyMap){
//		return keyMap.get("maxHitSpend") instanceof Long ? (long) keyMap.get("maxHitSpend") : 0L;
//	}
//
//	default String getTimeOfMaxHitSpend(Map<String, Object> keyMap){
//		return keyMap.get("timeOfMaxHitSpend") instanceof String ? (String) keyMap.get("timeOfMaxHitSpend") : "";
//	}
//
//	default int getTotalFailure(Map<String, Object> keyMap){
//		return  keyMap.get("failure") instanceof Integer ? (int) keyMap.get("failure") : 0;
//	}
//
//	default long getMinFailureSpend(Map<String, Object> keyMap){
//		return keyMap.get("minFailureSpend") instanceof Long ? (long) keyMap.get("minFailureSpend") : 0L;
//	}
//
//	default String getTimeOfMinFailureSpend(Map<String, Object> keyMap){
//		return keyMap.get("timeOfMinFailureSpend") instanceof String ? (String) keyMap.get("timeOfMinFailureSpend") : "";
//	}
//
//	default long getMaxFailureSpend(Map<String, Object> keyMap){
//		return keyMap.get("maxFailureSpend") instanceof Long ? (long) keyMap.get("maxFailureSpend") : 0L;
//	}
//
//	default String getTimeOfMaxFailureSpend(Map<String, Object> keyMap){
//		return keyMap.get("timeOfMaxFailureSpend") instanceof String ? (String) keyMap.get("timeOfMaxFailureSpend") : "";
//	}

	/**
	 * 缓存统计信息节点
	 * */
	class CacheStatisticsNode {
		/**
		 * 缓存key
		 * 由：applicationName、methodSignature、cacheHashCode、id组成
		 * */
		private String cacheKey;

		/**
		 * 方法签名
		 * */
		private String methodSignature;

		/**
		 * 方法签名哈希值
		 * */
		private int methodSignatureHashCode;

		/**
		 * 请求入参
		 * */
		private String args;

		/**
		 * 请求入参哈希值
		 * */
		private int argsHashCode;

		/**
		 * 缓存哈希值
		 * */
		private int cacheHashCode;

		/**
		 * 缓存ID
		 * */
		private String id;

		/**
		 * 缓存备注
		 * */
		private String remark;

		/**
		 * 缓存命中
		 * */
		private boolean hit;

		/**
		 * 请求开始时间
		 * */
		private long startTimestamp;

		/**
		 * 请求结束时间
		 * */
		private long endTimestamp;

		public CacheStatisticsNode(String cacheKey, String methodSignature, int methodSignatureHashCode, String args, int argsHashCode, int cacheHashCode, String id, String remark, boolean hit, long startTimestamp, long endTimestamp) {
			this.cacheKey = cacheKey;
			this.methodSignature = methodSignature;
			this.methodSignatureHashCode = methodSignatureHashCode;
			this.args = args;
			this.argsHashCode = argsHashCode;
			this.cacheHashCode = cacheHashCode;
			this.id = id;
			this.remark = remark;
			this.hit = hit;
			this.startTimestamp = startTimestamp;
			this.endTimestamp = endTimestamp;
		}

		public String getCacheKey() {
			return cacheKey;
		}

		public void setCacheKey(String cacheKey) {
			this.cacheKey = cacheKey;
		}

		public String getMethodSignature() {
			return methodSignature;
		}

		public void setMethodSignature(String methodSignature) {
			this.methodSignature = methodSignature;
		}

		public int getMethodSignatureHashCode() {
			return methodSignatureHashCode;
		}

		public void setMethodSignatureHashCode(int methodSignatureHashCode) {
			this.methodSignatureHashCode = methodSignatureHashCode;
		}

		public String getArgs() {
			return args;
		}

		public void setArgs(String args) {
			this.args = args;
		}

		public int getArgsHashCode() {
			return argsHashCode;
		}

		public void setArgsHashCode(int argsHashCode) {
			this.argsHashCode = argsHashCode;
		}

		public int getCacheHashCode() {
			return cacheHashCode;
		}

		public void setCacheHashCode(int cacheHashCode) {
			this.cacheHashCode = cacheHashCode;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getRemark() {
			return remark;
		}

		public void setRemark(String remark) {
			this.remark = remark;
		}

		public boolean isHit() {
			return hit;
		}

		public void setHit(boolean hit) {
			this.hit = hit;
		}

		public long getStartTimestamp() {
			return startTimestamp;
		}

		public void setStartTimestamp(long startTimestamp) {
			this.startTimestamp = startTimestamp;
		}

		public long getEndTimestamp() {
			return endTimestamp;
		}

		public void setEndTimestamp(long endTimestamp) {
			this.endTimestamp = endTimestamp;
		}
	}

}
