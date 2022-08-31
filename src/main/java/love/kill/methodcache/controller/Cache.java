package love.kill.methodcache.controller;

import love.kill.methodcache.datahelper.DataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存数据
 *
 * @author Lycop
 */
@ConditionalOnProperty(prefix = "methodcache", name = "enable-endpoint", havingValue = "true")
@RestController
@RequestMapping("/methodcache/cache")
public class Cache {

	@Autowired
	private DataHelper dataHelper;

	/**
	 * 查询所有缓存数据
	 *
	 * @param match 模糊匹配，支持：方法签名、缓存ID、缓存哈希值
	 * @return 所有匹配成功的缓存
	 */
	@GetMapping
	public Map<String, Map<String, Object>> get(@RequestParam(value = "match", required = false) String match) {
		return dataHelper.getCaches(match);
	}

	/**
	 * 清除数据
	 *
	 * @param id       缓存ID
	 * @param hashCode 缓存哈希值
	 * @return 删除的缓存
	 */
	@DeleteMapping
	public Map<String, Map<String, Object>> delete(@RequestParam(value = "id", required = false) String id, @RequestParam(value = "hashcode", required = false) String hashCode) {
		if (StringUtils.isEmpty(id) && StringUtils.isEmpty(hashCode)) {
			return new HashMap<>();
		}
		return dataHelper.wipeCache(id, hashCode);
	}

	/**
	 * 清除所有数据
	 *
	 * @return 删除的缓存
	 */
	@DeleteMapping("/all")
	public Map<String, Map<String, Object>> deleteAll() {
		return dataHelper.wipeCache(null, null);
	}
}
