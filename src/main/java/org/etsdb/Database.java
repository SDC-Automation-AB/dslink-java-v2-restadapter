package org.etsdb;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.etsdb.util.Handler;

/**
 * TODO seriesIds must be valid file names. Consider adding code to convert invalid characters to something valid.
 * TODO consider a flushing scheme that writes to files according to a set schedule, e.g. one file per second.
 * TODO add a query that returns the given time range, plus the pvts immediately before and after the range. Currently
 * this is three queries.
 * <p>
 * watch grep ^Cached /proc/meminfo # Page Cache size
 * watch grep -A 1 dirty /proc/vmstat # Dirty Pages and writeback to disk activity
 * watch cat /proc/sys/vm/nr_pdflush_threads # shows # of active pdflush threads
 *
 * @param <T> The type of value that is stored in the database. E.g. Double, Integer, DataValue,
 *            MyArbitraryValueThatMightBeNumericOrTextOrArrayOrObject
 * @author mlohbihler
 */
public interface Database<T> {
    File getBaseDir();

    void write(String seriesId, long ts, T value);

    void query(String seriesId, long fromTs, long toTs, final QueryCallback<T> cb);

    void query(String seriesId, long fromTs, long toTs, int limit, final QueryCallback<T> cb);

    void query(String seriesId, long fromTs, long toTs, int limit, boolean reverse, final QueryCallback<T> cb);

    long count(String seriesId, long fromTs, long toTs);

    List<String> getSeriesIds();

    long getDatabaseSize();

    long availableSpace();

    TimeRange getTimeRange(List<String> seriesIds);

    long delete(String seriesId, long fromTs, long toTs);

    void purge(String seriesId, long toTs);

    /**
     * @param seriesId Series to delete
     */
    void deleteSeries(String seriesId);

    void close() throws IOException;

    //
    //
    // Metrics
    //
    int getWritesPerSecond();

    long getWriteCount();

    void setWriteCountHandler(Handler<Long> handler);

    long getFlushCount();

    void setFlushCountHandler(Handler<Long> handler);

    long getBackdateCount();

    void setBackdateCountHandler(Handler<Long> handler);

    int getOpenFiles();

    void setOpenFilesHandler(Handler<Integer> handler);

    long getFlushForced();

    void setFlushForcedHandler(Handler<Long> handler);

    long getFlushExpired();

    void setFlushExpiredHandler(Handler<Long> handler);

    long getFlushLimit();

    void setFlushLimitHandler(Handler<Long> handler);

    long getForcedClose();

    int getLastFlushMillis();

    void setLastFlushMillisHandler(Handler<Integer> handler);

    int getQueueSize();

    void setQueueSizeHandler(Handler<Integer> handler);

    int getOpenShards();

    void setOpenShardsHandler(Handler<Integer> handler);
}
