**一、什么是MethodCache**

    MethodCache是一个基于Spring Boot的、非侵入式的方法缓存开源组件。对方法返回数据进行缓存，减少数据库访问次数，以提高接口的访问速度。

**二、快速开始**

    1、引入jar包
        <dependency>
            <groupId>love.kill</groupId>
            <artifactId>methodcache-spring-boot-starter</artifactId>
            <version>2.0.0</version>
        </dependency>

    2、在 application.yml 启用
        methodcache:
          enable: true

    3、在方法上加上注解 @CacheData

**三、demo**

    内存：https://github.com/Lycop/demo-for-methodcache-with-memory.git
    Redis：https://github.com/Lycop/demo-for-methodcache-with-redis.git


**四、缓存存储方式**

    通过 methodcache.cache-type 属性来指定缓存的存储方式。可选值：(M)emory、(R)edis，默认M。


**五、缓存属性**

    1、expiration，缓存时间(默认3000毫秒)。此属性表示缓存有效时间，在有效时间内，数据将会返回缓存中的数据；否则发起一次请求。
    2、refresh，刷新(默认true)。方法被请求后，都会发起一次请求并更新缓存。
    3、behindExpiration，宽限期。此属性表示缓存到期后，会在指定时间内随机失效。可以用来避免缓存大范围同时失效引起的服务器雪崩。
    4、capitalExpiration，基础时间。此属性表示在当前指定类型(秒/分钟/小时/日/月/年)内，缓存一直有效。
    5、id，缓存标识。标识一个缓存注解，可用于清除该注解的缓存。
    6、nullable，null认为有效(默认true)。为true表示当被注解的方法返回了 null(包括异常导致) 时，仍缓存该值。
    7、remark，备注。


**六、查看/清除缓存**

    /methodcache/cache，支持方法：GET(查看缓存)、DELETE(清除缓存)
    /methodcache/cache/all，支持方法：DELETE(清除所有缓存)