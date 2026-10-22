package com.hkjustar.plugin.okhttp

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class OkHttpClassVisitor(nextVisitor: ClassVisitor) : ClassVisitor(
    Opcodes.ASM7, nextVisitor) {

    private var className: String? = null

    override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
        super.visit(version, access, name, signature, superName, interfaces)
        this.className = name
    }

    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        val methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions)
        return if (className == "okhttp3/OkHttpClient\$Builder" && name == PluginHelper.targetBuilderMethodName()) {
            if (methodVisitor == null) null else OkHttpMethodAdapter(methodVisitor, access, name, descriptor)
        } else {
            methodVisitor
        }
    }
}
