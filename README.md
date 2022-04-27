**一、什么是MethodCache**

    MethodCache是一个基于Spring Boot的、非侵入式的方法缓存开源组件。用于对指定方法数据进行缓存，可以提高网站的访问速度。

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