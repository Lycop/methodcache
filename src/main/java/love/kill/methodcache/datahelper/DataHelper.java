package love.kill.methodcache.datahelper;

import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

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
	 * 获取数据
	 * @param method 方法
	 * @param args 请求参数
	 * @param refreshData 刷新数据
	 * @param actualDataFunctional 请求模型
	 * @return 数据
	 * */
	Object getData(Method method, Object[] args, boolean refreshData, ActualDataFunctional actualDataFunctional, String id, String remark, boolean nullable) throws Exception;


	/**
	 * 请求模型
	 * */
	interface ActualDataFunctional{
		/**
		 * 发起一次真实请求，缓存并返回该数据
		 * @return 请求数据
		 * */
		Object getActualData() throws Throwable;

		/**
		 * 数据过期时间，时间戳
		 * @return 过期时间
		 * */
		long getExpirationTime();
	}

	/**
	 * 格式化时间
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
	 * @param match 匹配规则
	 * @return 缓存信息
	 * */
	Map<String, Map<String,Object>> getSituation(String match);


	/**
	 * 筛选符合的缓存数据
	 *
	 * @param cacheMap 缓存数据
	 * @param cacheDataModel 待筛选的节点
	 * @return 符合的缓存
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

	/**
	 * 筛选符合的缓存情况
	 *
	 * @param cacheMap 缓存数据
	 * @param cacheSituationModel 待筛选的节点
	 * @param match 匹配条件
	 * */
	@SuppressWarnings("unchecked")
	default void filterSituationModel(Map<String, Map<String, Object>> cacheMap, CacheSituationModel cacheSituationModel, String match) {

		if (!StringUtils.isEmpty(match)) {
			int cacheHashCode = cacheSituationModel.getCacheHashCode();
			String methodSignature = cacheSituationModel.getMethodSignature();
			String id = cacheSituationModel.getId();
			if (!match.equals(String.valueOf(cacheHashCode)) && !methodSignature.contains(match) && !match.equals(id)) {
				return;
			}
		}

		Map<String, Object> keyMap = cacheMap.computeIfAbsent(cacheSituationModel.getMethodSignature(), k -> {
			Map<String, Object> map = new HashMap<>();
			map.put("id", cacheSituationModel.getId());
			map.put("remark", cacheSituationModel.getRemark());
			map.put("hit", 0);
			map.put("hitSpend", 0L);
			map.put("avgOfHitSpend", 0D);
			map.put("failure", 0);
			map.put("failureSpend", 0L);
			map.put("avgOfFailureSpend", 0D);
			return map;
		});

		List<Map<String, Object>> returnList = (List<Map<String, Object>>) keyMap.computeIfAbsent("situation", k -> new ArrayList<>());

		Map<String, Object> cacheInfo = new HashMap<>();
		cacheInfo.put("hashCode", cacheSituationModel.getCacheHashCode());
		cacheInfo.put("args", cacheSituationModel.getArgs());

		int hit = cacheSituationModel.getHit();
		long hitSpend = cacheSituationModel.getHitSpend();
		long minHitSpend = cacheSituationModel.getMinHitSpend();
		String timeOfMinHitSpend = formatDate(cacheSituationModel.getTimeOfMinHitSpend());
		long maxHitSpend = cacheSituationModel.getMaxHitSpend();
		String timeOfMaxHitSpend = formatDate(cacheSituationModel.getTimeOfMaxHitSpend());
		double avgOfHitSpend = (hit > 0 && hitSpend > 0L) ?
				new BigDecimal(hitSpend).divide(new BigDecimal(hit), 4, BigDecimal.ROUND_HALF_UP).doubleValue() :
				0d;

		int failure = cacheSituationModel.getFailure();
		long failureSpend = cacheSituationModel.getFailureSpend();
		long minFailureSpend = cacheSituationModel.getMinFailureSpend();
		String timeOfMinFailureSpend = formatDate(cacheSituationModel.getTimeOfMinFailureSpend());
		long maxFailureSpend = cacheSituationModel.getMaxFailureSpend();
		String timeOfMaxFailureSpend = formatDate(cacheSituationModel.getTimeOfMaxFailureSpend());
		double avgOfFailureSpend = (failure > 0 && failureSpend > 0L) ?
				new BigDecimal(failureSpend).divide(new BigDecimal(failure), 4, BigDecimal.ROUND_HALF_UP).doubleValue() :
				0d;


		cacheInfo.put("hit", hit);
		cacheInfo.put("hitSpend", hitSpend);
		cacheInfo.put("avgOfHitSpend", avgOfHitSpend);
		cacheInfo.put("minHitSpend", minHitSpend);
		cacheInfo.put("timeOfMinHitSpend", timeOfMinHitSpend);
		cacheInfo.put("maxHitSpend", maxHitSpend);
		cacheInfo.put("timeOfMaxHitSpend", timeOfMaxHitSpend);
		cacheInfo.put("failure", failure);
		cacheInfo.put("failureSpend", failureSpend);
		cacheInfo.put("avgOfFailureSpend", avgOfFailureSpend);
		cacheInfo.put("minFailureSpend", minFailureSpend);
		cacheInfo.put("timeOfMinFailureSpend", timeOfMinFailureSpend);
		cacheInfo.put("maxFailureSpend", maxFailureSpend);
		cacheInfo.put("timeOfMaxFailureSpend", timeOfMaxFailureSpend);
		returnList.add(cacheInfo);


		int totalHit = getTotalHit(keyMap) + hit;
		long totalHitSpend = getTotalHitSpend(keyMap) + hitSpend;
		long totalMinHitSpend = getMinHitSpend(keyMap);
		String totalTimeOfMinHitSpend = getTimeOfMinHitSpend(keyMap);
		if (totalMinHitSpend == 0L || totalMinHitSpend > minHitSpend) {
			totalMinHitSpend = minHitSpend;
			totalTimeOfMinHitSpend = timeOfMinHitSpend;
		}
		long totalMaxHitSpend = getMaxHitSpend(keyMap);
		String totalTimeOfMaxHitSpend = getTimeOfMaxHitSpend(keyMap);
		if (totalMaxHitSpend < maxHitSpend) {
			totalMaxHitSpend = maxHitSpend;
			totalTimeOfMaxHitSpend = timeOfMaxHitSpend;
		}
		double totalAvgOfHitSpend = (totalHit > 0 && totalHitSpend > 0L) ?
				new BigDecimal(totalHitSpend).divide(new BigDecimal(totalHit), 4, BigDecimal.ROUND_HALF_UP).doubleValue() :
				0d;

		int totalFailure = getTotalFailure(keyMap) + failure;
		long totalFailureSpend = getTotalFailureSpend(keyMap) + failureSpend;
		long totalMinFailureSpend = getMinFailureSpend(keyMap);
		String totalTimeOfMinFailureSpend = getTimeOfMinFailureSpend(keyMap);
		if (totalMinFailureSpend == 0L || getMinFailureSpend(keyMap) > minFailureSpend) {
			totalMinFailureSpend = minFailureSpend;
			totalTimeOfMinFailureSpend = timeOfMinFailureSpend;
		}
		long totalMaxFailureSpend = getMaxFailureSpend(keyMap);
		String totalTimeOfMaxFailureSpend = getTimeOfMaxFailureSpend(keyMap);
		if (totalMaxFailureSpend < maxFailureSpend) {
			totalMaxFailureSpend = maxFailureSpend;
			totalTimeOfMaxFailureSpend = timeOfMaxFailureSpend;
		}
		double totalAvgOfFailureSpend = (totalFailure > 0 && totalFailureSpend > 0L) ?
				new BigDecimal(totalFailureSpend).divide(new BigDecimal(totalFailure), 4, BigDecimal.ROUND_HALF_UP).doubleValue() :
				0d;

		keyMap.put("hit", totalHit);
		keyMap.put("hitSpend", totalHitSpend);
		keyMap.put("avgOfHitSpend", totalAvgOfHitSpend);
		keyMap.put("minHitSpend", totalMinHitSpend);
		keyMap.put("timeOfMinHitSpend", totalTimeOfMinHitSpend);
		keyMap.put("maxHitSpend", totalMaxHitSpend);
		keyMap.put("timeOfMaxHitSpend", totalTimeOfMaxHitSpend);
		keyMap.put("failure", totalFailure);
		keyMap.put("failureSpend", totalFailureSpend);
		keyMap.put("avgOfFailureSpend", totalAvgOfFailureSpend);
		keyMap.put("minFailureSpend", totalMinFailureSpend);
		keyMap.put("timeOfMinFailureSpend", totalTimeOfMinFailureSpend);
		keyMap.put("maxFailureSpend", totalMaxFailureSpend);
		keyMap.put("timeOfMaxFailureSpend", totalTimeOfMaxFailureSpend);

	}


	default long getTotalFailureSpend(Map<String, Object> keyMap){
		return keyMap.get("failureSpend") instanceof Long ? (long) keyMap.get("failureSpend") : 0;
	}

	default int getTotalHit(Map<String, Object> keyMap){
		return keyMap.get("hit") instanceof Integer ? (int) keyMap.get("hit") : 0;
	}

	default long getTotalHitSpend(Map<String, Object> keyMap){
		return keyMap.get("hitSpend") instanceof Long ? (long) keyMap.get("hitSpend") : 0L;
	}

	default long getMinHitSpend(Map<String, Object> keyMap){
		return keyMap.get("minHitSpend") instanceof Long ? (long) keyMap.get("minHitSpend") : 0L;
	}

	default String getTimeOfMinHitSpend(Map<String, Object> keyMap){
		return keyMap.get("timeOfMinHitSpend") instanceof String ? (String) keyMap.get("timeOfMinHitSpend") : "";
	}

	default long getMaxHitSpend(Map<String, Object> keyMap){
		return keyMap.get("maxHitSpend") instanceof Long ? (long) keyMap.get("maxHitSpend") : 0L;
	}

	default String getTimeOfMaxHitSpend(Map<String, Object> keyMap){
		return keyMap.get("timeOfMaxHitSpend") instanceof String ? (String) keyMap.get("timeOfMaxHitSpend") : "";
	}

	default int getTotalFailure(Map<String, Object> keyMap){
		return  keyMap.get("failure") instanceof Integer ? (int) keyMap.get("failure") : 0;
	}

	default long getMinFailureSpend(Map<String, Object> keyMap){
		return keyMap.get("minFailureSpend") instanceof Long ? (long) keyMap.get("minFailureSpend") : 0L;
	}

	default String getTimeOfMinFailureSpend(Map<String, Object> keyMap){
		return keyMap.get("timeOfMinFailureSpend") instanceof String ? (String) keyMap.get("timeOfMinFailureSpend") : "";
	}

	default long getMaxFailureSpend(Map<String, Object> keyMap){
		return keyMap.get("maxFailureSpend") instanceof Long ? (long) keyMap.get("maxFailureSpend") : 0L;
	}

	default String getTimeOfMaxFailureSpend(Map<String, Object> keyMap){
		return keyMap.get("timeOfMaxFailureSpend") instanceof String ? (String) keyMap.get("timeOfMaxFailureSpend") : "";
	}

}
