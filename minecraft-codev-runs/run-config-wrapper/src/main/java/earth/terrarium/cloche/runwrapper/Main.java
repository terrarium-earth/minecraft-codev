package earth.terrarium.cloche.runwrapper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Throwable {
        if (args.length < 1) {
            throw new IllegalArgumentException("run-config-wrapper needs at least one argument which is the path to the launch config");
        }

        FileInputStream stream = new FileInputStream(args[0]);

        Reader reader;

        try {
            reader = new InputStreamReader(stream);
        } catch (Throwable e) {
            stream.close();
            throw e;
        }

        JsonObject json;

        try {
            json = new Gson().fromJson(reader, JsonElement.class).getAsJsonObject();
        } finally {
            reader.close();
        }

        run(
            json.get("main").getAsString(),
            json.getAsJsonArray("arguments"),
            json.getAsJsonObject("properties")
        );
    }

    private static void run(String mainClass, JsonArray arguments, JsonObject jvmArguments) throws Throwable {
        for (Map.Entry<String, JsonElement> jvmArgument : jvmArguments.entrySet()) {
            System.setProperty(jvmArgument.getKey(), jvmArgument.getValue().getAsString());
        }

        String[] args = new String[arguments.size()];

        int i = 0;
        for (JsonElement argument : arguments) {
            args[i++] = argument.getAsString();
        }

        MethodHandle handle = MethodHandles.publicLookup().findStatic(Class.forName(mainClass), "main", MethodType.methodType(void.class, String[].class));
        handle.invokeExact((Object) args);
    }
}
