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
package jetbrains.exodus.core.execution.locks;

public class Semaphore {

    Thread thread;
    private int permits;
    private final int maxPermits;

    public Semaphore() {
        this(0);
    }

    public Semaphore(final int permits) {
        this.permits = permits;
        maxPermits = permits;
    }

    public synchronized boolean tryAcquire() {
        if (permits > 0) {
            acquire();
            return true;
        }

        return false;
    }

    public void acquire() {
        acquire(1);
    }

    public synchronized void acquire(final int permits) {
        while (this.permits < permits) {
            try {
                wait(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        this.permits -= permits;
        thread = Thread.currentThread();
    }

    public void acquireUninterruptibly() {
        acquire();
    }

    public void acquireUninterruptibly(final int permits) {
        acquire(permits);
    }

    public void release() {
        release(1);
    }

    public synchronized void release(int permits) {
        final int resultingPermits = this.permits + permits;
        if (resultingPermits > maxPermits)
            throw new IllegalArgumentException("Can't release " + permits + " permits. Current permits: " + this.permits + ", max permits: " + maxPermits);

        this.permits = resultingPermits;
        notifyAll();
    }

    public synchronized int availablePermits() {
        return permits;
    }
}
