package com.eureka.railway.aspect;

import org.aspectj.lang.ProceedingJoinPoint;


import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Spring AOP: logs every controller method call and how long it took.
// @Slf4j generates the private static final Logger named "log".
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("execution(* com.eureka.railway.controller..*(..))")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String method = joinPoint.getSignature().toShortString();
        try {
            Object result = joinPoint.proceed();
            log.info("{} completed in {} ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            log.warn("{} failed: {}", method, t.getMessage());
            throw t;
        }
    }
}
