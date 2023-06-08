package com.example.instrumentation;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

//import static sun.awt.windows.WKeyboardFocusManagerPeer.inst;
//import net.bytebuddy.agent.builder.AgentBuilder;
//import net.bytebuddy.implementation.MethodDelegation;
//import net.bytebuddy.matcher.ElementMatchers;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.stereotype.Controller;
//
//import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
//import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;

public class LoggingAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        try {
            ClassPool classPool = ClassPool.getDefault();
            for (Class<?> clazz : inst.getAllLoadedClasses()) {
                String className = clazz.getName();
                if (!className.startsWith("com.example.testlogger")) {
                    continue;
                }
                CtClass ctClass = classPool.get(className);
                for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
                    // Skip static and abstract methods
                    if (java.lang.reflect.Modifier.isStatic(ctMethod.getModifiers()) || java.lang.reflect.Modifier.isAbstract(ctMethod.getModifiers())) {
                        continue;
                    }
                    String methodName = ctMethod.getName();
                    String logStatement = "System.out.println(\"Method " + methodName + " executed.\");";
                    ctMethod.insertBefore(logStatement);
                }
                byte[] byteCode = ctClass.toBytecode();
                clazz.getProtectionDomain().getCodeSource().getLocation();
                inst.redefineClasses(new ClassDefinition(clazz, byteCode));
                ctClass.detach();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
