package love.kill.methodcache.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 版本信息
 *
 * @author Lycop
 */
@RestController
@RequestMapping("/methodcache/cache")
public class Info {
	@GetMapping("/version")
	public Map version(){
		Map<String,String> versionMap = new HashMap<>();
		versionMap.put("version","2.0.0");
		versionMap.put("release time","2022-07-26");
		return versionMap;
	}
}
