package love.kill.methodcache.annotation;


import java.lang.annotation.*;

/**
 * 缓存隔离
 *
 * 方法内部缓存的数据仅在该方法内部可见
 *
 * 注：需在方法所在类中开启{@link love.kill.methodcache.annotation.EnableCacheIsolation#isolationStrategy}
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CacheIsolation {

}
