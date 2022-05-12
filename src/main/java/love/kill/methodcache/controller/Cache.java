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
	 * @param match 模糊匹配，支持：(注解上的)id、(注解上的)备注、方法签名
	 * @param select 筛选入参
	 * */
	@GetMapping("/get")
	public Map<String, Map<String,Object>> cache(@RequestParam(value = "match", required = false) String match, @RequestParam(value = "select", required = false) String select) {
		return dataHelper.getCaches(match,select);
	}

	/**
	 * 清除数据
	 * */
	@GetMapping("/wipe")
	public Map<String, Map<String,Object>> value(@RequestParam("hashcode") int hashCode){
		dataHelper.wipeCache(hashCode);
		return dataHelper.getCaches(null,null);
	}
}
