package love.kill.methodcache.annotation;


import java.lang.annotation.*;

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
@Inherited
public @interface CacheData {

	/**
	 * 刷新数据
	 * 本次请求是否要刷新数据，为 true 则在返回数据后，发起线程异步请求并更新数据
	 * */
	boolean refresh() default true;

	/**
	 * 数据过期时间，毫秒
	 *
	 * 数据将会在指定时间过期，小于0表示永久有效
	 * */
	long expiration() default 30000L;

	/**
	 * 数据过期宽限期，毫秒
	 *
	 * 数据宽限过期时间，默认为0L。如果设置了此值大于0，则数据会在{@link #expiration()}基础上进行随机累加。
	 * 如：过期时间为30000毫秒，过期宽限期为10000毫秒，则数据会在30000～40000之间随机一个值过期。
	 * 这个值可以避免数据大规模同时失效引起的缓存雪崩
	 * */
	long behindExpiration() default 0L;

	/**
	 *  数据过期时间累加基础
	 *
	 *  设置一个类型，作为数据过期计算的基础时间进行累加，可选的范围：
	 * 	SECOND,MINUTE,HOUR,DAY,MONTH,YEAR
	 *
	 *  举个例子：设置BasicExpiration.HOUR，数据返回时间为16:38:22，那么16:38:22~16:59:59是有效的，17:00:00开始计算失效时间。
	 * */
	CapitalExpiration capitalExpiration() default CapitalExpiration.SECOND;

}
