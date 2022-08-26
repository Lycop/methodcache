package love.kill.methodcache;

import love.kill.methodcache.advisor.CacheDataInterceptor;
import love.kill.methodcache.annotation.CacheData;
import love.kill.methodcache.datahelper.DataHelper;
import love.kill.methodcache.datahelper.impl.MemoryDataHelper;
import love.kill.methodcache.datahelper.impl.RedisDataHelper;
import love.kill.methodcache.util.MemoryMonitor;
import love.kill.methodcache.util.RedisUtil;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
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
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;

@Configuration
@EnableConfigurationProperties({MethodcacheProperties.class, SpringApplicationProperties.class})
@ConditionalOnProperty(prefix = "methodcache",name = "enable" , havingValue = "true")
@ComponentScan(basePackages = {"love.kill.methodcache.controller"})
public class MethodcacheAutoConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "methodcache",name = "cache-type" , havingValue = "R")
	@ConditionalOnMissingBean
	@ConditionalOnClass({RedisTemplate.class})
	DataHelper redisDataHelper(RedisTemplate redisTemplate, MethodcacheProperties methodcacheProperties, SpringApplicationProperties springProperties){

		RedisTemplate<Object, Object> cacheRedisTemplate = new RedisTemplate<>();
		cacheRedisTemplate.setConnectionFactory(redisTemplate.getConnectionFactory());

		StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
		cacheRedisTemplate.setKeySerializer(stringRedisSerializer);
		cacheRedisTemplate.setValueSerializer(stringRedisSerializer);
		cacheRedisTemplate.setHashKeySerializer(stringRedisSerializer);
		cacheRedisTemplate.setHashValueSerializer(stringRedisSerializer);
		cacheRedisTemplate.afterPropertiesSet();

		return new RedisDataHelper(new RedisUtil(cacheRedisTemplate), methodcacheProperties, springProperties);
	}

	@Bean
	@ConditionalOnClass({MemoryDataHelper.class})
	@ConditionalOnProperty(prefix = "methodcache",name = "enable-memory-monitor", havingValue = "true", matchIfMissing = true)
	MemoryMonitor memoryMonitor(MethodcacheProperties methodcacheProperties){
		return new MemoryMonitor(methodcacheProperties);
	}

	@Bean
	@ConditionalOnMissingBean
	DataHelper memoryDataHelper(MethodcacheProperties methodcacheProperties, SpringApplicationProperties springProperties, @Nullable MemoryMonitor memoryMonitor){
		return new MemoryDataHelper(methodcacheProperties, springProperties, memoryMonitor);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
		return new DefaultAdvisorAutoProxyCreator();
	}

	@Bean
	public CacheDataInterceptor cacheDataInterceptor(MethodcacheProperties methodcacheProperties, @Autowired(required = false) DataHelper dataHelper) {
		return new CacheDataInterceptor(methodcacheProperties,dataHelper);
	}

	@Bean
	public StaticMethodMatcherPointcutAdvisor methodPointcutAdvisor(CacheDataInterceptor cacheDataInterceptor,MethodcacheProperties methodcacheProperties) {
		StaticMethodMatcherPointcutAdvisor advisor = new StaticMethodMatcherPointcutAdvisor(){
			@Override
			public boolean matches(Method method, Class<?> targetClass) {
				// 拦截被 @CacheData 注解的接口或类
				return method.isAnnotationPresent(CacheData.class) || targetClass.isAnnotationPresent(CacheData.class);
			}
		};
		advisor.setAdvice(cacheDataInterceptor);
		advisor.setOrder(methodcacheProperties.getOrder());
		return advisor;
	}
}