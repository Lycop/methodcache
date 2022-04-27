package love.kill.methodcache;

import love.kill.methodcache.advisor.CacheDataInterceptor;
import love.kill.methodcache.advisor.CacheDataPointcutAdvisor;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.datahelper.MemoryDataHelper;
import love.kill.methodcache.datahelper.RedisDataHelper;
import love.kill.methodcache.util.RedisUtil;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@EnableConfigurationProperties(MethodcacheProperties.class)
@ConditionalOnProperty(prefix = "methodcache",name = "enable" , havingValue = "true")
@ComponentScan(basePackages = {"love.kill.methodcache.controller"})
public class MethodcacheAutoConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "methodcache",name = "cache-type" , havingValue = "R")
	@ConditionalOnMissingBean
	@ConditionalOnClass({RedisTemplate.class})
	DataHelper redisDataHelper(RedisTemplate redisTemplate,MethodcacheProperties methodcacheProperties){
		RedisDataHelper redisDataHelper = new RedisDataHelper(methodcacheProperties);
		redisDataHelper.setRedisUtil(new RedisUtil(redisTemplate));
		return redisDataHelper;
	}

	@Bean
	@ConditionalOnMissingBean
	DataHelper memoryDataHelper(MethodcacheProperties methodcacheProperties){
		return new MemoryDataHelper(methodcacheProperties);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
		return new DefaultAdvisorAutoProxyCreator();
	}

	@Bean
	public CacheDataPointcutAdvisor methodPointcutAdvisor(CacheDataInterceptor cacheDataInterceptor,MethodcacheProperties methodcacheProperties) {
		CacheDataPointcutAdvisor advisor = new CacheDataPointcutAdvisor();
		advisor.setAdvice(cacheDataInterceptor);
		advisor.setOrder(methodcacheProperties.getOrder());
		return advisor;
	}

	@Bean
	public CacheDataInterceptor cacheDataInterceptor(MethodcacheProperties methodcacheProperties, @Autowired(required = false) DataHelper dataHelper) {
		return new CacheDataInterceptor(methodcacheProperties,dataHelper);
	}
}