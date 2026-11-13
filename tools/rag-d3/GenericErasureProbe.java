import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public final class GenericErasureProbe {
    private final List<String> declared = new ArrayList<>();

    private interface Source<T> {
        T read();
    }

    private static final class TextSource implements Source<String> {
        @Override
        public String read() {
            return "text";
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        List<String> strings = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();
        List<Integer> linked = new LinkedList<>();

        require(strings.getClass() == integers.getClass(), "same ArrayList implementation");
        require(strings.getClass() != linked.getClass(), "different List implementations");
        require(integers instanceof ArrayList<Integer>, "checked conversion from List<Integer>");

        Source<String> source = new TextSource();
        require("text".equals(source.read()), "erased interface call");

        Method bridge = Arrays.stream(TextSource.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow();
        require(bridge.getReturnType() == Object.class, "erased bridge return type");

        Field field = GenericErasureProbe.class.getDeclaredField("declared");
        ParameterizedType signature = (ParameterizedType) field.getGenericType();
        require(signature.getActualTypeArguments()[0] == String.class, "declared generic signature");
        require(field.getType() == List.class, "erased field type");

        System.out.println("Seven Java 21 erasure and metadata checks passed.");
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
