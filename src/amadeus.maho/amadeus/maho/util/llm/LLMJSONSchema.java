package amadeus.maho.util.llm;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.DynamicObject;
import amadeus.maho.util.dynamic.FieldsMap;

public interface LLMJSONSchema {
    
    Map<Class<?>, String> typeMap = new HashMap<Class<?>, String>().let(map -> {
        map[String.class] = "string";
        map[boolean.class] = "boolean";
        map[Boolean.class] = "boolean";
        map[byte.class] = "integer";
        map[Byte.class] = "integer";
        map[short.class] = "integer";
        map[Short.class] = "integer";
        map[int.class] = "integer";
        map[Integer.class] = "integer";
        map[long.class] = "integer";
        map[Long.class] = "integer";
        map[float.class] = "number";
        map[Float.class] = "number";
        map[double.class] = "number";
        map[Double.class] = "number";
        map[char.class] = "string";
        map[Character.class] = "string";
        map[void.class] = "null";
        map[Void.class] = "null";
    });
    
    private static void markType(final DynamicObject.MapUnit object, final Class<?> type, final Map<Class<?>, String> nameMapping) {
        final @Nullable String typeOf = typeMap[type];
        if (typeOf != null)
            object["type"] = typeOf;
        else if (type.isArray()) {
            final DynamicObject.MapUnit items = { };
            object["type"] = "array";
            object["items"] = items;
            markType(items, type.getComponentType(), nameMapping);
        } else
            object["$ref"] = nameMapping.computeIfAbsent(type, it -> STR."#/$defs/\{className(it)}");
    }
    
    ClassLocal<DynamicObject.MapUnit> responseFormatLocal = { LLMJSONSchema::createResponseFormat };
    
    private static DynamicObject.MapUnit createResponseFormat(final Class<?> type) {
        final HashMap<Class<?>, String> nameMapping = { };
        nameMapping[type] = "#";
        final DynamicObject.MapUnit response_format = { }, json_schema = { };
        response_format["type"] = "json_schema";
        response_format["json_schema"] = json_schema;
        json_schema["name"] = "Result";
        json_schema["schema"] = createJsonSchema(type, nameMapping);
        json_schema["strict"] = true;
        nameMapping.remove(type);
        if (!nameMapping.isEmpty()) {
            final DynamicObject.MapUnit definitions = { };
            json_schema["$defs"] = definitions;
            nameMapping.forEach((fieldType, name) -> definitions[name] = createJsonSchema(fieldType, nameMapping));
        }
        return response_format;
    }
    
    private static DynamicObject.MapUnit createJsonSchema(final Class<?> type, final Map<Class<?>, String> nameMapping) {
        final DynamicObject.MapUnit schema = { }, properties = { };
        final @Nullable String typeOf = typeMap[type];
        final DynamicObject.ArrayUnit required = { };
        applyDescriptionIfPresent(schema, type);
        schema["type"] = "object";
        schema["properties"] = properties;
        schema["required"] = required;
        schema["additionalProperties"] = false;
        if (typeOf != null) {
            final DynamicObject.MapUnit property = { };
            property["type"] = typeOf;
            properties["value"] = property;
            required += "value";
        } else if(type.isArray()) {
            final DynamicObject.MapUnit property = { };
            property["type"] = "array";
            properties["value"] = property;
            required += "value";
            final DynamicObject.MapUnit items = { };
            property["items"] = items;
            markType(items, type.getComponentType(), nameMapping);
        } else
            FieldsMap.fieldsMapLocal()[type].forEach((name, fieldInfo) -> {
                final Field field = fieldInfo.field();
                final boolean nullable = field.isAnnotationPresent(Nullable.class);
                final DynamicObject.MapUnit property = { };
                if (nullable) {
                    final DynamicObject.MapUnit anyOfProperty = { }, nullableProperty = { };
                    final DynamicObject.ArrayUnit anyOf = { };
                    nullableProperty["type"] = "null";
                    anyOf += property;
                    anyOf += nullableProperty;
                    anyOfProperty["anyOf"] = anyOf;
                    properties[name] = anyOfProperty;
                } else
                    properties[name] = property;
                markType(property, field.getType(), nameMapping);
                applyDescriptionIfPresent(property, field);
                required += name;
            });
        return schema;
    }
    
    private static String className(final Class<?> clazz) = clazz.getCanonicalName().replace('.', '_').replace('$', '_');
    
    static void applyDescriptionIfPresent(final DynamicObject.MapUnit object, final AnnotatedElement element) = LLM.Accessor.ifDescriptionPresent(element, description -> object["description"] = description);
    
}
