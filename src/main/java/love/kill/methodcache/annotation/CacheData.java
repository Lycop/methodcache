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
	 * false(默认)，表示仅在缓存未命中或失效后，发起请求时缓存；true 则表示每次请求返回数据后，均以异步的方式发起请求并刷新缓存数据；
	 *
	 * @return 每次请求是否刷新数据
	 */
	boolean refresh() default false;

	/**
	 * 数据过期时间(毫秒)
	 * 数据将会在指定时间过期，小于0(L)表示不会过期
	 *
	 * @return 本次请求缓存数据的过期时间
	 */
	long expiration() default 30000L;

	/**
	 * 宽限期(毫秒)
	 * 数据宽限过期时间，默认为0(L)。如果此值大于0，则数据会在{@link #expiration()}基础上进行随机累加。
	 * 一般情况下，此值可以用于避免数据同时失效引起的缓存雪崩。如：
	 * 		过期时间为30000毫秒(expiration=30000)，过期宽限期为10000毫秒(behindExpiration=10000)，则数据会
	 *	在 30000 ～ 40000 之间随机一个值过期。
	 *
	 * @return 数据过期宽限期
	 */
	long behindExpiration() default 0L;

	/**
	 * 过期基础时间
	 * 设置一个日期类型，作为数据过期计算的基础时间，表示当前(秒/分钟/小时/日/月/年)下数据不失效。
	 * 可选的范围：
	 *  	秒({@link love.kill.methodcache.annotation.CapitalExpiration#SECOND})
	 *  	分钟({@link love.kill.methodcache.annotation.CapitalExpiration#MINUTE})
	 *  	小时({@link love.kill.methodcache.annotation.CapitalExpiration#HOUR})
	 *  	天({@link love.kill.methodcache.annotation.CapitalExpiration#DAY})
	 *  	月({@link love.kill.methodcache.annotation.CapitalExpiration#MONTH})
	 *  	年({@link love.kill.methodcache.annotation.CapitalExpiration#YEAR})
	 *
	 * 例如：
	 *  	假设当前时间为16:38:22，过期基础时间为"小时"(capitalExpiration=CapitalExpiration.HOUR)，数据过期时间
	 *  为30000毫秒(expiration=30000)。那么数据在 17:00:00(过期基础时间)前不会失效，又因为数据过期时间为30000毫秒，
	 *  因此，实际的数据过期时间为：17:00:30。
	 *
	 *	@return 数据过期的基础时间
	 */
	CapitalExpiration capitalExpiration() default CapitalExpiration.SECOND;

	/**
	 * 缓存"null"结果
	 * 默认为true，请求结果返回为"null"时依旧缓存；为 false 时表示不缓存。
	 *
	 * @return 是否缓存null
	 */
	boolean nullable() default true;

	/**
	 * 共享缓存数据
	 *
	 * false(默认)时，缓存命中后返回的数据是个对象，那么该对象是线程独享的，修改对象的数据不影响其他线程。当然，内存占用率也较高；
	 * 为 true 时，缓存命中后返回的数据(如果是个对象)则是共享的，修改数据会影响其他的线程得到的数据，内存占用率较低。
	 *
	 * @return 缓存数据为共享
	 * */
	boolean shared() default false;

	/**
	 * 备注
	 *
	 * @return 缓存备注
	 */
	String remark() default "";

}
