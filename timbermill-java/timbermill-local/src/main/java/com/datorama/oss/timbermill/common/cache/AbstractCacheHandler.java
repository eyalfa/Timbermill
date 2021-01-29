package com.datorama.oss.timbermill.common.cache;

import com.datorama.oss.timbermill.common.ElasticsearchUtil;
import com.datorama.oss.timbermill.common.KamonConstants;
import com.datorama.oss.timbermill.unit.LocalTask;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import kamon.metric.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class AbstractCacheHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCacheHandler.class);

    private Cache<String, List<String>> orphansCache;

    public AbstractCacheHandler(long maximumOrphansCacheWeight) {
        orphansCache = CacheBuilder.newBuilder()
                .maximumWeight(maximumOrphansCacheWeight)
                .weigher(this::getEntryLength)
                .removalListener(notification -> {
                    int entryLength = getEntryLength(notification.getKey(), notification.getValue());
                    KamonConstants.ORPHANS_CACHE_SIZE_RANGE_SAMPLER.withoutTags().decrement(entryLength);
                    KamonConstants.ORPHANS_CACHE_ENTRIES_RANGE_SAMPLER.withoutTags().decrement();
                })
                .build();
    }

    private int getEntryLength(String key, List<String> value) {
        int valuesLengths = value.stream().mapToInt(String::length).sum();
        int keyLength = key.length();
        return 2 * (keyLength + valuesLengths);
    }

    public List<String> pullFromOrphansCache(String parentId) {
        List<String> orphans = getFromOrphansCache(parentId);
        if (orphans != null){
            orphansCache.invalidate(parentId);
        }
        return orphans;
    }

    public List<String> getFromOrphansCache(String parentId) {
        return orphansCache.getIfPresent(parentId);
    }

    public void pushToOrphanCache(String parentId, List<String> tasks) {
        orphansCache.put(parentId, tasks);
        int entryLength = getEntryLength(parentId, tasks);
        KamonConstants.ORPHANS_CACHE_SIZE_RANGE_SAMPLER.withoutTags().increment(entryLength);
        KamonConstants.ORPHANS_CACHE_ENTRIES_RANGE_SAMPLER.withoutTags().increment();
    }

    public Map<String, LocalTask> logGetFromTasksCache(Collection<String> idsList, String type){
        LOG.info("Retrieving {} tasks from cache, flow: [{}]", idsList.size(), type);
        Timer.Started start = KamonConstants.RETRIEVE_FROM_CACHE_TIMER.withTag("type", type).start();
        Map<String, LocalTask> retMap = getFromTasksCache(idsList);
        start.stop();
        KamonConstants.TASKS_QUERIED_FROM_CACHE_HISTOGRAM.withTag("type", type).record(idsList.size());
        KamonConstants.TASKS_RETRIEVED_FROM_CACHE_HISTOGRAM.withTag("type", type).record(retMap.size());
        LOG.info("{} tasks retrieved from cache, flow: [{}]", retMap.size(), type);
        return retMap;
    }

    public void logPushToTasksCache(Map<String, LocalTask> idsToMap, String type){
        LOG.info("Pushing {} tasks to cache, flow: [{}]", idsToMap.size(), type);
        Timer.Started start = KamonConstants.PUSH_TO_CACHE_TIMER.withTag("type", type).start();
        pushToTasksCache(idsToMap);
        start.stop();
        KamonConstants.TASKS_PUSHED_TO_CACHE_HISTOGRAM.withTag("type", type).record(idsToMap.size());
    }

    public abstract Map<String, LocalTask> getFromTasksCache(Collection<String> idsList);

    public abstract void pushToTasksCache(Map<String, LocalTask> idsToMap);

    public abstract void close();
}
