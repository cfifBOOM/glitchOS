import com.sun.source.util.JavacTask;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Syntax parsing only. Full Android type checking belongs to Build-Home.ps1. */
public final class SourceSyntaxCheck {
    public static void main(String[] paths) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A JDK is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    Arrays.asList("-source", "8", "-proc:none"), null, files.getJavaFileObjects(paths));
            task.parse();
            boolean failed = false;
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) { System.err.println(diagnostic); failed = true; }
            }
            if (failed) throw new AssertionError("Java syntax error");
        }
        System.out.println("PASS: parsed " + paths.length + " Java sources (Android type checking still requires SDK)");
    }
}
