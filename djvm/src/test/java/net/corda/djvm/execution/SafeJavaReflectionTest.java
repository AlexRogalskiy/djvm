package net.corda.djvm.execution;

import net.corda.djvm.ExceptionalFunction;
import net.corda.djvm.TestBase;
import net.corda.djvm.TypedTaskFactory;
import net.corda.djvm.WithJava;
import net.corda.djvm.rules.RuleViolationError;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

import static net.corda.djvm.SandboxType.JAVA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class SafeJavaReflectionTest extends TestBase {
    SafeJavaReflectionTest() {
        super(JAVA);
    }

    @Test
    void testGetClasses() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                String[] result = WithJava.run(taskFactory, GetClassClasses.class, null);
                assertThat(result).containsExactlyInAnyOrder(
                    "sandbox.net.corda.djvm.execution.GetClassClasses$NestedException"
                );
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testGetDeclaredClasses() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, GetClassDeclaredClasses.class, null));
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.Class.getDeclaredClasses()")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testInvokingConstructor() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, InvokeConstructor.class, null));
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.reflect.Constructor.newInstance(Object[])")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class InvokeConstructor implements Function<String, String> {
        @Override
        public String apply(String data) {
            try {
                UserData userData = UserData.class.getConstructor(String.class).newInstance(data);
                return userData.toString();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Test
    void testInvokingDeclaredConstructor() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, InvokeDeclaredConstructor.class, null));
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.Class.getDeclaredConstructor(Class[])")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class InvokeDeclaredConstructor implements Function<String, String> {
        @Override
        public String apply(String data) {
            try {
                UserData userData = UserData.class.getDeclaredConstructor(String.class).newInstance(data);
                return userData.toString();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Test
    void testInvokingNewInstanceByReference() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, InvokeNewInstanceByReference.class, null));
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.reflect.Constructor.newInstance(Object...)")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class InvokeNewInstanceByReference implements Function<String, String> {
        @Override
        public String apply(String data) {
            ExceptionalFunction<Object[], UserData> factory;
            Constructor<UserData> constructor;
            try {
                constructor = UserData.class.getConstructor(String.class);
                factory = constructor::newInstance;
                return factory.apply(new Object[]{ data }).toString();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Test
    void testInvokingMethod() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                     () -> WithJava.run(taskFactory, InvokeMethod.class, null));
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.reflect.Method.invoke(Object, Object...)")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class InvokeMethod implements Function<String, String> {
        public String getMessage() {
            return "Invoked!";
        }

        @Override
        public String apply(String unused) {
            try {
                return (String)getClass().getMethod("getMessage").invoke(this);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Test
    void testInvokingDeclaredMethod() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, InvokeDeclaredMethod.class, null));
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.Class.getDeclaredMethod(String, Class[])")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class InvokeDeclaredMethod implements Function<String, String> {
        public String getMessage() {
            return "Invoked Method!";
        }

        @Override
        public String apply(String unused) {
            try {
                return (String)(getClass().getDeclaredMethod("getMessage").invoke(this));
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class UserData {
        private final String data;
        public final String publicData;

        public UserData(String data) {
            this.data = data;
            this.publicData = data;
        }

        @Override
        public String toString() {
            return data;
        }
    }

    @Test
    void testWithEnclosingConstructor() {
        assertThat(new WithEnclosingConstructor().apply(null)).isNotNull();
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                Constructor result = WithJava.run(taskFactory, WithEnclosingConstructor.class, null);
                assertThat(result).isNotNull();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    @SuppressWarnings("WeakerAccess")
    public static class WithEnclosingConstructor implements Function<String, Constructor<?>> {
        private final Constructor<?> enclosed;

        public WithEnclosingConstructor() {
            class Enclosed {}
            enclosed = Enclosed.class.getEnclosingConstructor();
        }

        @Override
        public Constructor<?> apply(String unused) {
            return enclosed;
        }
    }

    @Test
    void testWithoutEnclosingConstructor() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                Constructor result = WithJava.run(taskFactory, WithoutEnclosingConstructor.class, null);
                assertThat(result).isNull();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class WithoutEnclosingConstructor implements Function<String, Constructor<?>> {
        @Override
        public Constructor<?> apply(String unused) {
            return UserData.class.getEnclosingConstructor();
        }
    }

    @Test
    void testWithEnclosingMethod() {
        assertThat(new WithEnclosingMethod().apply(null)).isNotNull();
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                Method result = WithJava.run(taskFactory, WithEnclosingMethod.class, null);
                assertThat(result).isNotNull();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class WithEnclosingMethod implements Function<String, Method> {
        @Override
        public Method apply(String unused) {
            class Enclosed {}
            return Enclosed.class.getEnclosingMethod();
        }
    }

    @Test
    void testWithoutEnclosingMethod() {
        assertThat(new WithoutEnclosingMethod().apply(null)).isNull();
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                Method result = WithJava.run(taskFactory, WithoutEnclosingMethod.class, null);
                assertThat(result).isNull();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class WithoutEnclosingMethod implements Function<String, Method> {
        @Override
        public Method apply(String unused) {
            return UserData.class.getEnclosingMethod();
        }
    }

    @Test
    void testGetField() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, GetField.class, "publicData")
                );
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.Class.getField(String)")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class GetField implements Function<String, Field> {
        @Override
        public Field apply(String fieldName) {
            try {
                return UserData.class.getField(fieldName);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Test
    void tstGetFields() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, GetFields.class, null)
                );
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.Class.getFields()")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class GetFields implements Function<String, Field[]> {
        @Override
        public Field[] apply(String unused) {
            return UserData.class.getFields();
        }
    }

    @Test
    void testGetDeclaredField() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, GetDeclaredField.class, "publicData")
                );
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.Class.getDeclaredField(String)")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class GetDeclaredField implements Function<String, Field> {
        @Override
        public Field apply(String fieldName) {
            try {
                return UserData.class.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Test
    void testDeclaredFields() {
        sandbox(ctx -> {
            try {
                TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                RuleViolationError ex = assertThrows(RuleViolationError.class,
                    () -> WithJava.run(taskFactory, GetDeclaredFields.class, null)
                );
                assertThat(ex)
                    .hasMessage("Disallowed reference to API; java.lang.Class.getDeclaredFields()")
                    .hasNoCause();
            } catch(Exception e) {
                fail(e);
            }
        });
    }

    public static class GetDeclaredFields implements Function<String, Field[]> {
        @Override
        public Field[] apply(String unused) {
            return UserData.class.getDeclaredFields();
        }
    }
}