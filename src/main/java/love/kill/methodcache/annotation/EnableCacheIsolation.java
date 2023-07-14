package love.kill.methodcache.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 开启缓存隔离
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EnableCacheIsolation {

	/**
	 * 隔离策略
	 * N：不隔离，T：线程隔离，默认 N
	 */
	char isolationStrategy() default 'N';
}
