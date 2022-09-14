package love.kill.methodcache.annotation;


import java.lang.annotation.*;

/**
 * 删除缓存数据
 *
 * 被注解的方法调用成功后，清除ID对应的缓存
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Inherited
public @interface DeleteData {

	/**
	 * 缓存ID
	 *
	 * @return 缓存ID
	 */
	String[] id();
}
