package love.kill.methodcache;

import love.kill.methodcache.aspect.CacheMethodAspect;
import love.kill.methodcache.util.DataHelper;
import love.kill.methodcache.util.MemoryDataHelper;
import love.kill.methodcache.util.RedisDataHelper;
import love.kill.methodcache.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@EnableConfigurationProperties(MethodcacheProperties.class)
@ConditionalOnProperty(prefix = "methodcache",name = "enable" , havingValue = "true")
public class MethodcacheAutoConfiguration {

//	@Bean
//	@ConditionalOnProperty(prefix = "methodcache",name = "cache-type" , havingValue = "R", matchIfMissing = true)
//	@ConditionalOnMissingBean
//	@ConditionalOnClass({RedisTemplate.class})
//	DataHelper redisDataHelper(RedisTemplate redisTemplate,MethodcacheProperties methodcacheProperties){
//		RedisDataHelper redisDataHelper = new RedisDataHelper(methodcacheProperties);
//		redisDataHelper.setRedisUtil(new RedisUtil(redisTemplate));
//		return redisDataHelper;
//	}

	@Bean
	@ConditionalOnMissingBean
	DataHelper memoryDataHelper(MethodcacheProperties methodcacheProperties){
		return new MemoryDataHelper(methodcacheProperties);
	}

	@Bean
	@ConditionalOnClass({DataHelper.class})
	CacheMethodAspect cacheMethodAspect(MethodcacheProperties methodcacheProperties,@Autowired(required = false) DataHelper dataHelper){
		CacheMethodAspect cacheMethodAspect = new CacheMethodAspect(methodcacheProperties);
		cacheMethodAspect.setDataHelper(dataHelper);
		return cacheMethodAspect;
	}
}