package love.kill.methodcache.controller;

import love.kill.methodcache.datahelper.DataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存信息
 *
 * @author Lycop
 */
@ConditionalOnProperty(prefix = "methodcache",name = "enable-endpoint" , havingValue = "true")
@RestController
@RequestMapping("/methodcache/Situation")
public class Situation {

	@Autowired
	private DataHelper dataHelper;

	/**
	 * 查询所有缓存情况
	 *
	 * @param match  模糊匹配，支持：方法签名、缓存哈希值、缓存ID
	 * @param select 筛选入参
	 * @return 所有匹配成功的缓存
	 */
	@GetMapping
	public Map<String, Map<String, Object>> get(@RequestParam(value = "match", required = false) String match, @RequestParam(value = "select", required = false) String select) {
		return dataHelper.getSituation(match, select);
	}
}
