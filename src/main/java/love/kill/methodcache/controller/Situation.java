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
@RequestMapping("/methodcache/situation")
public class Situation {

	@Autowired
	private DataHelper dataHelper;

	/**
	 * 查询所有缓存情况 todo 增加排序功能，按照：命中次数、
	 *
	 * @param match  模糊匹配，支持：方法签名、缓存ID、缓存哈希值
	 * @return 所有匹配成功的缓存
	 */
	@GetMapping
	public Map<String, Map<String, Object>> get(@RequestParam(value = "match", required = false) String match) {
		return dataHelper.getSituation(match);
	}
}
