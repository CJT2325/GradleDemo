package com.cjt.gradle;

import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Type;
import groovyjarjarasm.asm.tree.MethodNode;

import static groovyjarjarasm.asm.Opcodes.*;

public class LogcatVisitor extends ClassVisitor {
    static final String UTIL_CLASS = "com/cjt/gradleplugin/LogUtil";
    private boolean injectUtil;//标记正在对Util类进行注入，还是对调用者进行注入
    private String simpleClassName;

    public LogcatVisitor(ClassVisitor cv) {
        super(ASM5, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        System.out.println("Doing logcat injection on class " + name);
        //这里的name是当前类名
        if (name.equals(UTIL_CLASS)) {
            injectUtil = true;
        } else {
            simpleClassName = name.substring(name.lastIndexOf('/') + 1);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv;
        if (injectUtil && name.length() == 1) {//这里是对Util类的操作
            //super调用结果用于返回。即保留原有的方法。
            mv = super.visitMethod(access, name, desc, signature, exceptions);

            //给方法描述符在开头添加一个参数。这个描述符用于调用android/util/Log.*，我们和它恰好相差一个tag参数。
            String targetDesc = "(Ljava/lang/String;" + desc.substring(1);
            int i = desc.indexOf(')');
            //这里是给方法添加最后一个参数为String。这个描述符用于生成影子方法。
            desc = desc.substring(0, i) + "Ljava/lang/String;" + desc.substring(i);

            //MethodNode用于生成影子方法
            MethodNode mn = new MethodNode();
            mn.visitCode();//方法开始
            int localVarIndex = Type.getArgumentTypes(targetDesc).length - 1;
            mn.visitVarInsn(ALOAD, localVarIndex);//我们引入的最后一个参数是tag，但调用android/util/Log.*的第一个参数是tag，需要最先入栈。
            for (int j = 0; j < localVarIndex; j++) {//其余参数依次入栈。
                mn.visitVarInsn(ALOAD, j);
            }
            //调用android/util/Log中的方法
            mn.visitMethodInsn(INVOKESTATIC, "android/util/Log", name, targetDesc, false);
            //android/util/Log返回的值直接返回出去
            mn.visitInsn(IRETURN);
            mn.visitMaxs(0, 0);
            mn.visitEnd();
            //影子方法添加到类中
            mn.accept(cv.visitMethod(access, name, desc, signature, exceptions));
        } else {//这里是对调用者的操作
            mv = super.visitMethod(access, name, desc, signature, exceptions);
            //这里我们修改了MethodVisitor再返回，即修改了这个方法
            mv = new MethodVisitor(ASM5, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    //覆盖方法调用的过程
                    if (opcode == INVOKESTATIC && owner.equals(UTIL_CLASS)) {
                        //发现在调用LogUtil中的方法。接下来我们要入栈一个字符串，并替换为影子方法。
                        mv.visitLdcInsn(simpleClassName);//入栈类名
                        int i = desc.indexOf(')');
                        desc = desc.substring(0, i) + "Ljava/lang/String;" + desc.substring(i);//方法描述符替换为影子方法
                    }
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            };
        }
        return mv;
    }
}