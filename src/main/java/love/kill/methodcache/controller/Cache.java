package love.kill.methodcache.controller;

import love.kill.methodcache.datahelper.DataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
	 * @param key 支持 id、备注、签名模糊查询
	 * */
	@GetMapping("/get")
	public Map<String, Map<String,Object>> cache(@RequestParam(value = "key", required = false) String key) {
		return dataHelper.getCaches(key);
	}

	/**
	 * 清除数据
	 * */
	@GetMapping("/wipe")
	public Map<String, Map<String,Object>> value(@RequestParam("hashCode") int hashCode){
		dataHelper.wipeCache(hashCode);
		return dataHelper.getCaches(null);
	}
}
