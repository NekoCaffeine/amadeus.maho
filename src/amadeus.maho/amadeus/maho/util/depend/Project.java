package amadeus.maho.util.depend;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.ArrayHelper;

@ToString
@EqualsAndHashCode
public record Project(String group, String artifact, String version, String... classifiers) {
    
    @ToString
    @EqualsAndHashCode
    public record Dependency(Project project, boolean compile = true, boolean runtime = true, @Nullable Repository source = null) {
        
        @Getter
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        public static class Holder {
            
            Set<Dependency> dependencies = new HashSet<>();
            
            public self compile(final String... projects) = addAll(project -> new Dependency(project, true, false), projects);
            
            public self runtime(final String... projects) = addAll(project -> new Dependency(project, false, true), projects);
            
            public self all(final String... projects) = addAll(Dependency::new, projects);
            
            public self all(final List<String> projects) = all(projects.toArray(new String[0]));
            
            public void addAll(final Function<Project, Dependency> function, final String... projects) = Stream.of(projects).map(Project::of).map(function).forEach(dependencies()::add);
            
        }
        
    }
    
    @Override
    public String toString() = STR."\{group()}:\{artifact()}\{classifiers().length > 0 || !version().equals("+") ? STR.":\{version()}" : ""}\{classifiers().length > 0 ? STR.":\{String.join(":", classifiers())}" : ""}";
    
    public Project dropClassifier() = classifiers().length == 0 ? this : new Project(group(), artifact(), version());
    
    public Project concatClassifier(final String... classifiers) = { group(), artifact(), version(), ArrayHelper.addAll(classifiers(), classifiers) };
    
    public boolean snapshot() = version().endsWith("-SNAPSHOT");
    
    public static Project of(final String project) {
        final String info[] = project.split(":");
        if (info.length < 2)
            throw new IllegalArgumentException(STR."Invalid project: '\{project}', At least 'group:artifact(:version)(:extend)', e.g. 'org.lwjgl:lwjgl-bgfx'");
        return { info[0], info[1], info.length > 2 ? info[2] : "+", ArrayHelper.sub(info, 3) };
    }
    
    public static Predicate<Project> groupAny(final String... groups) = project -> Stream.of(groups).anyMatch(group -> project.group().startsWith(group));
    
    public static Predicate<Project> artifactAny(final String... artifacts) = project -> Stream.of(artifacts).anyMatch(artifact -> project.artifact().startsWith(artifact));
    
}
