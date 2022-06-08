package love.kill.methodcache.controller;

import love.kill.methodcache.datahelper.DataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存数据
 *
 * @author Lycop
 */
@RestController
@RequestMapping("/methodcache/cache")
public class Cache {

	@Autowired
	private DataHelper dataHelper;

	/**
	 * 查询所有缓存
	 *
	 * @param match  模糊匹配，支持：(注解上的)id、(注解上的)备注、方法签名
	 * @param select 筛选入参
	 */
	@GetMapping("/get")
	public Map<String, Map<String, Object>> cache(@RequestParam(value = "match", required = false) String match, @RequestParam(value = "select", required = false) String select) {
		return dataHelper.getCaches(match, select);
	}

	/**
	 * 清除数据
	 *
	 * @param id 缓存ID
	 * @param hashCode  哈希值，支持：方法签名哈希值、缓存哈希值
	 */
	@GetMapping("/wipe")
	public Map<String, Map<String, Object>> value(@RequestParam(value = "id", required = false) String id, @RequestParam(value = "hashcode", required = false) String hashCode) {
		if (StringUtils.isEmpty(id) && StringUtils.isEmpty(hashCode)){
			return new HashMap<>();
		}
		return dataHelper.wipeCache(id, hashCode);
	}

//	/**
//	 * 清除数据
//	 *
//	 * @param id 缓存id
//	 * @param hashCode  哈希值，支持：方法签名哈希值、缓存哈希值
//	 */
//	@GetMapping("/wipeall")
//	public Map<String, Map<String, Object>> value(@RequestParam(value = "id", required = false) String id, @RequestParam("hashcode") int hashCode) {
//		dataHelper.wipeCache(hashCode);
//		return dataHelper.getCaches(null, null);
//	}
}
