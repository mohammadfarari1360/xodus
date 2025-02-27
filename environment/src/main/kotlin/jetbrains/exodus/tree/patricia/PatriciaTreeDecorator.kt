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
package jetbrains.exodus.tree.patricia

import jetbrains.exodus.ByteIterable
import jetbrains.exodus.log.DataIterator
import jetbrains.exodus.log.Log
import jetbrains.exodus.tree.Dumpable
import jetbrains.exodus.tree.ITree
import java.io.PrintStream

abstract class PatriciaTreeDecorator protected constructor(val treeNoDuplicates: ITree) : ITree {
    override val log: Log
        get() = treeNoDuplicates.log

    override fun getDataIterator(address: Long): DataIterator {
        return treeNoDuplicates.getDataIterator(address)
    }

    override val rootAddress: Long
        get() = treeNoDuplicates.rootAddress
    override val structureId: Int
        get() = treeNoDuplicates.structureId

    override fun hasKey(key: ByteIterable): Boolean {
        return get(key) != null
    }

    override val isEmpty: Boolean
        get() = treeNoDuplicates.isEmpty
    override val size: Long
        get() = treeNoDuplicates.size

    override fun dump(out: PrintStream) {
        treeNoDuplicates.dump(out)
    }

    override fun dump(out: PrintStream, renderer: Dumpable.ToString?) {
        treeNoDuplicates.dump(out, renderer)
    }
}
