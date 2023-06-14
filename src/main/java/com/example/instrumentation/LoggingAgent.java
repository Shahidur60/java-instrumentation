package com.example.instrumentation;

import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class LoggingAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        ClassPool pool = ClassPool.getDefault();
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className.startsWith("com/example/testlogger")) {
                    try {
                        CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
                        CtMethod[] methods = ctClass.getDeclaredMethods();

                        for (CtMethod method : methods) {
                            addLogStatement(method, ctClass);
                        }

                        return ctClass.toBytecode();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        };

        inst.addTransformer(transformer);
    }

    private static void addLogStatement(CtMethod method, CtClass declaringClass) throws CannotCompileException {
        if (!method.isEmpty()) {
            String methodName = method.getName();
            String logStatement = String.format("org.slf4j.LoggerFactory.getLogger(%s.class).info(\"Entering method: %s\");", declaringClass.getName(), methodName);
            method.insertBefore(logStatement);
        }
    }
}
