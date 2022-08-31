package love.kill.methodcache.util;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public class DataUtil {

	public static int getArgsHashCode(Object[] args) {

		Map<String, Integer> fieldHash = new LinkedHashMap<>();
		try {
			for (int i = 0; i < args.length; i++) {
				Object arg = args[i];
				fieldHash.put("arg" + i, doGetHash(arg));
			}
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

		return fieldHash.hashCode();
	}

	private static int doGetHash(Object arg) throws IllegalAccessException {

		if (arg == null) {
			return 0;
		}

		if (isPrimitive(arg.getClass())) {
			// 基本数据类型
			return Objects.hash(arg);

		} else {
			// 复杂对象类型
			Field[] declaredFields = arg.getClass().getDeclaredFields();

			for (Field field : declaredFields) {
				field.setAccessible(true);
			}

			List<Field> fieldList = new LinkedList<>(Arrays.asList(declaredFields));

			// 按属性排序
			fieldList.sort(Comparator.comparing(Field::toString));

			Map<String, Integer> fieldHash = new LinkedHashMap<>();
			for (Field field : fieldList) {
				fieldHash.put(field.toString(), doGetFieldHash(field, arg));
			}
			return Objects.hash(fieldHash);
		}
	}


	private static int doGetFieldHash(Field field, Object object) throws IllegalAccessException {

		if (object == null) {
			return 0;
		}

		Class<?> typeClass = field.getType();
		Object fieldObject = field.get(object);


		if (isPrimitive(typeClass) || (typeClass.isAssignableFrom(Collection.class))) {
			// 基本数据类型
			return Objects.hash(fieldObject);

		} else {
			// 复杂对象类型
			Field[] declaredFields = typeClass.getDeclaredFields();

			for (Field innerField : declaredFields) {
				innerField.setAccessible(true);
			}

			List<Field> fieldList = new LinkedList<>(Arrays.asList(declaredFields));

			// 按属性排序
			fieldList.sort(Comparator.comparing(Field::toString));


			Map<String, Integer> fieldHash = new LinkedHashMap<>();
			for (Field innerField : fieldList) {
				fieldHash.put(innerField.toString(), doGetFieldHash(innerField, fieldObject));
			}
			return Objects.hash(fieldHash);
		}
	}

	private static boolean isPrimitive(Class clazz) {
		return clazz.isPrimitive() || isInternal(clazz);
	}

	private static boolean isInternal(Class clazz) {
		return (Map.class == clazz) ||
				(List.class == clazz) ||
				(String.class == clazz) ||
				(Short.class == clazz) ||
				(Integer.class == clazz) ||
				(Long.class == clazz) ||
				(Float.class == clazz) ||
				(Double.class == clazz) ||
				(Character.class == clazz) ||
				(Boolean.class == clazz);
	}

	public static int hash(Object key) {
		int h;
		return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
	}

}
