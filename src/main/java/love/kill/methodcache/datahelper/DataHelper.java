package love.kill.methodcache.datahelper;

import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * 数据缓存
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public interface DataHelper {

	SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * 缓存key
	 */
	String METHOD_CACHE_DATA = "METHOD_CACHE_DATA";

	/**
	 * 缓存统计key
	 */
	String METHOD_CACHE_STATISTICS = "METHOD_CACHE_STATISTICS";

	/**
	 * 签名和入参的分隔符
	 */
	String KEY_SEPARATION_CHARACTER = "@";

	/**
	 * cpu个数
	 */
	int CPU_COUNT = Runtime.getRuntime().availableProcessors();

	/**
	 * 执行线程
	 */
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
	 * @param method               方法
	 * @param args                 请求参数
	 * @param refreshData          刷新数据
	 * @param actualDataFunctional 请求模型
	 * @param id                   缓存ID
	 * @param remark               缓存备注
	 * @param nullable             缓存null
	 * @return 数据
	 * @throws Exception 获取数据时发生异常
	 */
	Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark, boolean nullable) throws Throwable;

	/**
	 * 请求模型
	 */
	interface ActualDataFunctional {
		/**
		 * 发起一次真实请求，缓存并返回该数据
		 *
		 * @return 请求数据
		 * @throws Throwable 发起实际请求时发生的异常
		 */
		Object getActualData() throws Throwable;

		/**
		 * 数据过期时间，时间戳
		 *
		 * @return 过期时间
		 */
		long getExpirationTime();
	}

	/**
	 * 获取缓存数据
	 *
	 * @param match 匹配规则
	 * @return key
	 */
	Map<String, Map<String, Object>> getCaches(String match);

	/**
	 * 清空数据
	 *
	 * @param id            缓存ID
	 * @param cacheHashCode 缓存哈希值
	 * @return 删除的缓存
	 */
	Map<String, Map<String, Object>> wipeCache(String id, String cacheHashCode);

	/**
	 * 获取缓存统计
	 *
	 * @return 缓存统计信息
	 */
	Map<String, CacheStatisticsModel> getCacheStatistics();

	/**
	 * 获取缓存统计
	 *
	 * @param methodSignature 方法签名
	 * @return 缓存统计信息
	 */
	CacheStatisticsModel getCacheStatistics(String methodSignature);

	/**
	 * 保存缓存统计信息
	 *
	 * @param methodSignature      方法签名
	 * @param cacheStatisticsModel 统计信息
	 */
	void setCacheStatistics(String methodSignature, CacheStatisticsModel cacheStatisticsModel);

	/**
	 * 缓存统计信息队列
	 */
	BlockingQueue<CacheStatisticsNode> cacheStatisticsInfoQueue = new LinkedBlockingQueue<>();

	/**
	 * 清空缓存统计
	 *
	 * @param statisticsModel 缓存信息
	 */
	void wipeStatistics(CacheStatisticsModel statisticsModel);

	/**
	 * 清空所有缓存统计
	 *
	 * @return 删除的缓存
	 */
	Map<String, CacheStatisticsModel> wipeStatisticsAll();

	/**
	 * 获取缓存key
	 *
	 * @param applicationName 应用名
	 * @param methodSignature 方法签名
	 * @param cacheHashCode   缓存签名
	 * @param id              缓存ID
	 * @return 缓存key
	 */
	default String getCacheKey(String applicationName, String methodSignature, int cacheHashCode, String id) {
		if (StringUtils.isEmpty(applicationName)) {
			return methodSignature + KEY_SEPARATION_CHARACTER + cacheHashCode + KEY_SEPARATION_CHARACTER + id;
		} else {
			return applicationName + KEY_SEPARATION_CHARACTER + methodSignature + KEY_SEPARATION_CHARACTER + cacheHashCode + KEY_SEPARATION_CHARACTER + id;
		}
	}

	/**
	 * 筛选符合的缓存数据
	 *
	 * @param cacheMap       缓存数据
	 * @param cacheDataModel 待筛选的节点
	 * @param select         过滤值
	 */
	@SuppressWarnings("unchecked")
	default void filterDataModel(Map<String, Map<String, Object>> cacheMap, CacheDataModel cacheDataModel, String select) {
		if (!StringUtils.isEmpty(select)) {
			String args = cacheDataModel.getArgs();
			if (!StringUtils.isEmpty(args) && !args.contains(select)) {
				return;
			}
		}

		Map<String, Object> keyMap = cacheMap.computeIfAbsent(cacheDataModel.getMethodSignature(), k -> {
			Map<String, Object> map = new HashMap<>();
			map.put("id", cacheDataModel.getId());
			map.put("remark", cacheDataModel.getRemark());
			return map;
		});

		List<Map<String, Object>> cacheInfoList = (List<Map<String, Object>>) keyMap.computeIfAbsent("cache", k -> new ArrayList<>());

		Map<String, Object> cacheInfo = new HashMap<>();
		cacheInfo.put("hashCode", cacheDataModel.getCacheHashCode());
		cacheInfo.put("args", cacheDataModel.getArgs());
		cacheInfo.put("data", Objects.toString(cacheDataModel.getData()));
		cacheInfo.put("cacheTime", cacheDataModel.getFormatCacheTime());
		cacheInfo.put("expireTime", cacheDataModel.getFormatExpireTime());
		cacheInfoList.add(cacheInfo);

	}

	/**
	 * 获取缓存统计
	 *
	 * @param match 匹配规则
	 * @return 缓存信息
	 */
	default Map<String, CacheStatisticsModel> getStatistics(String match) {

		Map<String, CacheStatisticsModel> cacheStatistics = getCacheStatistics();
		if (cacheStatistics == null) {
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
	 * 增加统计信息
	 *
	 * @param cacheStatisticsModel 缓存统计信息
	 * @param cacheStatisticsNode  缓存节点信息
	 * @return 缓存统计信息
	 */
	default CacheStatisticsModel increaseStatistics(CacheStatisticsModel cacheStatisticsModel, CacheStatisticsNode cacheStatisticsNode) {
		if (cacheStatisticsModel == null) {
			cacheStatisticsModel = new CacheStatisticsModel(cacheStatisticsNode.getCacheKey(), cacheStatisticsNode.getMethodSignature(),
					cacheStatisticsNode.getMethodSignatureHashCode(), cacheStatisticsNode.getId(), cacheStatisticsNode.getRemark());
		}

		boolean hit = cacheStatisticsNode.isHit(); // 命中
		boolean invokeException = cacheStatisticsNode.isInvokeException(); // 请求异常
		String stackTraceOfException = cacheStatisticsNode.getStackTraceOfException(); // 异常栈
		long startTimestamp = cacheStatisticsNode.getStartTimestamp(); // 请求开始时间戳
		long endTimestamp = cacheStatisticsNode.getEndTimestamp(); // 请求结束时间戳
		long spend = endTimestamp - startTimestamp; // 请求耗时
		String args = cacheStatisticsNode.getArgs(); // 请求入参

		if (invokeException) {
			// 异常
			cacheStatisticsModel.incrementTimesOfException(args, stackTraceOfException, startTimestamp);

		} else if (hit) {
			// 命中
			cacheStatisticsModel.incrementHit(spend);
			cacheStatisticsModel.setMinHitSpend(spend, startTimestamp, args);
			cacheStatisticsModel.setMaxHitSpend(spend, startTimestamp, args);
		} else {
			// 未命中
			cacheStatisticsModel.incrementFailure(spend);
			cacheStatisticsModel.setMinFailureSpend(spend, startTimestamp, args);
			cacheStatisticsModel.setMaxFailureSpend(spend, startTimestamp, args);
		}

		return cacheStatisticsModel;
	}

	/**
	 * 缓存统计
	 *
	 * @param cacheKey                缓存key
	 * @param methodSignature         方法签名
	 * @param methodSignatureHashCode 方法签名哈希
	 * @param args                    入参
	 * @param argsHashCode            入参哈希
	 * @param cacheHashCode           缓存哈希
	 * @param id                      缓存ID
	 * @param remark                  缓存备注
	 * @param hit                     命中
	 * @param invokeException         请求异常
	 * @param stackTraceOfException   异常栈
	 * @param startTimestamp          记录开始时间
	 */
	default void recordStatistics(String cacheKey, String methodSignature, int methodSignatureHashCode, String args, int argsHashCode,
								  int cacheHashCode, String id, String remark, boolean hit, boolean invokeException, String stackTraceOfException,
								  long startTimestamp, long endTimestamp) {
		recordExecutorService.execute(() -> {
			try {
				cacheStatisticsInfoQueue.put(new CacheStatisticsNode(cacheKey, methodSignature, methodSignatureHashCode, args, argsHashCode,
						cacheHashCode, id, remark, hit, invokeException, stackTraceOfException, startTimestamp, endTimestamp));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * 清空统计
	 *
	 * @param id              缓存ID
	 * @param methodSignature 方法签名
	 * @return 删除的缓存
	 */
	default Map<String, CacheStatisticsModel> wipeStatistics(String id, String methodSignature) {
		Map<String, CacheStatisticsModel> cacheStatistics = getCacheStatistics();

		if (cacheStatistics.isEmpty()) {
			return new HashMap<>();
		}

		Map<String, CacheStatisticsModel> resultMap = new HashMap<>();

		for (String key : cacheStatistics.keySet()) {
			CacheStatisticsModel statisticsModel = cacheStatistics.get(key);
			if (!StringUtils.isEmpty(id) && id.equals(statisticsModel.getId()) || !StringUtils.isEmpty(methodSignature) && methodSignature.equals(key)) {
				resultMap.put(key, statisticsModel);
			}
		}

		if (resultMap.isEmpty()) {
			return new HashMap<>();
		}

		for (CacheStatisticsModel statisticsModel : resultMap.values()) {
			wipeStatistics(statisticsModel);
		}
		return resultMap;
	}

	/**
	 * 格式化时间
	 *
	 * @param timeStamp 时间戳
	 * @return 格式化后的时间
	 */
	default String formatDate(long timeStamp) {
		try {
			return formatDate.format(new Date(timeStamp));
		} catch (Exception e) {
			e.printStackTrace();
			return String.valueOf(timeStamp);
		}
	}

	/**
	 * 打印异常栈
	 *
	 * @param stackTrace 异常栈
	 * @return 异常信息
	 */
	default String printStackTrace(Object[] stackTrace) {
		if (stackTrace == null)
			return "";

		int iMax = stackTrace.length - 1;
		if (iMax == -1)
			return "";

		StringBuilder b = new StringBuilder();
		for (int i = 0; ; i++) {
			b.append(String.valueOf(stackTrace[i])).append("\n");
			if (i == iMax)
				return b.toString();
		}
	}

	/**
	 * 输出异常栈
	 *
	 * @param throwable 异常
	 * @param uuid      UUID
	 * @return 异常信息
	 */
	default String printStackTrace(Throwable throwable, String uuid) {
		return "UUID=[" + uuid + "];message=[" + throwable.getMessage() + "];stackTrace=" + Arrays.toString(throwable.getStackTrace()) + "]";
	}

	/**
	 * 对象为空判定
	 *
	 * @param o        被判定的对象
	 * @param nullable 允许空数据
	 */
	default boolean isNotNull(Object o, boolean nullable) {
		if(o instanceof NullObject){
			return false;
		}

		return o != null || nullable;
	}

	/**
	 * 空对象
	 * */
	class NullObject implements Serializable {
		private static final long serialVersionUID = 1L;
	}


	/**
	 * 缓存统计信息节点
	 */
	class CacheStatisticsNode {
		/**
		 * 缓存key
		 * 由：applicationName、methodSignature、cacheHashCode、id组成
		 */
		private String cacheKey;

		/**
		 * 方法签名
		 */
		private String methodSignature;

		/**
		 * 方法签名哈希值
		 */
		private int methodSignatureHashCode;

		/**
		 * 请求入参
		 */
		private String args;

		/**
		 * 请求入参哈希值
		 */
		private int argsHashCode;

		/**
		 * 缓存哈希值
		 */
		private int cacheHashCode;

		/**
		 * 缓存ID
		 */
		private String id;

		/**
		 * 缓存备注
		 */
		private String remark;

		/**
		 * 缓存命中
		 */
		private boolean hit;

		/**
		 * 请求异常
		 */
		private boolean invokeException;

		/**
		 * 请求异常信息
		 */
		private String stackTraceOfException;

		/**
		 * 请求开始时间
		 */
		private long startTimestamp;

		/**
		 * 请求结束时间
		 */
		private long endTimestamp;

		public CacheStatisticsNode(String cacheKey, String methodSignature, int methodSignatureHashCode, String args, int argsHashCode,
								   int cacheHashCode, String id, String remark, boolean hit, boolean invokeException, String stackTraceOfException,
								   long startTimestamp, long endTimestamp) {
			this.cacheKey = cacheKey;
			this.methodSignature = methodSignature;
			this.methodSignatureHashCode = methodSignatureHashCode;
			this.args = args;
			this.argsHashCode = argsHashCode;
			this.cacheHashCode = cacheHashCode;
			this.id = id;
			this.remark = remark;
			this.hit = hit;
			this.invokeException = invokeException;
			this.stackTraceOfException = stackTraceOfException;
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

		public boolean isInvokeException() {
			return invokeException;
		}

		public long getStartTimestamp() {
			return startTimestamp;
		}

		public String getStackTraceOfException() {
			return stackTraceOfException;
		}

		public void setStackTraceOfException(String stackTraceOfException) {
			this.stackTraceOfException = stackTraceOfException;
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
