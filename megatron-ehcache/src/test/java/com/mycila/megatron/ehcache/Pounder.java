/*
 * Copyright © 2017 Mathieu Carbou (mathieu.carbou@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mycila.megatron.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @author Mathieu Carbou
 */
class Pounder {
  static Thread pounder(CacheManager cacheManager) {
    List<Cache<String, byte[]>> caches = Arrays.asList(
        cacheManager.getCache("cache-1", String.class, byte[].class),
        cacheManager.getCache("cache-2", String.class, byte[].class),
        cacheManager.getCache("cache-3", String.class, byte[].class));

    return new Thread(() -> {
      Random RANDOM = new Random();
      while (!Thread.currentThread().isInterrupted()) {
        try {
          byte[] data = new byte[RANDOM.nextInt(1024)];
          caches.get(RANDOM.nextInt(caches.size())).put("key-" + RANDOM.nextInt(1000), data);
          caches.get(RANDOM.nextInt(caches.size())).get("key-" + RANDOM.nextInt(1000));
          caches.get(RANDOM.nextInt(caches.size())).remove("key-" + RANDOM.nextInt(1000));
        } catch (Exception e) {
          e.printStackTrace();
        }
        try {
          Thread.sleep(250);
        } catch (InterruptedException e) {
          return;
        }
      }
    });
  }
}
