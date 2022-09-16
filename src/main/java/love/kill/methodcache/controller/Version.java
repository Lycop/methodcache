package love.kill.methodcache.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "methodcache", name = "enable-endpoint", havingValue = "true")
@RestController
@RequestMapping("/methodcache/version")
public class Version {
	@GetMapping
	public Map version() {
		Map<String, String> versionMap = new HashMap<>();
		versionMap.put("version", "2.0.2");
		versionMap.put("release time", "2022-09-16");
		return versionMap;
	}
}
