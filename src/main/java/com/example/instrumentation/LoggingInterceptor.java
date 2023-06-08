//package com.example.instrumentation;
//
//import net.bytebuddy.implementation.bind.annotation.*;
//
//import java.lang.reflect.Method;
//import java.util.concurrent.Callable;
//
//public class LoggingInterceptor {
//    @RuntimeType
//    public static Object intercept(@This Object target,
//                                   @AllArguments Object[] arguments,
//                                   @SuperCall Callable<?> superMethod,
//                                   @Origin Method method) throws Exception {
//        System.out.println("Entering method: " + method.getName());
//
//        try {
//            // Invoke the original method
//            Object result = superMethod.call();
//            System.out.println("Exiting method: " + method.getName());
//            return result;
//        } catch (Exception e) {
//            System.err.println("Exception in method: " + method.getName());
//            throw e;
//        }
//    }
//}