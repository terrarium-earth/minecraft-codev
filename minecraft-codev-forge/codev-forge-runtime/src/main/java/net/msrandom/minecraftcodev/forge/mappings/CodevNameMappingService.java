package net.msrandom.minecraftcodev.forge.mappings;

import com.google.common.base.Suppliers;
import cpw.mods.modlauncher.api.INameMappingService;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class CodevNameMappingService implements INameMappingService {
    private static final String MAPPINGS_PATH_PROPERTY = "codev.naming.mappingsPath";

    private final Supplier<Mappings> mappings = Suppliers.memoize(() -> {
        Path path = Paths.get(Objects.requireNonNull(
            System.getProperty(MAPPINGS_PATH_PROPERTY),
            "Missing mappings path"
        ));
        InputStream mappingsStream;
        try {
            mappingsStream = Files.newInputStream(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Won't be null since we're requiring a property
//        if (mappingsStream == null) {
//            return new Mappings(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
//        }

        Map<String, String> classes = new HashMap<>();
        Map<String, String> methods = new HashMap<>();
        Map<String, String> fields = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(mappingsStream))) {
            MemoryMappingTree mappings = new MemoryMappingTree();
            Tiny2FileReader.read(reader, mappings);
            for (final MappingTree.ClassMapping classMapping : mappings.getClasses()) {
                classes.put(
                    processClassName(classMapping.getName("srg")),
                    processClassName(classMapping.getName("named"))
                );

                for (final MappingTree.MethodMapping method : classMapping.getMethods()) {
                    methods.put(method.getName("srg"), method.getName("named"));
                }

                for (final MappingTree.FieldMapping field : classMapping.getFields()) {
                    fields.put(field.getName("srg"), field.getName("named"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new Mappings(classes, methods, fields);
    });

    private static String processClassName(String name) {
        return name.replace('/', '.');
    }

    public String mappingName() {
        return "codev";
    }

    public String mappingVersion() {
        return "1";
    }

    public Map.Entry<String, String> understanding() {
        return new Map.Entry<String, String>() {
            @Override
            public String getKey() {
                return "srg";
            }

            @Override
            public String getValue() {
                return "mcp";
            }

            @Override
            public String setValue(String value) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public BiFunction<INameMappingService.Domain, String, String> namingFunction() {
        return (domain, name) -> {
            switch (domain) {
                case CLASS:
                    return (String) this.mappings.get().classes.getOrDefault(name, name);
                case METHOD:
                    return (String) this.mappings.get().methods.getOrDefault(name, name);
                case FIELD:
                    return (String) this.mappings.get().fields.getOrDefault(name, name);
                default:
                    return name;
            }
        };
    }

    private static class Mappings {
        private final Map<String, String> classes;
        private final Map<String, String> methods;
        private final Map<String, String> fields;

        public Mappings(Map<String, String> classes, Map<String, String> methods, Map<String, String> fields) {
            this.classes = classes;
            this.methods = methods;
            this.fields = fields;
        }
    }
}

