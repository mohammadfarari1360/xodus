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
package jetbrains.exodus.crypto

import jetbrains.exodus.core.dataStructures.hash.LongHashMap
import jetbrains.exodus.core.dataStructures.hash.LongSet
import jetbrains.exodus.core.dataStructures.Pair
import jetbrains.exodus.entitystore.BlobVault
import jetbrains.exodus.entitystore.BlobVaultItem
import jetbrains.exodus.entitystore.DiskBasedBlobVault
import jetbrains.exodus.entitystore.FileSystemBlobVaultOld
import jetbrains.exodus.entitystore.StoreTransaction
import jetbrains.exodus.env.Transaction
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Path

class EncryptedBlobVault(
    private val decorated: FileSystemBlobVaultOld,
    private val cipherProvider: StreamCipherProvider,
    private val cipherKey: ByteArray,
    private val cipherBasicIV: Long
) : BlobVault(decorated), DiskBasedBlobVault {

    override fun getSourceVault() = decorated

    override fun clear() = decorated.clear()

    override fun getBackupStrategy() = decorated.backupStrategy

    override fun getBlob(blobHandle: Long): BlobVaultItem {
        return decorated.getBlob(blobHandle)
    }

    override fun getContent(
        blobHandle: Long,
        txn: Transaction,
        expectedLength: Long?
    ): InputStream? {
        return decorated.getContent(blobHandle, txn, expectedLength)?.run {
            StreamCipherInputStream(this) {
                newCipher(blobHandle)
            }
        }
    }

    override fun delete(blobHandle: Long): Boolean {
        return decorated.delete(blobHandle)
    }

    override fun getBlobLocation(blobHandle: Long): File {
        return decorated.getBlobLocation(blobHandle)
    }

    override fun getBlobLocation(blobHandle: Long, readonly: Boolean): File {
        return decorated.getBlobLocation(blobHandle, readonly)
    }

    override fun getBlobKey(blobHandle: Long): String {
        return decorated.getBlobKey(blobHandle)
    }

    override fun getSize(blobHandle: Long, txn: Transaction) = decorated.getSize(blobHandle, txn)

    override fun requiresTxn() = decorated.requiresTxn()

    override fun flushBlobs(
        blobStreams: LongHashMap<InputStream>?,
        blobFiles: LongHashMap<Path>?,
        tmpBlobFiles: LongHashMap<Path>?,
        deferredBlobsToDelete: LongSet?,
        txn: Transaction
    ) {
        val streams = LongHashMap<InputStream>()
        blobStreams?.forEach {
            streams[it.key] = StreamCipherInputStream(it.value) {
                newCipher(it.key)
            }
        }

        var openFiles: MutableList<InputStream>? = null
        try {
            if (!blobFiles.isNullOrEmpty()) {
                openFiles = mutableListOf()
                blobFiles.forEach {
                    streams[it.key] = StreamCipherInputStream(
                        BufferedInputStream(FileInputStream(it.value.toFile())
                            .also { file -> openFiles.add(file) })
                    ) {
                        newCipher(it.key)
                    }
                }
            }

            decorated.flushBlobs(streams, null, tmpBlobFiles, deferredBlobsToDelete, txn)
        } finally {
            openFiles?.forEach { it.close() }
        }
    }

    override fun size() = decorated.size()

    override fun nextHandle(txn: Transaction) = decorated.nextHandle(txn)

    override fun close() = decorated.close()
    override fun copyToTemporaryStore(
        handle: Long,
        stream: InputStream,
        transaction: StoreTransaction?
    ): Pair<Path, Long> {
        return decorated.copyToTemporaryStore(handle, StreamCipherInputStream(stream) {
            newCipher(handle)
        }, transaction)
    }

    override fun openTmpStream(handle: Long, path: Path): InputStream {
        return StreamCipherInputStream(decorated.openTmpStream(handle, path)) {
            newCipher(handle)
        }
    }

    private fun newCipher(blobHandle: Long) =
        Companion.newCipher(cipherProvider, blobHandle, cipherKey, cipherBasicIV)

    companion object {
        @JvmStatic
        fun newCipher(cipherProvider: StreamCipherProvider, blobHandle: Long, cipherKey: ByteArray, cipherBasicIV: Long) : StreamCipher {
            return cipherProvider.newCipher().apply { init(cipherKey, (cipherBasicIV - blobHandle).asHashedIV()) }
        }
    }
}
