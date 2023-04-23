/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.converter.ByteValueConverter;
import co.elastic.apm.agent.configuration.converter.TimeDurationValueConverter;
import co.elastic.apm.agent.tracer.configuration.ByteValue;
import co.elastic.apm.agent.tracer.configuration.ConfigurationProperty;
import co.elastic.apm.agent.tracer.configuration.Matcher;
import co.elastic.apm.agent.tracer.configuration.TimeDuration;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.BooleanValueConverter;
import org.stagemonitor.configuration.converter.DoubleValueConverter;
import org.stagemonitor.configuration.converter.EnumValueConverter;
import org.stagemonitor.configuration.converter.IntegerValueConverter;
import org.stagemonitor.configuration.converter.ListValueConverter;
import org.stagemonitor.configuration.converter.LongValueConverter;
import org.stagemonitor.configuration.converter.MapValueConverter;
import org.stagemonitor.configuration.converter.StringValueConverter;
import org.stagemonitor.configuration.converter.ValueConverter;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigurationProxy<T> extends ConfigurationOptionProvider implements InvocationHandler {

    private static final Map<Class<?>, ValueConverter<?>> CONVERTERS = new HashMap<>();

    static {
        CONVERTERS.put(boolean.class, BooleanValueConverter.INSTANCE);
        CONVERTERS.put(Boolean.class, BooleanValueConverter.INSTANCE);
        CONVERTERS.put(int.class, IntegerValueConverter.INSTANCE);
        CONVERTERS.put(Integer.class, IntegerValueConverter.INSTANCE);
        CONVERTERS.put(long.class, LongValueConverter.INSTANCE);
        CONVERTERS.put(Long.class, LongValueConverter.INSTANCE);
        CONVERTERS.put(double.class, DoubleValueConverter.INSTANCE);
        CONVERTERS.put(Double.class, DoubleValueConverter.INSTANCE);
        CONVERTERS.put(String.class, StringValueConverter.INSTANCE);
        CONVERTERS.put(ByteValue.class, ByteValueConverter.INSTANCE);
        CONVERTERS.put(TimeDuration.class, TimeDurationValueConverter.withDefaultDuration());
        CONVERTERS.put(Matcher.class, new MatcherValueConverter());
    }

    private final Class<T> config;

    private final Map<Method, ConfigurationOption<?>> options;

    private ConfigurationProxy(Class<T> config, Map<Method, ConfigurationOption<?>> options) {
        this.config = config;
        this.options = options;
    }

    public static <T> ConfigurationProxy<T> of(Class<T> config) {
        Map<Method, ConfigurationOption<?>> options = new HashMap<>();
        for (Method method : config.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            } else if (method.getParameterTypes().length > 0) {
                throw new IllegalArgumentException(method + " declares parameter types");
            }
            ConfigurationProperty property = method.getAnnotation(ConfigurationProperty.class);
            if (property == null) {
                throw new IllegalArgumentException(method + " lacks " + ConfigurationProperty.class.getName() + " annotation");
            }
            Class<?> type = method.getReturnType();
            boolean list;
            ValueConverter<?> converter;
            if (type == List.class) {
                Type generic = method.getGenericReturnType();
                if (!(generic instanceof ParameterizedType)) {
                    throw new IllegalArgumentException(method + " declares a raw list type");
                }
                Type actual = ((ParameterizedType) generic).getActualTypeArguments()[0];
                if (!(actual instanceof Class<?>)) {
                    throw new IllegalArgumentException(method + " declares a generic parameter type");
                }
                type = (Class<?>) actual;
                converter = toConverter(type);
                if (converter != null) {
                    converter = new ListValueConverter<>(converter);
                }
                list = true;
            } else if (type == Map.class) {
                Type generic = method.getGenericReturnType();
                if (!(generic instanceof ParameterizedType)) {
                    throw new IllegalArgumentException(method + " declares a raw map type");
                }
                Type left = ((ParameterizedType) generic).getActualTypeArguments()[0], right = ((ParameterizedType) generic).getActualTypeArguments()[1];
                if (!(left instanceof Class<?>) || !(right instanceof Class<?>)) {
                    throw new IllegalArgumentException(method + " declares a generic map type");
                }
                ValueConverter<?> leftConverter = toConverter((Class<?>) left), rightConverter = toConverter((Class<?>) right);
                if (leftConverter != null && rightConverter != null) {
                    converter = new MapValueConverter<>(leftConverter, rightConverter, "=", ",");
                } else {
                    converter = null;
                }
                list = true;
            } else {
                converter = toConverter(type);
                list = false;
            }
            if (converter == null) {
                throw new IllegalArgumentException(method + " declares an unknown property type");
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            ConfigurationOption.ConfigurationOptionBuilder<Object> builder = ConfigurationOption
                .builder((ValueConverter) converter, method.getReturnType())
                .key(property.key())
                .aliasKeys(property.aliasKeys())
                .configurationCategory(property.configurationCategory())
                .tags(property.tags())
                .description(property.description())
                .dynamic(property.dynamic());
            String[] defaults = property.withDefault();
            ConfigurationOption<?> option;
            if (list) {
                List<Object> values = new ArrayList<>(defaults.length);
                for (String aDefault : defaults) {
                    values.add(converter.convert(aDefault));
                }
                if (values.isEmpty() && method.isAnnotationPresent(Nullable.class)) {
                    option = builder.buildWithDefault(null);
                } else {
                    option = builder.buildWithDefault(values);
                }
            } else if (defaults.length > 1) {
                throw new IllegalStateException(method + " does not support multiple default values " + Arrays.asList(defaults));
            } else if (defaults.length == 1) {
                option = builder.buildWithDefault(converter.convert(defaults[0]));
            } else if (method.isAnnotationPresent(Nullable.class)) {
                option = builder.buildOptional();
            } else {
                option = builder.buildRequired();
            }
            options.put(method, option);
        }
        return new ConfigurationProxy<>(config, options);
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ValueConverter<?> toConverter(Class<?> type) {
        return type.isEnum() ? new EnumValueConverter<>((Class) type) : CONVERTERS.get(type);
    }

    public Class<T> getConfig() {
        return config;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(this);
            } else if (method.getName().equals("equals")) {
                return args[0] == this;
            } else if (method.getName().equals("toString")) {
                return "proxy@" + this.getClass().getInterfaces()[0].getName();
            } else {
                throw new IllegalStateException("Unknown Object method: " + method);
            }
        }
        ConfigurationOption<?> handler = options.get(method);
        if (handler == null) {
            throw new IllegalStateException("Unknown configuration method: " + method);
        }
        return handler.get();
    }

    @Override
    public List<ConfigurationOption<?>> getConfigurationOptions() {
        return new ArrayList<>(options.values());
    }

    private static class MatcherValueConverter implements ValueConverter<Matcher> {

        @Override
        public Matcher convert(String s) {
            return new WildcardMatcherMatcher(WildcardMatcher.valueOf(s));
        }

        @Override
        public String toString(Matcher value) {
            return value.toString();
        }

        @Override
        public String toSafeString(Matcher value) {
            return value.toString();
        }
    }
}
