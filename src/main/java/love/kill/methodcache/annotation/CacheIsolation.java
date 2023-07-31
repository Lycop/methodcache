package love.kill.methodcache.annotation;


import java.lang.annotation.*;

/**
 * 缓存隔离
 *
 * 方法内部缓存的数据，仅会在该方法及子方法内部可见
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CacheIsolation {

	/**
	 * 隔离策略
	 * N：不隔离，T：线程隔离，默认 N
	 *
	 * @return 隔离策略
	 */
	char isolationStrategy() default 'N';

}
