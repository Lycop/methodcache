package love.kill.methodcache.annotation;


import java.lang.annotation.*;

/**
 * 缓存数据
 *
 * 被注解的方法，会根据请求参数(hashcode)进行匹配，匹配成功返回缓存数据
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Inherited
public @interface CacheData {

	/**
	 * 缓存ID
	 * 标识缓存，支持根据id清除此类缓存。{@link love.kill.methodcache.controller.Cache#delete}
	 *
	 * @return 缓存ID
	 */
	String id() default "";

	/**
	 * 刷新数据
	 * 每次请求都刷新缓存，true 则在返回数据后，异步请求并更新数据
	 *
	 * @return 本次请求是否刷新数据
	 */
	boolean refresh() default true;

	/**
	 * 数据过期时间(毫秒)
	 * 数据将会在指定时间过期，小于0表示不会过期
	 *
	 * @return 本次请求缓存数据的过期时间
	 */
	long expiration() default 30000L;

	/**
	 * 宽限期，毫秒
	 * 数据宽限过期时间，默认为0(L)。如果此值大于0，则数据会在{@link #expiration()}基础上进行随机累加。
	 * 一般情况下，此值可以用于避免数据同时失效引起的缓存雪崩。如：
	 * 过期时间为30000毫秒，过期宽限期为10000毫秒，则数据会在30000～40000之间随机一个值过期。
	 *
	 * @return 数据过期宽限期
	 */
	long behindExpiration() default 0L;

	/**
	 *  过期基础时间
	 *  设置一个日期类型，作为数据过期计算的基础时间进行累加，表示当前(秒/分钟/小时/日/月/年)下数据不失效。
	 *  可选的范围：
	 *  			秒({@link love.kill.methodcache.annotation.CapitalExpiration#SECOND})
	 *  			分钟({@link love.kill.methodcache.annotation.CapitalExpiration#MINUTE})
	 *  			小时({@link love.kill.methodcache.annotation.CapitalExpiration#HOUR})
	 *  			天({@link love.kill.methodcache.annotation.CapitalExpiration#DAY})
	 *  			月({@link love.kill.methodcache.annotation.CapitalExpiration#MONTH})
	 *  			年({@link love.kill.methodcache.annotation.CapitalExpiration#YEAR})
	 *
	 *  例如：小时，则意味着当前小时下数据不会失效。该数据返回时间为16:38:22，那么16:38:22~16:59:59是有
	 *  效的，17:00:00开始计算失效时间。
	 *
	 *	@return 数据过期的基础时间
	 */
	CapitalExpiration capitalExpiration() default CapitalExpiration.SECOND;

	/**
	 * 备注
	 *
	 * @return 缓存备注
	 */
	String remark() default "";

	/**
	 * 缓存null
	 * 默认为true，当请求结果返回为null依旧缓存；否则不缓存
	 *
	 * @return 是否缓存null
	 */
	boolean nullable() default true;
}
