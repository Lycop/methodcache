package love.kill.methodcache.annotation;


import java.lang.annotation.*;

/**
 * 缓存数据
 *
 * 被注解的方法，会根据请求参数(hashcode)进行匹配，匹配成功返回缓存数据
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
	 * id
	 * 标识一批缓存，可以用来清除缓存
	 * */
	String id() default "";

	/**
	 * 刷新数据
	 *
	 * 每次请求都刷新缓存，true 则在返回数据后，异步请求并更新数据
	 * */
	boolean refresh() default true;

	/**
	 * 数据过期时间，毫秒
	 *
	 * 数据将会在指定时间过期，小于0表示不会过期
	 * */
	long expiration() default 30000L;

	/**
	 * 宽限期，毫秒
	 *
	 * 数据宽限过期时间，默认为0L。如果设置了此值大于0，则数据会在{@link #expiration()}基础上进行随机累加。
	 * 此值可以用于避免数据同时失效引起的缓存雪崩。
	 * 如：过期时间为30000毫秒，过期宽限期为10000毫秒，则数据会在30000～40000之间随机一个值过期。
	 * */
	long behindExpiration() default 0L;

	/**
	 *  数据过期时间累加基础
	 *
	 *  设置一个日期类型，作为数据过期计算的基础时间进行累加，表示当前类型(秒/分钟/小时/日/月/年)下数据不失效。
	 *  可选的范围：SECOND,MINUTE,HOUR,DAY,MONTH,YEAR
	 *  例：设置小时(BasicExpiration.HOUR)，则意味着当前小时下数据不会失效。该数据返回时间为16:38:22，那
	 *  么16:38:22~16:59:59是有效的，17:00:00开始计算失效时间。
	 * */
	CapitalExpiration capitalExpiration() default CapitalExpiration.SECOND;

	/**
	 * 备注
	 * */
	String remark() default "";

	/**
	 * 缓存null
	 * */
	boolean nullable() default true;
}
