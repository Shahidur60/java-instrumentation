package com.example.instrumentation;

import javassist.*;
import javassist.bytecode.*;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoggingAgent {

    //static Logger logger = org.slf4j.LoggerFactory.getLogger(LoggingAgent.class);
    public static void premain(String agentArgs, Instrumentation inst) {
        ClassPool pool = ClassPool.getDefault();
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className.startsWith("com/example/testlogger")) {
                    try {
                        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        CtMethod[] methods = ctClass.getDeclaredMethods();

                        for (CtMethod method : methods) {
                            addLogStatement(method, ctClass);  /// adding logs

                            // Analyze control-flow graph paths
                            analyzeControlFlowPaths(method);
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

    private static void analyzeControlFlowPaths(CtMethod method) {
        System.out.println("in the flow path method");

        try {
            MethodInfo methodInfo = method.getMethodInfo();
            CodeAttribute codeAttribute = methodInfo.getCodeAttribute();

            if (codeAttribute == null) {
                return; // Skip methods without code (e.g., abstract, native)
            }

            CodeIterator codeIterator = codeAttribute.iterator();
            List<Integer> branchPositions = new ArrayList<>();
            System.out.println("Length of codeIterator " + codeIterator.getCodeLength());
            while (codeIterator.hasNext()) {
                System.out.println("i am in loop");
                int position = codeIterator.next();
                int opcode = codeIterator.byteAt(position) & 0xFF;

                if (Mnemonic.OPCODE[opcode].startsWith("if")) {
                    branchPositions.add(position);
                } else if (Mnemonic.OPCODE[opcode].startsWith("invoke")) {
                    int index = codeIterator.u16bitAt(position + 1);
                    String methodRefType = methodInfo.getConstPool().getMethodrefType(index);
                    CtClass[] parameterTypes = Descriptor.getParameterTypes(methodRefType, method.getDeclaringClass().getClassPool());
                    CtClass returnType = Descriptor.getReturnType(methodRefType, method.getDeclaringClass().getClassPool());

                    // Skip methods with void return type
                    if (returnType.equals(CtClass.voidType)) {
                        continue;
                    }

                    CtMethod[] declaredMethods = method.getDeclaringClass().getDeclaredMethods();
                    for (CtMethod declaredMethod : declaredMethods) {
                        if (declaredMethod.getName().equals(methodInfo.getConstPool().getMethodrefName(index))
                                && Arrays.equals(declaredMethod.getParameterTypes(), parameterTypes)) {
                            analyzeControlFlowPaths(declaredMethod);
                            break;
                        }
                    }
                }
            }

            // Perform depth-first traversal to explore all paths
            traverseControlFlowPaths(branchPositions, 0, new boolean[branchPositions.size() + 1]);

            // Update the modified code
            method.getMethodInfo().rebuildStackMapIf6(method.getDeclaringClass().getClassPool(), method.getDeclaringClass().getClassFile());
            method.getDeclaringClass().getClassFile().compact();
            System.out.println("Finished try block");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void traverseControlFlowPaths(List<Integer> branchPositions, int currentIndex, boolean[] path) {
        if (currentIndex == branchPositions.size()) {
            printControlFlowPath(path);
            return;
        }

        // Traverse the 'true' branch
        path[currentIndex] = true;
        traverseControlFlowPaths(branchPositions, currentIndex + 1, path);

        // Traverse the 'false' branch
        path[currentIndex] = false;
        traverseControlFlowPaths(branchPositions, currentIndex + 1, path);
    }

    private static void printControlFlowPath(boolean[] path) {
        StringBuilder sb = new StringBuilder();

        for (boolean branch : path) {
            sb.append(branch ? "true -> " : "false -> ");
        }

        sb.append("end");
        String logMessage = "Control-flow path: " + sb.toString();
        org.slf4j.LoggerFactory.getLogger(LoggingAgent.class).info(logMessage);
    }
}
