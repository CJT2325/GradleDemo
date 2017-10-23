package com.cjt.gradle

import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter;

import java.util.Set;

public class LogTransform extends Transform {
    @Override
    String getName() {//这个名称会用于生成的gradle task名称
        return "ASMPlugin"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        //接受输入的类型。我们这里只处理Java类。
        return [QualifiedContent.DefaultContentType.CLASSES]
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        //作用范围。这里我们只处理工程中编写的类。
        return [QualifiedContent.Scope.PROJECT]
    }

    @Override
    boolean isIncremental() {//是否支持增量。这里暂时不实现增量。
        return false
    }

    @Override
    void transform(TransformInvocation ti) throws TransformException, InterruptedException, IOException {
        TransformOutputProvider outputProvider = ti.outputProvider
        //获取输出路径
        def outDir = outputProvider.getContentLocation("inject", outputTypes, scopes, Format.DIRECTORY)

        outDir.deleteDir()
        outDir.mkdirs()

        ti.inputs.each {
            //directoryInputs就是class文件所在的目录。如果需要处理jar文件，那么也要对it.jarInputs进行处理。本文中不需要。
            it.directoryInputs.each {
                int pathBitLen = it.file.toString().length()

                it.file.traverse {
                    if (!it.isDirectory()) {
                        File file = it
                        File outputFile = new File(outDir, "${file.toString().substring(pathBitLen)}")
                        System.out.println(file.toString())
                        outputFile.getParentFile().mkdirs()
                        byte[] classFileBuffer = file.bytes
                        //doTransfer待实现
                        outputFile.bytes = doTransfer(classFileBuffer)
                    }
                }
            }
        }
    }
    private static byte[] doTransfer(byte[] input) {
//        System.out.println("=============================================")
        String s = new String(input)

        int flags = 0
        if (s.contains(LogcatVisitor.UTIL_CLASS)) {//UTIL_CLASS="com/xxx/LogUtil"
            //一个小trick：由于被调用的方法和类会被放入常量池，我们可以直接忽略字节码中不含有字符串"com/xxx/LogUtil"的文件
            flags = 1
        }
        if (flags) {
            ClassReader reader = new ClassReader(input)
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS)//COMPUTE_MAXS会让我们免于手动计算局部变量数和方法栈大小
            ClassVisitor cv = writer
            //LogcatVisitor会承载我们核心的修改字节码的任务
            cv = new LogcatVisitor(cv)
            reader.accept(cv, 8)
            return writer.toByteArray()
        } else { //无需转换
            return input
        }
    }
}
