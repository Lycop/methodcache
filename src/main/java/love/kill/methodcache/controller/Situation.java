package love.kill.methodcache.controller;

import love.kill.methodcache.datahelper.DataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

/**
 * 缓存信息
 *
 * @author Lycop
 */
@ConditionalOnProperty(prefix = "methodcache",name = "enable-endpoint" , havingValue = "true")
@RestController
@RequestMapping("/methodcache/situation")
public class Situation {

	@Autowired
	private DataHelper dataHelper;

	/**
	 * 查询所有缓存情况
	 *
	 * @param match  模糊匹配，支持：方法签名、缓存ID、缓存哈希值
	 * @param orderBy 排序内容，0-id，1-命中次数，2-未命中次数，3-命中时平均耗时，4-未命中时平均耗时
	 * @param orderType 排序方式，0-升序，1-降序
	 * @return 所有匹配成功的缓存
	 */
	@GetMapping
	public Map<String, Map<String, Object>> get(@RequestParam(value = "match", required = false) String match, @RequestParam(value = "order_by", required = false) String orderBy, @RequestParam(value = "order_type", required = false) String orderType) {
		Map<String, Map<String, Object>> situation = dataHelper.getSituation(match);

		if(situation == null || situation.isEmpty() || StringUtils.isEmpty(orderBy) && StringUtils.isEmpty(orderType)){
			return situation;
		}

		Map<String, Map<String, Object>> orderly = new TreeMap<>((situationKey1, situationKey2)-> compare(situation.get(situationKey1), situation.get(situationKey2), orderBy, orderType));

		for (String eachSituationKey : situation.keySet()) {
			orderly.put(eachSituationKey, situation.get(eachSituationKey));
		}

		return orderly;
	}

	/**
	 * 排序
	 *
	 * @param orderBy 排序内容，0-id，1-命中次数，2-未命中次数，3-命中时平均耗时，4-未命中时平均耗时
	 * @param orderType 排序方式，0-升序，1-降序
	 * @return 排序结果
	 * */
	private int compare(Map<String, Object> situationVal1, Map<String, Object> situationVal2, String orderBy, String orderType) {

		switch (orderBy){
			case "0":{
				// id
				return doSort(situationVal1.get("id"), situationVal2.get("id"), orderType);
			}
			case "1":{
				// 命中次数
				return doSort(situationVal1.get("hit"), situationVal2.get("hit"), orderType);
			}
			case "2":{
				// 未命中次数
				return doSort(situationVal1.get("failure"), situationVal2.get("failure"), orderType);
			}
			case "3":{
				// 命中时平均耗时
				return doSort(situationVal1.get("avgOfHitSpend"), situationVal2.get("avgOfHitSpend"), orderType);
			}
			case "4":{
				// 命中时平均耗时
				return doSort(situationVal1.get("avgOfFailureSpend"), situationVal2.get("avgOfFailureSpend"), orderType);
			}
		}
		return -1;
	}

	/**
	 * 比较
	 *
	 * @param o1，比较对象1
	 * @param o2，比较对象2
	 * @param orderType 排序方式，0-升序，1-降序
	 * @return 比较结果
	 * */
	@SuppressWarnings("unchecked")
	private int doSort(Object o1, Object o2, String orderType) {
		if(o1.getClass() != o2.getClass() || !(o1 instanceof Comparable)){
			return -1;
		}

		int c= "0".equals(orderType) ? ((Comparable) o1).compareTo(o2) : ((Comparable) o2).compareTo(o1);
		if(c == 0){
			// 相同时不覆盖
			c = -1;
		}
		return c;
	}
}
