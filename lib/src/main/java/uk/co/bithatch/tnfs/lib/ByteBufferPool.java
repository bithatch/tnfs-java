/*
 * Copyright © 2025 Bithatch (brett@bithatch.co.uk)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package uk.co.bithatch.tnfs.lib;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple, thread-safe pool of {@link ByteBuffer}s with a fixed maximum size.
 * <p>
 * On checkout, callers request a required size. The pool will:
 * <ul>
 *   <li>Reuse an existing buffer if available. If the buffer is larger than requested,
 *       a <strong>slice</strong> view of the requested size is returned (the original backing
 *       buffer is held by the lease and returned to the pool on close).</li>
 *   <li>If the best reusable buffer is smaller than requested, the pool will <strong>grow</strong>:
 *       allocate a new buffer of the required size and replace the chosen internal entry.</li>
 *   <li>If no buffers are available, a new one is allocated.</li>
 * </ul>
 * When the lease is closed, the pooled (backing) buffer is returned to the pool (subject to
 * max entries). If the pool is full at that time, the buffer is discarded.
 */
public final class ByteBufferPool {
	private final static Logger LOG = LoggerFactory.getLogger(ByteBufferPool.class);

    /** Strategy for allocating new ByteBuffers. */
    @FunctionalInterface
    public interface Allocator {
        ByteBuffer allocate(int size);
    }

    /** Allocate heap buffers. */
    public static final Allocator HEAP = ByteBuffer::allocate;
    /** Allocate direct (off-heap) buffers. */
    public static final Allocator DIRECT = ByteBuffer::allocateDirect;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<ByteBuffer> buffers; // available (not checked out)
    private final int maxEntries;
    private final Allocator allocator;
    private volatile ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;

    /**
     * @param maxEntries  maximum number of buffers to keep pooled (must be >= 1)
     * @param allocator   allocator strategy (heap vs direct)
     */
    public ByteBufferPool(int maxEntries, Allocator allocator) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be >= 1");
        this.maxEntries = maxEntries;
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.buffers = new ArrayList<>(maxEntries);
    }

    /** Optional: set the byte order used for newly allocated buffers and shrink slices. */
    public ByteBufferPool byteOrder(ByteOrder order) {
        this.byteOrder = Objects.requireNonNull(order);
        return this;
    }

    /** Current number of available buffers in the pool (not including checked-out leases). */
    public int available() {
        lock.lock();
        try { return buffers.size(); }
        finally { lock.unlock(); }
    }

    /** Maximum number of entries this pool will retain. */
    public int capacity() { return maxEntries; }

    /**
     * Acquire a buffer lease of at least {@code requiredSize} bytes.
     * <p>Always call {@link Lease#close()} when finished to return the buffer to the pool.</p>
     */
    public Lease acquire(int requiredSize) {
        if (requiredSize < 0) throw new IllegalArgumentException("requiredSize must be >= 0");
        
        if(LOG.isDebugEnabled()) {
        	LOG.debug("Acquiring buffer of {}", requiredSize);
        }
        
        ByteBuffer picked = null;
        lock.lock();
        try {
            // Choose the smallest buffer that fits; if none fits, choose the largest available.
            int bestFitIdx = -1;
            int bestFitCap = Integer.MAX_VALUE;
            int largestIdx = -1;
            int largestCap = -1;
            for (int i = 0; i < buffers.size(); i++) {
                int cap = buffers.get(i).capacity();
                if (cap >= requiredSize) {
                    if (cap < bestFitCap) { bestFitCap = cap; bestFitIdx = i; }
                }
                if (cap > largestCap) { largestCap = cap; largestIdx = i; }
            }

            if (bestFitIdx >= 0) {
                picked = buffers.remove(bestFitIdx);
            } else if (!buffers.isEmpty()) {
                // Grow: take the largest we have and replace it with a bigger one for this lease
                ByteBuffer toReplace = buffers.remove(largestIdx);
                // 'toReplace' is dropped; we allocate a new one sized as requested
                picked = allocator.allocate(requiredSize).order(byteOrder);
            } else {
                // Empty pool: allocate from scratch
                picked = allocator.allocate(requiredSize).order(byteOrder);
            }
        } finally {
            lock.unlock();
        }

        // If picked is larger than required, give a slice view; otherwise, return it directly.
        if (picked.capacity() > requiredSize) {
            ByteBuffer backing = picked; // keep this to return to pool
            ByteBuffer view = sliceOf(backing, requiredSize, byteOrder);
            return new Lease(this, backing, view);
        } else {
            // capacity == requiredSize (or 0): use the same buffer for backing and view
            picked.clear();
            picked.order(byteOrder);
            return new Lease(this, picked, picked);
        }
    }

    private static ByteBuffer sliceOf(ByteBuffer src, int size, ByteOrder order) {
        // Create a slice of exactly 'size' bytes from position 0.
        src.clear();
        src.limit(Math.min(size, src.capacity()));
        ByteBuffer slice = src.slice(); // slice has capacity = remaining = limit - position
        slice.order(order);
        return slice;
    }

    private void release(ByteBuffer backing) {
        // Reset for reuse and attempt to return to pool; drop if pool is full.
        backing.clear();
        backing.order(byteOrder);
        lock.lock();
        try {
            if (buffers.size() < maxEntries) {
                buffers.add(backing);
                // Keep list roughly ordered small->large to improve future best-fit scans.
                buffers.sort(Comparator.comparingInt(ByteBuffer::capacity));
            } // else: drop
        } finally {
            lock.unlock();

            if(LOG.isDebugEnabled()) {
            	LOG.debug("Released buffer");
            }
        }
    }

    /** An AutoCloseable handle that returns the buffer to the pool on close. */
    public static final class Lease implements AutoCloseable {
        private final ByteBufferPool owner;
        private final ByteBuffer backing; // the buffer that goes back into the pool
        private final ByteBuffer view;    // what the caller uses (may be a slice of backing)
        private boolean closed;

        private Lease(ByteBufferPool owner, ByteBuffer backing, ByteBuffer view) {
            this.owner = owner;
            this.backing = backing;
            this.view = view;
        }

        /** The buffer to use. Do not keep references after calling close(). */
        public ByteBuffer buffer() { return view; }

        /** Return this lease to the pool. Idempotent. */
        @Override public void close() {
            if (closed) return;
            closed = true;
            owner.release(backing);
        }
    }

    // --- Convenience helpers -------------------------------------------------

    /** Execute an action with a leased buffer, auto-returning it afterward. */
    public void withBuffer(int requiredSize, java.util.function.Consumer<ByteBuffer> use) {
        try (Lease l = acquire(requiredSize)) {
            use.accept(l.buffer());
        }
    }

    /** Execute a function with a leased buffer and return its result. */
    public <T> T withBuffer(int requiredSize, java.util.function.Function<ByteBuffer, T> use) {
        try (Lease l = acquire(requiredSize)) { return use.apply(l.buffer()); }
    }

    // --- Example -------------------------------------------------------------
    public static void main(String[] args) {
        ByteBufferPool pool = new ByteBufferPool(8, HEAP);

        // Acquire 1024 bytes
        try (Lease l1 = pool.acquire(1024)) {
            ByteBuffer b = l1.buffer();
            b.putInt(42);
        }

        // Reuse (shrink via slice to 128 bytes)
        try (Lease l2 = pool.acquire(128)) {
            ByteBuffer b = l2.buffer();
            System.out.println("Lease2 capacity: " + b.capacity()); // should be 128
        }

        // Grow (replace) to 4096 bytes
        try (Lease l3 = pool.acquire(4096)) {
            ByteBuffer b = l3.buffer();
            System.out.println("Lease3 capacity: " + b.capacity()); // should be 4096
        }

        System.out.println("Available after returns: " + pool.available());
    }
}
