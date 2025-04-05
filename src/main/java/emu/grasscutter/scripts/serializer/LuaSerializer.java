package emu.grasscutter.scripts.serializer;

import com.esotericsoftware.reflectasm.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.scripts.ScriptUtils;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.*;

public class LuaSerializer implements Serializer {

    private static final Map<Class<?>, MethodAccess> methodAccessCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ConstructorAccess<?>> constructorCache =
            new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, FieldMeta>> fieldMetaCache =
            new ConcurrentHashMap<>();

    @Override
    public <T> List<T> toList(Class<T> type, Object obj) {
        return serializeList(type, (LuaTable) obj);
    }

    @Override
    public <T> T toObject(Class<T> type, Object obj) {
        return serialize(type, null, (LuaTable) obj);
    }

    @Override
    public <T> Map<String, T> toMap(Class<T> type, Object obj) {
        return serializeMap(type, (LuaTable) obj);
    }

    private <T> T convertToType(Class<T> type, LuaValue value) {
        if (value == null) {
            return null;
        }

        if (value.istable()) {
            return serialize(type, null, value.checktable());
        } else if (type.isAssignableFrom(Integer.class) && value.isint()) {
            return type.cast(value.toint());
        } else if (type.isAssignableFrom(Float.class) && value.isnumber()) {
            return type.cast(value.tofloat());
        } else if (type.isAssignableFrom(String.class) && value.isstring()) {
            return type.cast(value.tojstring());
        } else if (type.isAssignableFrom(Boolean.class) && value.isboolean()) {
            return type.cast(value.toboolean());
        } else if (type.isInstance(value)) {
            return type.cast(value);
        } else {
            throw new IllegalArgumentException("Unsupported type conversion");
        }
    }

    private <T> Map<String, T> serializeMap(Class<T> type, LuaTable table) {
        Map<String, T> map = new HashMap<>();

        if (table == null || table.length() == 0) {
            return map;
        }

        try {
            LuaValue[] keys = table.keys();
            if (keys == null || keys.length == 0) {
                return map;
            }

            for (LuaValue k : keys) {
                String keyStr = String.valueOf(k);
                try {
                    LuaValue keyValue = table.get(k);

                    T object = convertToType(type, keyValue);

                    if (object != null) {
                        map.put(keyStr, object);
                    }
                } catch (Exception ex) {
                    Grasscutter.getLogger().error("Error processing key: {}", k, ex);
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error serializing map", e);
        }

        return map;
    }

    public <T> List<T> serializeList(Class<T> type, LuaTable table) {
        List<T> list = new ArrayList<>();

        if (table == null || table.length() == 0) {
            return list;
        }

        for (LuaValue k : table.keys()) {
            try {
                LuaValue keyValue = table.get(k);
                T object = convertToType(type, keyValue);
                if (object != null) {
                    list.add(object);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return list;
    }

    private Class<?> getListType(Class<?> type, @Nullable Field field) {
        if (field == null) {
            return type.getTypeParameters()[0].getClass();
        }
        Type fieldType = field.getGenericType();
        if (fieldType instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) fieldType).getActualTypeArguments()[0];
        }

        return null;
    }

    public <T> T serialize(Class<T> type, @Nullable Field field, LuaTable table) {
        T object = null;

        if (type == List.class) {
            try {
                Class<?> listType = getListType(type, field);
                return (T) serializeList(listType, table);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        try {
            if (!methodAccessCache.containsKey(type)) {
                cacheType(type);
            }
            var methodAccess = methodAccessCache.get(type);
            var fieldMetaMap = fieldMetaCache.get(type);

            object = (T) constructorCache.get(type).newInstance();

            if (table == null) {
                return object;
            }

            LuaValue[] keys = table.keys();
            for (LuaValue k : keys) {
                try {
                    var keyName = k.checkjstring();
                    if (!fieldMetaMap.containsKey(keyName)) {
                        continue;
                    }
                    var fieldMeta = fieldMetaMap.get(keyName);
                    LuaValue keyValue = table.get(k);

                    if (keyValue.istable()) {
                        methodAccess.invoke(
                                object,
                                fieldMeta.index,
                                serialize(fieldMeta.getType(), fieldMeta.getField(), keyValue.checktable()));
                    } else if (fieldMeta.getType().equals(float.class)) {
                        methodAccess.invoke(object, fieldMeta.index, keyValue.tofloat());
                    } else if (fieldMeta.getType().equals(int.class)) {
                        methodAccess.invoke(object, fieldMeta.index, keyValue.toint());
                    } else if (fieldMeta.getType().equals(String.class)) {
                        methodAccess.invoke(object, fieldMeta.index, keyValue.tojstring());
                    } else if (fieldMeta.getType().equals(boolean.class)) {
                        methodAccess.invoke(object, fieldMeta.index, keyValue.toboolean());
                    } else {
                        methodAccess.invoke(object, fieldMeta.index, keyValue.tojstring());
                    }
                } catch (Exception ex) {
                    // ex.printStackTrace();
                    continue;
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger().debug(ScriptUtils.toMap(table).toString());
            e.printStackTrace();
        }

        return object;
    }

    public <T> Map<String, FieldMeta> cacheType(Class<T> type) {
        if (fieldMetaCache.containsKey(type)) {
            return fieldMetaCache.get(type);
        }
        if (!constructorCache.containsKey(type)) {
            constructorCache.putIfAbsent(type, ConstructorAccess.get(type));
        }
        var methodAccess =
                Optional.ofNullable(methodAccessCache.get(type)).orElse(MethodAccess.get(type));
        methodAccessCache.putIfAbsent(type, methodAccess);

        var fieldMetaMap = new HashMap<String, FieldMeta>();
        var methodNameSet = new HashSet<>(Arrays.stream(methodAccess.getMethodNames()).toList());

        Arrays.stream(type.getDeclaredFields())
                .filter(field -> methodNameSet.contains(getSetterName(field.getName())))
                .forEach(
                        field -> {
                            var setter = getSetterName(field.getName());
                            var index = methodAccess.getIndex(setter);
                            fieldMetaMap.put(
                                    field.getName(),
                                    new FieldMeta(field.getName(), setter, index, field.getType(), field));
                        });

        Arrays.stream(type.getFields())
                .filter(field -> !fieldMetaMap.containsKey(field.getName()))
                .filter(field -> methodNameSet.contains(getSetterName(field.getName())))
                .forEach(
                        field -> {
                            var setter = getSetterName(field.getName());
                            var index = methodAccess.getIndex(setter);
                            fieldMetaMap.put(
                                    field.getName(),
                                    new FieldMeta(field.getName(), setter, index, field.getType(), field));
                        });

        fieldMetaCache.put(type, fieldMetaMap);
        return fieldMetaMap;
    }

    public String getSetterName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        if (fieldName.length() == 1) {
            return "set" + fieldName.toUpperCase();
        }
        return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    @Data
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    static class FieldMeta {
        String name;
        String setter;
        int index;
        Class<?> type;
        @Nullable Field field;
    }
}
