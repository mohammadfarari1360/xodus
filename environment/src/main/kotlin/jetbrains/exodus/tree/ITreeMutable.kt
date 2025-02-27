/*
 * Copyright 2010 - 2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrains.exodus.tree

import jetbrains.exodus.ByteIterable
import jetbrains.exodus.log.RandomAccessLoggable

interface ITreeMutable : ITree {
    val root: MutableTreeRoot?
    val isAllowingDuplicates: Boolean
    val openCursors: Iterable<ITreeCursorMutable>?
    fun cursorClosed(cursor: ITreeCursorMutable)

    /**
     * If tree supports duplicates, then add key/value pair.
     * If tree doesn't support duplicates and key already exists, then overwrite value.
     * If tree doesn't support duplicates and key doesn't exists, then add key/value pair.
     *
     * @param key   key.
     * @param value value.
     */
    fun put(key: ByteIterable, value: ByteIterable): Boolean

    /**
     * Add key/value pair with greatest (rightmost) key.
     * In duplicates tree, value must be greatest too.
     *
     * @param key   key.
     * @param value value.
     */
    fun putRight(key: ByteIterable, value: ByteIterable)

    /**
     * If tree supports duplicates and key already exists, then return false.
     * If tree supports duplicates and key doesn't exists, then add key/value pair, return true.
     * If tree doesn't support duplicates and key already exists, then return false.
     * If tree doesn't support duplicates and key doesn't exists, then add key/value pair, return true.
     *
     * @param key   key.
     * @param value value.
     * @return false if key exists and tree is not supports duplicates
     */
    fun add(key: ByteIterable, value: ByteIterable): Boolean

    /**
     * If tree supports duplicates, then add key/value pair.
     * If tree doesn't support duplicates and key already exists, then overwrite value.
     * If tree doesn't support duplicates and key doesn't exists, then add key/value pair.
     *
     * @param ln leaf node
     */
    fun put(ln: INode)

    /**
     * Add key/value pair with greatest (rightmost) key.
     * In duplicates tree, value must be greatest too.
     *
     * @param ln leaf node, its key and values should be subtypes of ArrayByteIterable
     */
    fun putRight(ln: INode)

    /**
     * If tree support duplicates and key already exists, then return false.
     * If tree support duplicates and key doesn't exists, then add key/value pair, return true.
     * If tree doesn't support duplicates and key already exists, then return false.
     * If tree doesn't support duplicates and key doesn't exists, then add key/value pair, return true.
     *
     * @param ln leaf node
     * @return true if succeed
     */
    fun add(ln: INode): Boolean

    /**
     * Delete key/value pairs for given key. If duplicate values exist for given key, all they will be removed.
     *
     * @param key key.
     * @return false if key wasn't found
     */
    fun delete(key: ByteIterable): Boolean

    /**
     * Delete key/value pair, should be supported only by tree which allows duplicates if value is not null.
     *
     * @param key          key.
     * @param value        value.
     * @param cursorToSkip mutable cursor to skip.
     * @return false if key/value pair wasn't found
     */
    fun delete(key: ByteIterable, value: ByteIterable?, cursorToSkip: ITreeCursorMutable?): Boolean

    /**
     * Save changes to log.
     *
     * @return address of new root page
     */
    fun save(): Long

    /**
     * @return set of expired loggables that were changed by put or delete methods.
     */
    val expiredLoggables: ExpiredLoggableCollection

    /**
     * Same as reclaim with expirationChecker, but takes all loggables into account
     *
     * @param loggable  a candidate to reclaim.
     * @param loggables loggables following the candidate.
     * @return true if any loggable (the candidate or any among loggables) was reclaimed.
     */
    fun reclaim(loggable: RandomAccessLoggable, loggables: Iterator<RandomAccessLoggable>): Boolean
}
