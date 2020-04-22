package net.corda.djvm.rules.implementation

import net.corda.djvm.code.*
import net.corda.djvm.code.instructions.MemberAccessInstruction
import org.objectweb.asm.Opcodes.*

/**
 * Some non-deterministic APIs belong to whitelisted classes and so cannot be stubbed out.
 * Replace their invocations with safe alternatives, e.g. throwing an exception.
 */
object DisallowNonDeterministicMethods : Emitter {

    private val ALLOWED_GETTERS = setOf(
        "getConstructor",
        "getConstructors",
        "getEnclosingConstructor",
        "getMethod",
        "getMethods",
        "getEnclosingMethod"
    )
    private val FORBIDDEN_METHODS = setOf(
        "getDeclaredClasses",
        "getProtectionDomain"
    )
    private val CLASSLOADING_METHODS = setOf("defineClass", "findClass")
    private val NEW_INSTANCE_CLASSES = setOf(
        "sun/security/x509/CertificateExtensions",
        "sun/security/x509/CRLExtensions",
        "sun/security/x509/OtherName"
    )

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        val className = (context.member ?: return).className
        if (instruction is MemberAccessInstruction && instruction.isMethod) {
            when (instruction.operation) {
                INVOKEVIRTUAL, INVOKESPECIAL ->
                    if (isObjectMonitor(instruction)) {
                        forbid(instruction)
                    } else {
                        when (Enforcer(instruction).runFor(className)) {
                            Choice.FORBID -> forbid(instruction)
                            Choice.LOAD_CLASS -> loadClass()
                            Choice.INIT_CLASSLOADER -> initClassLoader()
                            Choice.GET_PARENT, Choice.GET_PACKAGE -> returnNull(POP)
                            Choice.NO_RESOURCE -> returnNull(POP2)
                            Choice.EMPTY_RESOURCES -> emptyResources(POP2)
                            else -> Unit
                        }
                    }

                INVOKESTATIC ->
                    if (instruction.className == CLASSLOADER_NAME) {
                        when {
                            instruction.memberName == "getSystemClassLoader" -> {
                                invokeStatic(DJVM_NAME, instruction.memberName, instruction.descriptor)
                                preventDefault()
                            }
                            instruction.memberName == "getSystemResources" -> emptyResources(POP)
                            instruction.memberName.startsWith("getSystemResource") -> returnNull(POP)
                            else -> forbid(instruction)
                        }
                    }
            }
        }
    }

    private fun EmitterModule.forbid(instruction: MemberAccessInstruction) {
        throwRuleViolationError("Disallowed reference to API; ${formatFor(instruction)}")
        preventDefault()
    }

    private fun EmitterModule.loadClass() {
        invokeStatic(DJVM_NAME, "loadClass", "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/lang/Class;")
        preventDefault()
    }

    private fun EmitterModule.initClassLoader() {
        invokeStatic(DJVM_NAME, "getSystemClassLoader", "()Ljava/lang/ClassLoader;")
        invokeSpecial(CLASSLOADER_NAME, CONSTRUCTOR_NAME, "(Ljava/lang/ClassLoader;)V")
        preventDefault()
    }

    private fun EmitterModule.returnNull(popCode: Int) {
        instruction(popCode)
        pushNull()
        preventDefault()
    }

    private fun EmitterModule.emptyResources(popCode: Int) {
        instruction(popCode)
        invokeStatic("sandbox/java/util/Collections", "emptyEnumeration", "()Lsandbox/java/util/Enumeration;")
        preventDefault()
    }

    private fun isObjectMonitor(instruction: MemberAccessInstruction): Boolean
        = isObjectMonitor(instruction.memberName, instruction.descriptor)

    private enum class Choice {
        FORBID,
        LOAD_CLASS,
        INIT_CLASSLOADER,
        GET_PACKAGE,
        GET_PARENT,
        NO_RESOURCE,
        EMPTY_RESOURCES,
        PASS
    }

    private class Enforcer(private val instruction: MemberAccessInstruction) {
        private val isClassLoader: Boolean = instruction.className == "java/lang/ClassLoader"
        private val isClass: Boolean = instruction.className == CLASS_NAME
        private val isLoadClass: Boolean = instruction.memberName == "loadClass"

        private val hasClassReflection: Boolean get() = isClass
            && instruction.descriptor.contains("Ljava/lang/reflect/")
            && instruction.memberName !in ALLOWED_GETTERS

        private val isNewInstance: Boolean get() = instruction.className == "java/lang/reflect/Constructor"
            && instruction.memberName == "newInstance"

        fun runFor(className: String): Choice = when {
            isClassLoader && instruction.memberName == CONSTRUCTOR_NAME -> if (instruction.descriptor == "()V") {
                Choice.INIT_CLASSLOADER
            } else {
                Choice.FORBID
            }
            isClassLoader && instruction.memberName == "getParent" -> Choice.GET_PARENT
            isClassLoader && instruction.memberName == "getResources" -> Choice.EMPTY_RESOURCES
            isClassLoader && instruction.memberName.startsWith("getResource") -> Choice.NO_RESOURCE
            isClass && instruction.memberName == "getPackage" -> Choice.GET_PACKAGE
            isClass && instruction.memberName in FORBIDDEN_METHODS -> Choice.FORBID

            className == "java/security/Provider\$Service" -> allowLoadClass()

            isNewInstance -> forbidNewInstance(className)

            // Forbid reflection otherwise.
            hasClassReflection -> Choice.FORBID

            else -> allowLoadClass()
        }

        private fun forbidNewInstance(className: String): Choice = when(className) {
            in NEW_INSTANCE_CLASSES -> Choice.PASS
            else -> Choice.FORBID
        }

        private fun allowLoadClass(): Choice = when {
            !isClassLoader -> Choice.PASS
            isLoadClass -> when (instruction.descriptor) {
                "(Ljava/lang/String;)Ljava/lang/Class;" -> Choice.LOAD_CLASS
                else -> Choice.FORBID
            }
            instruction.memberName in CLASSLOADING_METHODS -> Choice.FORBID
            else -> Choice.PASS
        }
    }
}
