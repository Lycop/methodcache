package love.kill.methodcache.controller;

import love.kill.methodcache.util.CacheDataModel;
import love.kill.methodcache.util.DataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
	 * 查询缓存中所有key
	 *
	 * @param key 筛选
	 * */
	@GetMapping("/keys")
	public List<String> keys(String key){
		// TODO: 2022/4/15
		return dataHelper.getKeys();
	}

	/**
	 * 查询缓存中所有key
	 * */
	@GetMapping("/value")
	public String value(@RequestParam("key") String key){
		return dataHelper.getData(key).toJSONString();
	}
}
