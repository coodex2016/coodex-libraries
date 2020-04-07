/*
 * Copyright (c) 2018 coodex.org (jujus.shen@126.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coodex.util;

import java.util.function.Supplier;

public class Singleton<T> {

    private Supplier<T> supplier;
    private volatile T instance = null;
    private volatile boolean loaded = false;


    private Singleton(Supplier<T> supplier) {
        if (supplier == null) throw new NullPointerException("supplier MUST NOT be null.");
        this.supplier = supplier;
    }

    public static <T> Singleton<T> with(Supplier<T> supplier) {
        return new Singleton<>(supplier);
    }


    public T get() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    instance = supplier.get();
                    loaded = true;
                }
            }
        }
        return instance;
    }

}
