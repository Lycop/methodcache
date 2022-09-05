package love.kill.methodcache.controller;

import love.kill.methodcache.datahelper.CacheStatisticsModel;
import love.kill.methodcache.datahelper.DataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 缓存统计信息
 *
 * @author Lycop
 */
@ConditionalOnProperty(prefix = "methodcache", name = "enable-endpoint", havingValue = "true")
@RestController
@RequestMapping("/methodcache/statistics")
public class Statistics {

	@Autowired
	private DataHelper dataHelper;

	/**
	 * 查询缓存统计
	 *
	 * @param match     模糊匹配，支持：方法签名、缓存ID
	 * @param orderBy   排序内容，0-id，1-总次数，2-命中次数，3-未命中次数，4-命中时平均耗时，5-未命中时平均耗时
	 * @param orderType 排序方式，0-升序，1-降序
	 * @return 所有匹配成功的缓存
	 */
	@GetMapping
	public Map<String, Map<String, Object>> get(@RequestParam(value = "match", required = false) String match, @RequestParam(value = "order_by", required = false) String orderBy, @RequestParam(value = "order_type", required = false) String orderType) {

		Map<String, CacheStatisticsModel> statistics = dataHelper.getStatistics(match);

		if (statistics == null) {
			return new HashMap<>();
		}

		Map<String, Map<String, Object>> resultMap = new TreeMap<>((situationKey1, situationKey2) ->
				compare(statistics.get(situationKey1),
						statistics.get(situationKey2),
						StringUtils.isEmpty(orderBy) ? "-1" : orderBy,
						StringUtils.isEmpty(orderType) ? "0" : orderType)
		);

		return transferStatistics(statistics, resultMap);
	}

	/**
	 * 清除缓存统计
	 *
	 * @param id 缓存ID
	 * @param methodSignature 方法签名
	 * @return 删除的缓存
	 */
	@DeleteMapping
	public Map<String, Map<String, Object>> delete(@RequestParam(value = "id", required = false) String id, @RequestParam(value = "method", required = false) String methodSignature) {
		if (StringUtils.isEmpty(id) && StringUtils.isEmpty(methodSignature)) {
			return new HashMap<>();
		}

		Map<String, CacheStatisticsModel> wipeStatistics = dataHelper.wipeStatistics(id, methodSignature);

		Map<String, Map<String, Object>> resultMap = new TreeMap<>((situationKey1, situationKey2) ->
				compare(wipeStatistics.get(situationKey1),
						wipeStatistics.get(situationKey2),
						"-1",
						"0")
		);

		return transferStatistics(wipeStatistics, resultMap);
	}

	/**
	 * 清除所有数据
	 *
	 * @return 删除的缓存
	 */
	@DeleteMapping("/all")
	public Map<String, Map<String, Object>> deleteAll() {

		Map<String, CacheStatisticsModel> wipeStatistics = dataHelper.wipeStatisticsAll();

		Map<String, Map<String, Object>> resultMap = new TreeMap<>((situationKey1, situationKey2) ->
				compare(wipeStatistics.get(situationKey1),
						wipeStatistics.get(situationKey2),
						"-1",
						"0")
		);

		return transferStatistics(wipeStatistics, resultMap);
	}

	private Map<String, Map<String, Object>> transferStatistics(Map<String, CacheStatisticsModel> statistics, Map<String, Map<String, Object>> targetMap){
		for (String methodSignature : statistics.keySet()) {
			CacheStatisticsModel statisticsModel = statistics.get(methodSignature);
			Map<String, Object> statisticsInfo = new HashMap<>();
			statisticsInfo.put("id", statisticsModel.getId());
			statisticsInfo.put("remark", statisticsModel.getRemark());
			statisticsInfo.put("times", statisticsModel.printTimes());
			statisticsInfo.put("hit", statisticsModel.printHit());
			statisticsInfo.put("avgOfHitSpend", statisticsModel.printAvgOfHitSpend());
			statisticsInfo.put("totalOfHitSpend", statisticsModel.printTotalOfHitSpend());
			statisticsInfo.put("minHitSpend", statisticsModel.printMinHitSpend());
			statisticsInfo.put("timeOfMinHitSpend", statisticsModel.printTimeOfMinHitSpend());
			statisticsInfo.put("argsOfMinHitSpend", statisticsModel.printArgsOfMinHitSpend());
			statisticsInfo.put("maxHitSpend", statisticsModel.printMaxHitSpend());
			statisticsInfo.put("timeOfMaxHitSpend", statisticsModel.printTimeOfMaxHitSpend());
			statisticsInfo.put("argsOfMaxHitSpend", statisticsModel.printArgsOfMaxHitSpend());
			statisticsInfo.put("failure", statisticsModel.printFailure());
			statisticsInfo.put("avgOfFailureSpend", statisticsModel.printAvgOfFailureSpend());
			statisticsInfo.put("totalOfFailureSpend", statisticsModel.printTotalOfFailureSpend());
			statisticsInfo.put("minFailureSpend", statisticsModel.printMinFailureSpend());
			statisticsInfo.put("timeOfMinFailureSpend", statisticsModel.printTimeOfMinFailureSpend());
			statisticsInfo.put("argsOfMinFailureSpend", statisticsModel.printArgsOfMinFailureSpend());
			statisticsInfo.put("maxFailureSpend", statisticsModel.printMaxFailureSpend());
			statisticsInfo.put("timeOfMaxFailureSpend", statisticsModel.printTimeOfMaxFailureSpend());
			statisticsInfo.put("argsOfMaxFailureSpend", statisticsModel.printArgsOfMaxFailureSpend());
			targetMap.put(methodSignature, statisticsInfo);
		}

		return targetMap;
	}

	/**
	 * 排序
	 *
	 * @param orderBy   排序内容，0-id，1-总次数，2-命中次数，3-未命中次数，4-命中时平均耗时，5-未命中时平均耗时
	 * @param orderType 排序方式，0-升序，1-降序
	 * @return 排序结果
	 */
	private int compare(CacheStatisticsModel model1, CacheStatisticsModel model2, String orderBy, String orderType) {

		switch (orderBy) {
			case "0": {
				// id
				return doSort(model1.getId(), model2.getId(), orderType);
			}
			case "1": {
				// 总次数
				return doSort(model1.getTimes(), model2.getTimes(), orderType);
			}
			case "2": {
				// 命中次数
				return doSort(model1.getHit(), model2.getHit(), orderType);
			}
			case "3": {
				// 未命中次数
				return doSort(model1.getFailure(), model2.getFailure(), orderType);
			}
			case "4": {
				// 命中时平均耗时
				return doSort(model1.getAvgOfHitSpend(), model2.getAvgOfHitSpend(), orderType);
			}
			case "5": {
				// 命中时平均耗时
				return doSort(model1.getAvgOfFailureSpend(), model2.getAvgOfFailureSpend(), orderType);
			}
			default: {
				// 名称
				return doSort(model1.getMethodSignature(), model2.getMethodSignature(), orderType);
			}
		}
	}

	/**
	 * 比较
	 *
	 * @param o1，比较对象1
	 * @param o2，比较对象2
	 * @param orderType 排序方式，0-升序，1-降序
	 * @return 比较结果
	 */
	@SuppressWarnings("unchecked")
	private int doSort(Object o1, Object o2, String orderType) {
		if (o1.getClass() != o2.getClass() || !(o1 instanceof Comparable)) {
			return -1;
		}

		int c = "0".equals(orderType) ? ((Comparable) o1).compareTo(o2) : ((Comparable) o2).compareTo(o1);
		if (c == 0) {
			// 相同时不覆盖
			c = -1;
		}
		return c;
	}
}
