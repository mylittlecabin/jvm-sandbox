package com.alibaba.jvm.sandbox.core.enhance;

import com.alibaba.jvm.sandbox.api.event.Event;
import com.alibaba.jvm.sandbox.core.enhance.weaver.asm.EventWeaver;
import com.alibaba.jvm.sandbox.core.util.AsmUtils;
import com.alibaba.jvm.sandbox.core.util.ObjectIDs;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ASM7;

/**
 * 事件代码增强器
 *
 * @author luanjia@taobao.com
 */
public class EventEnhancer implements Enhancer {

    private static final Logger logger = LoggerFactory.getLogger(EventEnhancer.class);
    private final String nativePrefix;

    public EventEnhancer(String nativePrefix) {
        this.nativePrefix = nativePrefix;
    }


    /**
     * 创建ClassWriter for asm
     *
     * @param cr ClassReader
     * @return ClassWriter
     */
    private ClassWriter createClassWriter(final ClassLoader targetClassLoader,
                                          final ClassReader cr) {
        return new ClassWriter(cr, COMPUTE_FRAMES | COMPUTE_MAXS) {

            /*
             * 注意，为了自动计算帧的大小，有时必须计算两个类共同的父类。
             * 缺省情况下，ClassWriter将会在getCommonSuperClass方法中计算这些，通过在加载这两个类进入虚拟机时，使用反射API来计算。
             * 但是，如果你将要生成的几个类相互之间引用，这将会带来问题，因为引用的类可能还不存在。
             * 在这种情况下，你可以重写getCommonSuperClass方法来解决这个问题。
             *
             * 通过重写 getCommonSuperClass() 方法，更正获取ClassLoader的方式，改成使用指定ClassLoader的方式进行。
             * 规避了原有代码采用Object.class.getClassLoader()的方式
             */
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return AsmUtils.getCommonSuperClass(type1, type2, targetClassLoader);
            }

        };
    }

    /**
     * 是否存储字节码到class文件
     */
    private static final boolean isDumpClass = true;
    /**
     *
     */
    private static final boolean isPrintOriginByteCode = true;
    /**
     * 是否打印字节码
     */
    private static final boolean isPrintNewByteCode = true;
    /**
     * 是否打印生成此字节码对应java asm代码
     */
    private static final boolean isPrintAsmCode = true;

    /**
     * 是否check class
     */
    private static final boolean isCheckClass = true;

    /*
     * dump class to file
     * 用于代码调试
     */
    private static byte[] dumpClassIfNecessary(String className, byte[] data) {
        if (!isDumpClass) {
            return data;
        }
        final File dumpClassFile = new File("./sandbox-class-dump/" + className + ".class");
        final File classPath = new File(dumpClassFile.getParent());

        // 创建类所在的包路径
        if (!classPath.mkdirs()
                && !classPath.exists()) {
            logger.warn("create dump classpath={} failed.", classPath);
            return data;
        }

        // 将类字节码写入文件
        try {
            writeByteArrayToFile(dumpClassFile, data);
            logger.info("dump {} to {} success.", className, dumpClassFile);
        } catch (IOException e) {
            logger.warn("dump {} to {} failed.", className, dumpClassFile, e);
        }

        return data;
    }

    @Override
    public byte[] toByteCodeArray(final ClassLoader targetClassLoader,
                                  final byte[] byteCodeArray,
                                  final Set<String> signCodes,
                                  final String namespace,
                                  final int listenerId,
                                  final Event.Type[] eventTypeArray) {
        // 返回增强后字节码
        final ClassReader cr = new ClassReader(byteCodeArray);
        final ClassWriter cw = createClassWriter(targetClassLoader, cr);
        final int targetClassLoaderObjectID = ObjectIDs.instance.identity(targetClassLoader);
        if(isPrintNewByteCode){
            //执行链路 EventWeaver -> tcv[asmCode] ->tcv[printByteCode] -> pw
            PrintWriter pw = new PrintWriter(System.out);//打印到控制台
            ClassVisitor tcv = new TraceClassVisitor(cw, pw);
            if(isPrintAsmCode){
                tcv = new TraceClassVisitor(tcv, new ASMifier(), pw);
            }
            if(isCheckClass){
                tcv = new CheckClassAdapter(tcv);
            }
            tcv = new EventWeaver(ASM7, tcv, namespace, listenerId,
                    targetClassLoaderObjectID,
                    cr.getClassName(),
                    signCodes,
                    eventTypeArray,
                    nativePrefix
            );
            if(isPrintOriginByteCode){
                tcv = new TraceClassVisitor(tcv,pw);
            }
            cr.accept(tcv
                    ,
                    EXPAND_FRAMES
            );
            pw.flush();
        }else {
            cr.accept(
                    new EventWeaver(ASM7, cw, namespace, listenerId,
                            targetClassLoaderObjectID,
                            cr.getClassName(),
                            signCodes,
                            eventTypeArray,
                            nativePrefix
                    ),
                    EXPAND_FRAMES
            );
        }
        return dumpClassIfNecessary(cr.getClassName(), cw.toByteArray());
    }

}
