package com.shaovi.monitorx.gradle.okhttp

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter

class OkHttpMethodAdapter(methodVisitor: MethodVisitor?, access: Int, name: String?, descriptor: String?) : AdviceAdapter(Opcodes.ASM7, methodVisitor, access, name, descriptor) {

    override fun onMethodEnter() {
        super.onMethodEnter()
        mv?.let {
            // 注入 Monitor 拦截器，并保留已有的 EventListener.Factory
            it.visitFieldInsn(GETSTATIC, "com/shaovi/monitorx/MonitorHelper", "INSTANCE", "Lcom/shaovi/monitorx/MonitorHelper;")
            it.visitVarInsn(ALOAD, 0)
            it.visitFieldInsn(GETFIELD, "okhttp3/OkHttpClient\$Builder", "interceptors", "Ljava/util/List;")
            it.visitLdcInsn(PluginHelper.insertMode())
            it.visitLdcInsn(PluginHelper.anchorInterceptorClassName())
            it.visitVarInsn(ALOAD, 0)
            it.visitVarInsn(ALOAD, 0)
            it.visitFieldInsn(GETFIELD, "okhttp3/OkHttpClient\$Builder", "eventListenerFactory", "Lokhttp3/EventListener\$Factory;")
            it.visitMethodInsn(
                INVOKEVIRTUAL,
                "com/shaovi/monitorx/MonitorHelper",
                "installHookInterceptors",
                "(Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Lokhttp3/OkHttpClient\$Builder;Lokhttp3/EventListener\$Factory;)V",
                false
            )
        }
    }

    override fun onMethodExit(opcode: Int) {
        if (opcode == ARETURN) {
            // Keep the returned client on the operand stack while handing a copy to MonitorX.
            mv.visitInsn(DUP)
            mv.visitMethodInsn(
                INVOKESTATIC,
                "com/shaovi/monitorx/MonitorHelper",
                "captureBuiltClient",
                "(Lokhttp3/OkHttpClient;)V",
                false
            )
        }
        super.onMethodExit(opcode)
    }
}
