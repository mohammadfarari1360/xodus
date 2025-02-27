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
package jetbrains.exodus.tree.btree

import jetbrains.exodus.*
import jetbrains.exodus.core.dataStructures.hash.HashSet
import jetbrains.exodus.log.*
import jetbrains.exodus.log.CompressedUnsignedLongByteIterable.Companion.getCompressedSize
import jetbrains.exodus.log.CompressedUnsignedLongByteIterable.Companion.getIterable
import jetbrains.exodus.tree.*
import jetbrains.exodus.tree.ExpiredLoggableCollection.Companion.newInstance
import jetbrains.exodus.tree.TreeCursorMutable.Companion.notifyCursors
import jetbrains.exodus.util.LightOutputStream

@Suppress("LeakingThis")
open class BTreeMutable @JvmOverloads internal constructor(
    private val immutableTree: BTreeBase,
    extraBelongings: ExtraMutableBelongings? = ExtraMutableBelongings()
) : BTreeBase(
    immutableTree.log, immutableTree.balancePolicy, immutableTree.allowsDuplicates, immutableTree.structureId
), ITreeMutable {
    override var root: BasePageMutable = immutableTree.root.getMutableCopy(this)

    private val extraBelongings: ExtraMutableBelongings?
    init {
        size = immutableTree.size
        this.extraBelongings = extraBelongings
    }

    override val rootAddress: Long
        get() = Loggable.NULL_ADDRESS
    override val isAllowingDuplicates: Boolean
        get() = allowsDuplicates
    override val openCursors: Iterable<ITreeCursorMutable>?
        get() = extraBelongings!!.openCursors

    override fun addressIterator(): AddressIterator {
        return immutableTree.addressIterator()
    }

    override val mutableCopy: BTreeMutable
        get() = this

    override fun getDataIterator(address: Long): DataIterator {
        return immutableTree.getDataIterator(address)
    }

    override fun put(ln: INode) {
        val value = ln.value ?: throw ExodusException("Value can't be null")
        put(ln.key, value)
    }

    override fun putRight(ln: INode) {
        val value = ln.value ?: throw ExodusException("Value can't be null")
        putRight(ln.key, value)
    }

    override fun add(ln: INode): Boolean {
        val value = ln.value ?: throw ExodusException("Value can't be null")
        return add(ln.key, value)
    }

    override fun put(key: ByteIterable, value: ByteIterable): Boolean {
        return put(key, value, true)
    }

    override fun putRight(key: ByteIterable, value: ByteIterable) {
        val newSibling = root.putRight(key, value)
        if (newSibling != null) {
            root = InternalPageMutable(this, root, newSibling)
        }
    }

    override fun add(key: ByteIterable, value: ByteIterable): Boolean {
        return put(key, value, false)
    }

    private fun put(key: ByteIterable, value: ByteIterable, overwrite: Boolean): Boolean {
        val result = booleanArrayOf(false)
        val newSibling = root.put(key, value, overwrite, result)
        if (newSibling != null) {
            root = InternalPageMutable(this, root, newSibling)
        }
        return result[0]
    }

    override fun delete(key: ByteIterable): Boolean {
        return deleteImpl(key, null)
    }

    override fun delete(key: ByteIterable, value: ByteIterable?, cursorToSkip: ITreeCursorMutable?): Boolean {
        if (deleteImpl(key, value)) {
            notifyCursors(this, cursorToSkip)
            return true
        }
        return false
    }

    open val leafStream: LightOutputStream?
        get() {
            var leafStream = extraBelongings!!.leafStream
            if (leafStream == null) {
                leafStream = LightOutputStream(16)
                extraBelongings.leafStream = leafStream
            } else {
                leafStream.clear()
            }
            return leafStream
        }

    // for test only!!!
    fun delete(key: ByteIterable, value: ByteIterable?): Boolean {
        if (deleteImpl(key, value)) {
            notifyCursors(this)
            return true
        }
        return false
    }

    private fun deleteImpl(key: ByteIterable, value: ByteIterable?): Boolean {
        val res = BooleanArray(1)
        root = delete(root, key, value, res)
        return res[0]
    }

    open fun decrementSize(delta: Long) {
        size -= delta
    }

    open fun incrementSize() {
        size++
    }

    override fun save(): Long {
        // dfs, save leafs, then bottoms, then internals, then root
        val type: Byte = if (root.isBottom) BOTTOM_ROOT else INTERNAL_ROOT
        val log = log
        val savedData = root.data
        val iterables = arrayOf(
            getIterable(size),
            savedData
        )
        return log.write(type, structureId, CompoundByteIterable(iterables), expiredLoggables)
    }

    open fun addExpiredLoggable(loggable: Loggable) {
        if (loggable.address != Loggable.NULL_ADDRESS) {
            expiredLoggables.add(loggable)
        }
    }

    open fun addExpiredLoggable(address: Long) {
        if (address != Loggable.NULL_ADDRESS) {
            addExpiredLoggable(getLoggable(address))
        }
    }

    fun addExpiredLoggable(node: ILeafNode) {
        if (!node.isMutable) {
            if (node is LeafNode) {
                addExpiredLoggable(node.loggable)
            } else {
                addExpiredLoggable(node.address)
            }
        }
    }

    override val expiredLoggables: ExpiredLoggableCollection
        get() {
            var expiredLoggables = extraBelongings!!.expiredLoggables
            if (expiredLoggables == null) {
                expiredLoggables = newInstance(log)
                extraBelongings.expiredLoggables = expiredLoggables
            }
            return expiredLoggables
        }

    override fun openCursor(): TreeCursor {
        var cursors = extraBelongings!!.openCursors
        if (cursors == null) {
            cursors = HashSet()
            extraBelongings.openCursors = cursors
        }
        val result = if (allowsDuplicates) BTreeCursorDupMutable(this, BTreeTraverserDup(root)) else TreeCursorMutable(
            this,
            BTreeTraverser(root)
        )
        cursors.add(result)
        return result
    }

    override fun cursorClosed(cursor: ITreeCursorMutable) {
        extraBelongings!!.openCursors!!.remove(cursor)
    }

    open val bottomPageType: Byte
        get() = BOTTOM
    open val internalPageType: Byte
        get() = INTERNAL
    open val leafType: Byte
        get() = LEAF
    open val isDup: Boolean
        get() = false

    open fun createMutableLeaf(key: ByteIterable, value: ByteIterable): BaseLeafNodeMutable {
        return LeafNodeMutable(key, value)
    }

    override fun reclaim(
        loggable: RandomAccessLoggable,
        loggables: Iterator<RandomAccessLoggable>
    ): Boolean {
        var inputLoggable = loggable
        val context = BTreeReclaimTraverser(this)
        val nextFileAddress = log.getFileAddress(inputLoggable.address) + log.fileLengthBound
        loop@ while (true) {
            val type = inputLoggable.type
            when (type) {
                NullLoggable.TYPE, HashCodeLoggable.TYPE -> {}
                LEAF_DUP_BOTTOM_ROOT, LEAF_DUP_INTERNAL_ROOT -> {
                    context.dupLeafsLo.clear()
                    context.dupLeafsHi.clear()
                    LeafNodeDup(this, inputLoggable).reclaim(context)
                }

                LEAF -> LeafNode(log, inputLoggable).reclaim(context)
                BOTTOM_ROOT, INTERNAL_ROOT -> {
                    if (inputLoggable.address == immutableTree.rootAddress) {
                        context.wasReclaim = true
                    }
                    break@loop  // txn ended
                }

                BOTTOM -> reclaimBottom(inputLoggable, context)
                INTERNAL -> reclaimInternal(inputLoggable, context)
                DUP_LEAF, DUP_BOTTOM, DUP_INTERNAL -> {
                    context.dupLeafsLo.clear()
                    context.dupLeafsHi.clear()
                    val leaf: RandomAccessLoggable =
                        LeafNodeDup.collect(context.dupLeafsHi, inputLoggable, loggables)
                            ?: break@loop  // loggable of dup leaf type not found, txn ended prematurely
                    LeafNodeDup(this, leaf).reclaim(context)
                }

                else -> throw ExodusException("Unexpected loggable type $type")
            }
            if (!loggables.hasNext()) {
                break
            }
            if (type == NullLoggable.TYPE) {
                break
            }
            inputLoggable = loggables.next()
            if (inputLoggable.address >= nextFileAddress) {
                break
            }
        }
        while (context.canMoveUp()) {
            // wire up mutated stuff
            context.popAndMutate()
        }
        return context.wasReclaim
    }

    fun reclaimInternal(loggable: RandomAccessLoggable, context: BTreeReclaimTraverser) {
        val data = loggable.data
        val it = data.iterator()
        val i = it.compressedUnsignedInt
        if (i and 1 == 1 && i > 1) {
            val minKey = loadMinKey(data, getCompressedSize(i.toLong()))
            if (minKey != null) {
                val page = InternalPage(
                    this, data.cloneWithAddressAndLength(
                        it.address,
                        it.available()
                    ),
                    i shr 1, loggable.isDataInsideSinglePage
                )
                page.reclaim(minKey.key, context)
            }
        }
    }

    fun reclaimBottom(loggable: RandomAccessLoggable, context: BTreeReclaimTraverser) {
        val data = loggable.data
        val it = data.iterator()
        val i = it.compressedUnsignedInt
        if (i and 1 == 1 && i > 1) {
            val minKey = loadMinKey(data, getCompressedSize(i.toLong()))
            if (minKey != null) {
                val page = BottomPage(
                    this, data.cloneWithAddressAndLength(
                        it.address,
                        it.available()
                    ),
                    i shr 1,
                    loggable.isDataInsideSinglePage
                )
                page.reclaim(minKey.key, context)
            }
        }
    }

    private fun loadMinKey(data: ByteIterableWithAddress, offset: Int): LeafNode? {
        val addressLen = data.byteAt(offset).toInt()
        val keyAddress = data.nextLong(offset + 1, addressLen)
        return if (log.hasAddress(keyAddress)) loadLeaf(keyAddress) else null
    }

    internal class ExtraMutableBelongings {
        var expiredLoggables: ExpiredLoggableCollection? = null
        var openCursors: MutableSet<ITreeCursorMutable>? = null
        var leafStream: LightOutputStream? = null
    }

    companion object {
        fun delete(
            root: BasePageMutable?,
            key: ByteIterable,
            value: ByteIterable?,
            res: BooleanArray
        ): BasePageMutable {
            var inputRoot = root
            if (inputRoot!!.delete(key, value)) {
                inputRoot = inputRoot.mergeWithChildren()
                res[0] = true
                return inputRoot
            }
            res[0] = false
            return inputRoot
        }
    }
}
