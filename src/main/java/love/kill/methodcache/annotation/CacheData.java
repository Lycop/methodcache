package love.kill.methodcache.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 缓存数据
 *
 * 被注解的方法，会根据请求参数(hashcode)进行匹配，匹配成功返回缓存数据
 *
 * refresh 刷新数据。为true时，本次请求返回后，会发起一次异步的请求，并更新缓存中的数据
 * expiration 缓存有效期。缓存的数据超过该值(毫秒)，则会重新发起请求获取数据。
 *
 *
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CacheData {

	boolean refresh() default true;

	long expiration() default 30000L;

}
