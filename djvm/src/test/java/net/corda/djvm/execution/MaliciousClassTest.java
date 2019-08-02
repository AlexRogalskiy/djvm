package net.corda.djvm.execution;

import net.corda.djvm.TestBase;
import net.corda.djvm.WithJava;
import net.corda.djvm.rewiring.SandboxClassLoadingException;
import net.corda.djvm.rules.RuleViolationError;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.util.function.Function;

import static net.corda.djvm.SandboxType.JAVA;
import static net.corda.djvm.messages.Severity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MaliciousClassTest extends TestBase {
    MaliciousClassTest() {
        super(JAVA);
    }

    @Test
    void testImplementingToDJVMString() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            Throwable ex = assertThrows(SandboxClassLoadingException.class, () -> WithJava.run(executor, EvilToString.class, ""));
            assertThat(ex)
                .hasMessageContaining("Class is not allowed to implement toDJVMString()");
            return null;
        });
    }

    public static class EvilToString implements Function<String, String> {
        @Override
        public String apply(String s) {
            return toString();
        }

        @SuppressWarnings("unused")
        public String toDJVMString() {
            throw new IllegalStateException("Victory is mine!");
        }
    }

    @Test
    void testImplementingFromDJVM() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<Object, Object> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            Throwable ex = assertThrows(SandboxClassLoadingException.class, () -> WithJava.run(executor, EvilFromDJVM.class, null));
            assertThat(ex)
                .hasMessageContaining("Class is not allowed to implement fromDJVM()");
            return null;
        });
    }

    public static class EvilFromDJVM implements Function<Object, Object> {
        @Override
        public Object apply(Object obj) {
            return this;
        }

        @SuppressWarnings("unused")
        protected Object fromDJVM() {
            throw new IllegalStateException("Victory is mine!");
        }
    }

    @Test
    void testPassingClassIntoSandboxIsForbidden() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<Class<?>, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            Throwable ex = assertThrows(RuleViolationError.class, () -> WithJava.run(executor, EvilClass.class, String.class));
            assertThat(ex)
                .hasMessageContaining("Cannot sandbox class java.lang.String");
            return null;
        });
    }

    public static class EvilClass implements Function<Class<?>, String> {
        @Override
        public String apply(Class<?> clazz) {
            return clazz.getName();
        }
    }

    @Test
    void testPassingConstructorIntoSandboxIsForbidden() throws NoSuchMethodException {
        Constructor<?> constructor = getClass().getDeclaredConstructor();
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<Constructor<?>, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            Throwable ex = assertThrows(RuleViolationError.class, () -> WithJava.run(executor, EvilConstructor.class, constructor));
            assertThat(ex)
                .hasMessageContaining("Cannot sandbox " + constructor);
            return null;
        });
    }

    public static class EvilConstructor implements Function<Constructor<?>, String> {
        @Override
        public String apply(Constructor<?> constructor) {
            return constructor.getName();
        }
    }

    @Test
    void testPassingClassLoaderIntoSandboxIsForbidden() {
        ClassLoader classLoader = getClass().getClassLoader();
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<ClassLoader, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            Throwable ex = assertThrows(RuleViolationError.class, () -> WithJava.run(executor, EvilClassLoader.class, classLoader));
            assertThat(ex)
                .hasMessageContaining("Cannot sandbox a ClassLoader");
            return null;
        });
    }

    public static class EvilClassLoader implements Function<ClassLoader, String> {
        @Override
        public String apply(ClassLoader classLoader) {
            return classLoader.toString();
        }
    }

    @Test
    void testCannotInvokeSandboxMethodsExplicitly() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            Throwable ex = assertThrows(SandboxClassLoadingException.class,
                               () -> WithJava.run(executor, SelfSandboxing.class, "Victory is mine!"));
            assertThat(ex)
                .isExactlyInstanceOf(SandboxClassLoadingException.class)
                .hasMessageContaining(Type.getInternalName(SelfSandboxing.class))
                .hasMessageContaining("Access to sandbox.java.lang.String.toDJVM(String) is forbidden.")
                .hasMessageContaining("Access to sandbox.java.lang.String.fromDJVM(String) is forbidden.")
                .hasMessageContaining("Casting to sandbox.java.lang.String is forbidden.")
                .hasNoCause();
            return null;
        });
    }

    public static class SelfSandboxing implements Function<String, String> {
        @SuppressWarnings("ConstantConditions")
        @Override
        public String apply(String message) {
            return (String) (Object) sandbox.java.lang.String.toDJVM(
                sandbox.java.lang.String.fromDJVM((sandbox.java.lang.String) (Object) message)
            );
        }
    }
}