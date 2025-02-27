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
package jetbrains.exodus.log

import jetbrains.exodus.*

object NullLoggable {
    const val TYPE: Byte = 0
    fun create(startAddress: Long, endAddress: Long): SinglePageLoggable {
        return SinglePageLoggable(
            startAddress, endAddress, TYPE, Loggable.NO_STRUCTURE_ID,
            Loggable.NULL_ADDRESS, ByteIterable.EMPTY_BYTES, 0, 0
        )
    }

    @JvmStatic
    fun create(): SinglePageLoggable {
        return SinglePageLoggable.NULL_PROTOTYPE
    }

    @JvmStatic
    fun isNullLoggable(type: Byte): Boolean {
        return type == TYPE
    }

    fun isNullLoggable(loggable: Loggable): Boolean {
        return isNullLoggable(loggable.type)
    }
}
