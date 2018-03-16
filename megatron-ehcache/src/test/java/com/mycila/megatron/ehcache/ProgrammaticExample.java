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

import org.ehcache.CacheManager;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.management.registry.DefaultManagementRegistryConfiguration;

import java.util.concurrent.TimeUnit;

import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder;
import static org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder;
import static org.ehcache.config.builders.ResourcePoolsBuilder.newResourcePoolsBuilder;

/**
 * @author Mathieu Carbou
 */
public class ProgrammaticExample {
  public static void main(String[] args) throws InterruptedException {

    CacheManager cacheManager = newCacheManagerBuilder()
        // management config
        .using(new DefaultManagementRegistryConfiguration()
            .addTags("webapp-1", "server-node-1")
            .setCacheManagerAlias("cm1"))
        // Megatron config
        .using(new DefaultMegatronServiceConfiguration()
            .setStatisticCollectorInterval(5, TimeUnit.SECONDS)
            .loadProperties(ProgrammaticExample.class.getResource("/megatron.properties"))
            .setProperty("megatron.console.enable", "true"))
        // cache config
        .withCache("cache-1", newCacheConfigurationBuilder(
            String.class, byte[].class,
            newResourcePoolsBuilder()
                .heap(1000, EntryUnit.ENTRIES)
                .offheap(5, MemoryUnit.MB))
            .build())
        .withCache("cache-2", newCacheConfigurationBuilder(
            String.class, byte[].class,
            newResourcePoolsBuilder()
                .heap(1000, EntryUnit.ENTRIES)
                .offheap(5, MemoryUnit.MB))
            .build())
        .withCache("cache-3", newCacheConfigurationBuilder(
            String.class, byte[].class,
            newResourcePoolsBuilder()
                .heap(1000, EntryUnit.ENTRIES)
                .offheap(5, MemoryUnit.MB))
            .build())
        .build(true);

    Thread pounder = Pounder.pounder(cacheManager);
    pounder.start();
    pounder.join();
  }
}
