/**
 * Copyright 2018 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.reactive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.RedissonSetMultimap;
import org.redisson.api.RSetMultimapReactive;
import org.redisson.api.RSetReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonSetMultimapReactive<K, V> extends RedissonBaseMultimapReactive<K, V> implements RSetMultimapReactive<K, V> {

    public RedissonSetMultimapReactive(UUID id, CommandReactiveExecutor commandExecutor, String name) {
        super(new RedissonSetMultimap<K, V>(commandExecutor, name), commandExecutor, name);
    }

    public RedissonSetMultimapReactive(UUID id, Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(new RedissonSetMultimap<K, V>(codec, commandExecutor, name), codec, commandExecutor, name);
    }

    @Override
    public RSetReactive<V> get(final K key) {
        final ByteBuf keyState = encodeMapKey(key);
        final String keyHash = hashAndRelease(keyState);
        final String setName = getValuesName(keyHash);

        return new RedissonSetReactive<V>(codec, commandExecutor, setName) {
            
            @Override
            public Publisher<Boolean> delete() {
                ByteBuf keyState = encodeMapKey(key);
                return RedissonSetMultimapReactive.this.fastRemove(Arrays.<Object>asList(keyState), Arrays.<Object>asList(setName), RedisCommands.EVAL_BOOLEAN_AMOUNT);
            }
            
            @Override
            public Publisher<Boolean> clearExpire() {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Boolean> expire(long timeToLive, TimeUnit timeUnit) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Boolean> expireAt(long timestamp) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Long> remainTimeToLive() {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Void> rename(String newName) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Boolean> renamenx(String newName) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
        };
    }

    @Override
    public Publisher<Set<V>> getAll(K key) {
        ByteBuf keyState = encodeMapKey(key);
        String keyHash = hashAndRelease(keyState);
        String setName = getValuesName(keyHash);

        return commandExecutor.readReactive(getName(), codec, RedisCommands.SMEMBERS, setName);
    }

    @Override
    public Publisher<Set<V>> removeAll(Object key) {
        ByteBuf keyState = encodeMapKey(key);
        String keyHash = hash(keyState);

        String setName = getValuesName(keyHash);
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_SET,
                "redis.call('hdel', KEYS[1], ARGV[1]); " +
                "local members = redis.call('smembers', KEYS[2]); " +
                "redis.call('del', KEYS[2]); " +
                "return members; ",
            Arrays.<Object>asList(getName(), setName), keyState);
    }

    @Override
    public Publisher<Set<V>> replaceValues(K key, Iterable<? extends V> values) {
        List<Object> params = new ArrayList<Object>();
        ByteBuf keyState = encodeMapKey(key);
        params.add(keyState);
        String keyHash = hash(keyState);
        params.add(keyHash);
        for (Object value : values) {
            ByteBuf valueState = encodeMapValue(value);
            params.add(valueState);
        }

        String setName = getValuesName(keyHash);
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_SET,
                "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                "local members = redis.call('smembers', KEYS[2]); " +
                "redis.call('del', KEYS[2]); " +
                "redis.call('sadd', KEYS[2], unpack(ARGV, 3, #ARGV)); " +
                "return members; ",
            Arrays.<Object>asList(getName(), setName), params.toArray());
    }

}
