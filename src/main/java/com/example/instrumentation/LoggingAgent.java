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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoggingAgent {
    private static final Map<String, List<String>> controlFlowPaths = new HashMap<>();

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
                            addLogStatement(method, ctClass);  // adding logs
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
            String logMethodEntry = String.format("org.slf4j.LoggerFactory.getLogger(%s.class).info(\"Entering method: %s\");", declaringClass.getName(), methodName);
            method.insertBefore(logMethodEntry);
        }
    }

    private static void analyzeControlFlowPaths(CtMethod method) {
        try {
            MethodInfo methodInfo = method.getMethodInfo();
            CodeAttribute codeAttribute = methodInfo.getCodeAttribute();

            if (codeAttribute == null) {
                return; // Skip methods without code (e.g., abstract, native)
            }

            CodeIterator codeIterator = codeAttribute.iterator();
            List<Integer> branchPositions = new ArrayList<>();
            List<String> executingMethods = new ArrayList<>();
            while (codeIterator.hasNext()) {
                int position = codeIterator.next();
                int opcode = codeIterator.byteAt(position) & 0xFF;

                if (Mnemonic.OPCODE[opcode].startsWith("if")) {
                    branchPositions.add(position);
                } else if (Mnemonic.OPCODE[opcode] == "invokevirtual" || Mnemonic.OPCODE[opcode] == "invokeinterface") {
                    int index = codeIterator.u16bitAt(position + 1);
                    ConstPool constPool = codeAttribute.getConstPool();
                    String className = constPool.getMethodrefClassName(index);
                    String methodName = constPool.getMethodrefName(index);
                    String executingMethod = className + "." + methodName;
                    executingMethods.add(executingMethod);
                }
            }

            if (!branchPositions.isEmpty()) {
                traverseControlFlowPaths(branchPositions, 0, new boolean[branchPositions.size()], method, executingMethods, new ArrayList<>());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void traverseControlFlowPaths(List<Integer> branchPositions, int currentIndex, boolean[] path, CtMethod method, List<String> executingMethods, List<Boolean> branchDecisions) {
        if (currentIndex == branchPositions.size()) {
            printControlFlowPath(path, method, executingMethods, branchDecisions);
            return;
        }

        // Traverse the 'true' branch
        path[currentIndex] = true;
        branchDecisions.add(true);
        traverseControlFlowPaths(branchPositions, currentIndex + 1, path, method, executingMethods, branchDecisions);

        // Traverse the 'false' branch
        path[currentIndex] = false;
        branchDecisions.set(currentIndex, false);
        traverseControlFlowPaths(branchPositions, currentIndex + 1, path, method, executingMethods, branchDecisions);

        // Remove the last path entry if no further branches were traversed
        if (currentIndex == branchPositions.size() - 1 && executingMethods.size() > branchPositions.size()) {
            executingMethods.remove(executingMethods.size() - 1);
            branchDecisions.remove(branchDecisions.size() - 1);
        }
    }

    private static void printControlFlowPath(boolean[] path, CtMethod method, List<String> executingMethods, List<Boolean> branchDecisions) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (int i = 0; i < path.length; i++) {
            boolean branch = path[i];
            String chosenPath;
            if (branch) {
                chosenPath = "if";
            } else {
                boolean nextBranch = false;
                for (int j = i + 1; j < path.length; j++) {
                    if (path[j]) {
                        nextBranch = true;
                        break;
                    }
                }
                chosenPath = nextBranch ? "else-if" : "else";
            }

            String conditionalBranch = branchDecisions.get(i) ? "true" : "false";

            // Check if the index is valid before accessing the executingMethods list
            if (i < executingMethods.size()) {
                String executingMethod = executingMethods.get(i);

                if (!first) {
                    sb.append(" > ");
                } else {
                    first = false;
                }

                sb.append(chosenPath).append(" (").append(conditionalBranch).append(") > ").append("MethodName: ").append(executingMethod);
            }
        }

        sb.append(" > end");

        String methodName = method.getName();
        String declaringClassName = method.getDeclaringClass().getSimpleName();
        String logMessage = String.format("ClassName: %s > %s > %s", declaringClassName, methodName, sb.toString());

        try {
            // Insert the log message after the method execution
            method.insertAfter(String.format("org.slf4j.LoggerFactory.getLogger(%s.class).info(\"%s\");", method.getDeclaringClass().getName(), logMessage));
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }
    }

}
