package net.msrandom.minecraftcodev.forge.mappings;

import com.google.common.base.Suppliers;
import cpw.mods.modlauncher.api.INameMappingService;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CodevNameMappingService implements INameMappingService {
    private final Supplier<Mappings> mappings = Suppliers.memoize(() -> {
        InputStream mappingsStream = getClass().getResourceAsStream("/mappings.zip");

        if (mappingsStream == null) {
            return new Mappings(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        Map<String, String> classes = new HashMap<>();
        Map<String, String> methods = new HashMap<>();
        Map<String, String> fields = new HashMap<>();

        try (ZipInputStream zip = new ZipInputStream(mappingsStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().contains("mappings/mappings.tiny")) {
                    continue;
                }

                InputStream entryStream = new FilterInputStream(zip) {
                    @Override
                    public void close() throws IOException {
                        zip.closeEntry();
                    }
                };

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(entryStream))) {
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

