/*
 * Copyright 2014 CIRDLES.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cirdles.topsoil.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author John Zeringue
 */
public class SimpleEntry implements Entry {

    private final Map<Field, Object> fieldsToValues;

    public SimpleEntry() {
        fieldsToValues = new HashMap<>();
    }

    @Override
    public <T> Optional<T> get(Field<? super T> field) {
        return Optional.ofNullable((T) fieldsToValues.get(field));
    }

    @Override
    public <T> void set(Field<? super T> field, T value) {
        fieldsToValues.put(field, value);
    }
    
}
