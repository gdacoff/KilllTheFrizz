package io.github.gdacoff.killthefrizz.culling.backend;

/**
 * Abstraction over a GPU backend used for occlusion-based entity culling.
 *
 * <p>Implementations must never throw from these methods; on any error they should
 * degrade to a conservative "visible" answer and report {@link #isSupported()} as
 * {@code false} where appropriate.</p>
 */
public interface GpuBackend {

    /** @return true if this backend can be used on the current platform/driver. */
    boolean isSupported();

    /** Initializes GPU resources. Safe to call once; subsequent calls are no-ops. */
    void init();

    /** Called at the start of a frame, before any queries are issued. */
    void beginFrame();

    /** Called at the end of a frame, after queries are issued. */
    void endFrame();

    /**
     * @param queryId id returned by {@link #createQuery()}
     * @return true if the entity associated with the query is occluded. If the result is
     *         not yet available (latency) or unknown, returns false (visible).
     */
    boolean isOccluded(int queryId);

    /** @return a new query id, or -1 if queries are unavailable. */
    int createQuery();

    /** Deletes a query previously created by {@link #createQuery()}. */
    void deleteQuery(int queryId);

    /** Releases all GPU resources. */
    void destroy();
}
