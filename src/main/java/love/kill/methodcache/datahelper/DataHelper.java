package love.kill.methodcache.datahelper;

import love.kill.methodcache.MethodcacheProperties;
import love.kill.methodcache.annotation.CacheDataAssert;
import love.kill.methodcache.annotation.DataAssert;
import love.kill.methodcache.annotation.ResultDataAssert;
import love.kill.methodcache.util.DataUtil;
import love.kill.methodcache.util.DateUtil;
import love.kill.methodcache.util.ThreadPoolBuilder;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 数据缓存
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public interface DataHelper {

	Logger logger = LoggerFactory.getLogger(DataHelper.class);

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
	 * 刷新数据任务执行器
	 */
	ExecutorService refreshDataExecutorService = Executors.newFixedThreadPool(10);

	/**
	 * 缓存统计线程池
	 */
	ExecutorService recordStatisticsExecutorService = ThreadPoolBuilder.buildDefaultThreadPool();

	/**
	 * 线程数据
	 * */
	ThreadLocal<String> threadLocal = new ThreadLocal<>();


	/**
	 * 共享式缓存数据锁
	 */
	ReentrantReadWriteLock sharedCacheDataLock = new ReentrantReadWriteLock();

	/**
	 * 代理类
	 *
	 * 值：＜被代理类(或接口), 代理类＞
	 */
	Map<Class , WeakReference<DataAssert>> resultDataAssertInstance = new ConcurrentHashMap<>();

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
		ActualDataModel getActualData() throws Throwable;
	}

	/**
	 * 获取数据
	 *
	 * @param proxy                代理对象
	 * @param methodInvocation     方法引用
	 * @param isolationSignal      隔离标记
	 * @param refreshData          是否刷新数据
	 * @param actualDataFunctional 请求模型
	 * @param id                   缓存ID
	 * @param remark               缓存备注
	 * @param cacheNull            缓存null
	 * @param shared               共享式数据
	 * @param cacheDataAssert      缓存数据断言
	 * @return 数据
	 * @throws Exception 获取数据时发生异常
	 */
	CacheDataModel getData(Object proxy, MethodInvocation methodInvocation, String isolationSignal, boolean refreshData,
				   ActualDataFunctional actualDataFunctional, String id, String remark, boolean cacheNull,
				   boolean shared, Class cacheDataAssert) throws Throwable;


	/**
	 * 刷新数据
	 *
	 * @param proxy        			  执行对象
	 * @param cacheDataModel          数据模型
	 * @param actualDataFunctional    真实数据请求
	 * @param nullable                返回值允许为空
	 */
	default void refreshData(final Object proxy, final CacheDataModel cacheDataModel,
							 ActualDataFunctional actualDataFunctional, boolean nullable) {

		refreshDataExecutorService.execute(() -> {

			if(cacheDataModel == null){
				return;
			}


			String methodSignature = cacheDataModel.getMethodSignature();
			String args = cacheDataModel.getArgs();
			int cacheHashCode = cacheDataModel.getCacheHashCode();
			Object data = cacheDataModel.getData();
			long expireTime = cacheDataModel.getExpireTime();
			String id = cacheDataModel.getId();
			String remark = cacheDataModel.getRemark();

			if(data == null){
				try {
					ActualDataModel actualData = actualDataFunctional.getActualData();
					data = actualData.getData();
					expireTime = actualData.getExpirationTime();
				} catch (Throwable throwable) {
					throwable.printStackTrace();
				}
			}

			CacheDataModel refreshCacheDataModel = new CacheDataModel(getCacheName(), methodSignature, args,
					cacheHashCode, data, expireTime, id, remark);

			if (!StringUtils.isEmpty(id)) {
				refreshCacheDataModel.setId(id);
			}

			if (!StringUtils.isEmpty(remark)) {
				refreshCacheDataModel.setRemark(remark);
			}

			if ((isNotNull(refreshCacheDataModel.getData(), nullable))) {
				doRefreshData(proxy, refreshCacheDataModel);
			}
		});
	}

	/**
	 * 刷新数据
	 *
	 * @param proxy        			  	执行对象
	 * @param cacheDataModel        	数据模型
	 */
	void doRefreshData(Object proxy, CacheDataModel cacheDataModel);

	/**
	 * 共享式缓存数据
	 * 内容：＜方法签名,＜缓存哈希值,数据＞＞
	 */
	Map<String, Map<Integer, WeakReference<CacheDataModel>>> sharedCacheData = new ConcurrentHashMap<>();

	/**
	 * 获取共享数据
	 *
	 * @param methodSignature 方法签名
	 * @param cacheHashCode   缓存哈希值
	 * @return 缓存数据
	 */
	static CacheDataModel getSharedData(String methodSignature, Integer cacheHashCode) {

		if(StringUtils.isEmpty(methodSignature) || StringUtils.isEmpty(cacheHashCode)){
			return null;
		}

		try {
			sharedCacheDataLock.readLock().lock();
			Map<Integer, WeakReference<CacheDataModel>> cacheDataModelMap = sharedCacheData.get(methodSignature);
			WeakReference<CacheDataModel> cacheDataModelWeakReference;
			if (cacheDataModelMap != null &&
					(cacheDataModelWeakReference = cacheDataModelMap.get(cacheHashCode)) != null) {
				return cacheDataModelWeakReference.get();
			}
		}finally {
			sharedCacheDataLock.readLock().unlock();
		}

		return null;
	}

	/**
	 * 保存共享数据
	 *
	 * @param cacheDataModel 缓存数据
	 * @return 缓存数据
	 */
	static CacheDataModel setSharedData(CacheDataModel cacheDataModel) {

		try {
			sharedCacheDataLock.writeLock().lock();
			String methodSignature = cacheDataModel.getMethodSignature();
			int cacheHashCode = cacheDataModel.getCacheHashCode();

			Map<Integer, WeakReference<CacheDataModel>> cacheDataModelMap =
					sharedCacheData.computeIfAbsent(methodSignature, k -> new HashMap<>());
			cacheDataModelMap.put(cacheHashCode, new WeakReference<>(cacheDataModel));

		} finally {
			sharedCacheDataLock.writeLock().unlock();
		}

		return cacheDataModel;
	}

	/**
	 * 决定数据
	 * 决定是返回原数据，还是共享数据
	 *
	 * @param cacheDataModel 实际数据
	 * @return 决定后的数据
	 * */
	static CacheDataModel decisionCacheDataModel(CacheDataModel cacheDataModel) {
		CacheDataModel sharedData = DataHelper.getSharedData(cacheDataModel.getMethodSignature(), cacheDataModel.getCacheHashCode());
		if(sharedData == null || cacheDataModel.getCacheTime() != sharedData.getCacheTime()){
			return DataHelper.setSharedData(cacheDataModel);
		}
		cacheDataModel = null; // help GC
		return sharedData;
	}


	/**
	 * 断言缓存数据
	 *
	 * @param cacheData            请求返回数据
	 * @param resultDataAssertClass 断言实现类
	 * @return 请求返回值断言结果
	 */
	default boolean doCacheDataAssert(Object cacheData, Class resultDataAssertClass) {
		if (resultDataAssertClass != void.class) {
			DataAssert dataAssert = getDataAssertInstance(resultDataAssertClass);
			if (!(dataAssert instanceof CacheDataAssert)) {
				// 一般不会进入
				logger.warn("获取断言缓存数据实例异常：" + dataAssert);
				return true;
			}

			CacheDataAssert cacheDataAssert = (CacheDataAssert) dataAssert;
			if (!cacheDataAssert.isClass(cacheData)) {
				logger.warn("断言方法的参数类型不一致");
				return true;
			}

			return cacheDataAssert.doAssert(cacheData);
		}
		return true;
	}

	/**
	 * 断言请求返回数据
	 *
	 * @param resultData            请求返回数据
	 * @param resultDataAssertClass 断言实现类
	 * @return 请求返回值断言结果
	 */
	default boolean doResultDataAssert(Object resultData, Class resultDataAssertClass){
		if(resultDataAssertClass != void.class){
			DataAssert dataAssert = getDataAssertInstance(resultDataAssertClass);
			if (!(dataAssert instanceof ResultDataAssert)) {
				// 一般不会进入
				logger.warn("获取断言请求返回数据实例异常：" + dataAssert);
				return true;
			}

			ResultDataAssert resultDataAssert = (ResultDataAssert) dataAssert;
			if (!resultDataAssert.isClass(resultData)) {
				logger.warn("断言方法的参数类型不一致");
				return true;
			}

			return resultDataAssert.doAssert(resultData);
		}
		return true;
	}

	/**
	 * 获取断言实例
	 */
	default DataAssert getDataAssertInstance(Class assertClass) {
		try {
			WeakReference<DataAssert> dataAssertWeakReference = resultDataAssertInstance.get(assertClass);
			DataAssert dataAssert;
			if (dataAssertWeakReference != null && (dataAssert = dataAssertWeakReference.get()) != null) {
				return dataAssert;
			}
			synchronized (resultDataAssertInstance) {
				dataAssertWeakReference = resultDataAssertInstance.get(assertClass);
				if (dataAssertWeakReference != null && (dataAssert = dataAssertWeakReference.get()) != null) {
					return dataAssert;
				}

				Object instance = assertClass.newInstance();
				if (instance instanceof DataAssert) {
					dataAssert = (DataAssert) instance;
					resultDataAssertInstance.put(assertClass, new WeakReference<>(dataAssert));
					return dataAssert;
				}
			}
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 获取缓存数据
	 *
	 * @param match 匹配规则
	 * @return key
	 */
	Map<String, Object> getCaches(String match, int pageSize, int pageNo);

	/**
	 * 清空数据
	 *
	 * @param id            缓存ID
	 * @param cacheHashCode 缓存哈希值
	 * @return 删除的缓存
	 */
	Map<String, Object> wipeCache(String id, String cacheHashCode);

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
	 * 获取应用名
	 *
	 * @return 应用名
	 */
	String getApplicationName();

	/**
	 * 获取方法缓存名
	 *
	 * @return 方法缓存名
	 */
	String getMethodCacheName();


	/**
	 * 获取方法缓存分组名
	 *
	 * @return 方法缓存分组名
	 */
	String getMethodcacheGroupName();


	/**
	 * 获取缓存名
	 * 优先级：方法缓存分组名 ＞ 方法缓存名 ＞ 获取应用名 ＞ DEFAULT_CACHE_NAME
	 *
	 * @return 缓存名
	 */
	default String getCacheName() {
		String cacheName;
		if (!StringUtils.isEmpty(getMethodcacheGroupName())) {
			cacheName = getMethodcacheGroupName();
		} else if (!StringUtils.isEmpty(getMethodCacheName())) {
			cacheName = getMethodCacheName();
		} else if (!StringUtils.isEmpty(getApplicationName())) {
			cacheName = getApplicationName();
		} else {
			cacheName = "DEFAULT_CACHE_NAME";
		}
		return cacheName;
	}

	/**
	 * 获取缓存哈希值
	 *
	 * @param methodSignatureHashCode 	方法签名哈希值
	 * @param argsHashCode            	方法入参哈希值
	 * @param extensionStr         	  	扩展字符串
	 * @return 缓存哈希值
	 */
	default int getCacheHashCode(int methodSignatureHashCode, int argsHashCode, String extensionStr) {
		StringBuilder s = new StringBuilder(getCacheName() + String.valueOf(methodSignatureHashCode) +
				String.valueOf(argsHashCode));

		if (!StringUtils.isEmpty(extensionStr)) {
			s.append(extensionStr);
		}
		return DataUtil.hash(s.toString());
	}

	/**
	 * 获取缓存key
	 *
	 * @param methodSignature 方法签名
	 * @param cacheHashCode   缓存哈希值
	 * @param id              缓存ID
	 * @return 缓存key
	 */
	default String getCacheKey(String methodSignature, int cacheHashCode, String id) {
		StringBuilder cacheKey = new StringBuilder(getCacheName() + KEY_SEPARATION_CHARACTER + methodSignature +
				KEY_SEPARATION_CHARACTER + cacheHashCode + KEY_SEPARATION_CHARACTER + id);
		return cacheKey.toString();
	}

	/**
	 * 筛选符合的缓存数据
	 *
	 * @param cacheMap       缓存数据
	 * @param cacheDataModel 待筛选的节点
	 * @param select         过滤值
	 */
	@SuppressWarnings("unchecked")
	default void filterDataModel(Map<String, Object> cacheMap, CacheDataModel cacheDataModel,
								 String select) {
		if (!StringUtils.isEmpty(select)) {
			String args = cacheDataModel.getArgs();
			if (!StringUtils.isEmpty(args) && !args.contains(select)) {
				return;
			}
		}

		Object keyMap = cacheMap.computeIfAbsent(cacheDataModel.getMethodSignature(), k -> {
			Map<String, Object> map = new HashMap<>();
			map.put("id", cacheDataModel.getId());
			map.put("remark", cacheDataModel.getRemark());
			return map;
		});

		List<Map<String, Object>> cacheInfoList =
				(List<Map<String, Object>>) ((Map<String, Object>)keyMap).computeIfAbsent("cache", k -> new ArrayList<>());

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
	default CacheStatisticsModel increaseStatistics(CacheStatisticsModel cacheStatisticsModel,
													CacheStatisticsNode cacheStatisticsNode) {
		if (cacheStatisticsModel == null) {
			cacheStatisticsModel = new CacheStatisticsModel(cacheStatisticsNode.getCacheKey(),
					cacheStatisticsNode.getMethodSignature(), cacheStatisticsNode.getMethodSignatureHashCode(),
					cacheStatisticsNode.getId(), cacheStatisticsNode.getRemark());
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
	 * @param startTimestamp          开始时间
	 * @param endTimestamp    		  结束时间
	 */
	default void recordStatistics(String cacheKey, String methodSignature, int methodSignatureHashCode, String args,
								  int argsHashCode, int cacheHashCode, String id, String remark, boolean hit,
								  boolean invokeException, String stackTraceOfException, long startTimestamp,
								  long endTimestamp) {
		recordStatisticsExecutorService.execute(() -> {
			try {
				CacheStatisticsNode cacheStatisticsNode = new CacheStatisticsNode(cacheKey, methodSignature,
						methodSignatureHashCode, args, argsHashCode, cacheHashCode, id, remark, hit, invokeException,
						stackTraceOfException, startTimestamp, endTimestamp);
				cacheStatisticsInfoQueue.put(cacheStatisticsNode);
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
			if (!StringUtils.isEmpty(id) && id.equals(statisticsModel.getId()) ||
					!StringUtils.isEmpty(methodSignature) && methodSignature.equals(key)) {
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
			return  DateUtil.FORMAT_YYYY_MM_DD_HH_MM_SS.format(new Date(timeStamp));
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
	static String printStackTrace(Object[] stackTrace) {
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
	 * @return 异常信息
	 */
	default String printStackTrace(Throwable throwable) {
		return "message=[" + throwable.getMessage() + "] \n stackTrace=" +
				Arrays.toString(throwable.getStackTrace()) + "]";
	}

	/**
	 * 对象不为空
	 *
	 * @param o        被判定的对象
	 * @param nullable 允许空数据
	 * @return 对象为空对象
	 */
	default boolean isNotNull(Object o, boolean nullable) {
		return o != null || nullable;
	}

	/**
	 * 空对象
	 * */
	class NullObject implements Serializable {
		private static final long serialVersionUID = 1L;
	}

	/**
	 * 打印日志
	 *
	 * @param info 内容
	 */
	default void log(String info, MethodcacheProperties methodcacheProperties, Logger logger) {
		if (methodcacheProperties.isEnableLog()) {
			logger.info(info);
		}
	}

	/**
	 * 实际请求模型
	 * */
	class ActualDataModel{

		/**
		 * 实际请求返回的数据
		 */
		private Object data;

		/**
		 * 数据过期时间(时间戳)
		 */
		private long expirationTime;

		/**
		 * 请求成功
		 */
		private boolean succeeded;



		public Object getData() {
			return data;
		}

		public void setData(Object data) {
			this.data = data;
		}

		public long getExpirationTime() {
			return expirationTime;
		}

		public void setExpirationTime(long expirationTime) {
			this.expirationTime = expirationTime;
		}

		public boolean isSucceeded() {
			return succeeded;
		}

		public void setSucceeded(boolean succeeded) {
			this.succeeded = succeeded;
		}
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

		public CacheStatisticsNode(String cacheKey, String methodSignature, int methodSignatureHashCode, String args,
								   int argsHashCode, int cacheHashCode, String id, String remark, boolean hit,
								   boolean invokeException, String stackTraceOfException,  long startTimestamp,
								   long endTimestamp) {
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
